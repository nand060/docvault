package com.yourname.docvault.file;

import com.yourname.docvault.common.ApiException;
import com.yourname.docvault.external.HuggingFaceClient;
import com.yourname.docvault.user.User;
import com.yourname.docvault.user.UserService;
import com.yourname.docvault.websocket.SocketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class FileService {
    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileRepository fileRepository;
    private final FileVectorRepository fileVectorRepository;
    private final UserService userService;
    private final HuggingFaceClient huggingFaceClient;
    private final FileChunker fileChunker;
    private final SimpMessagingTemplate messagingTemplate;
    private final long maxSizeBytes;

    public FileService(
            FileRepository fileRepository,
            FileVectorRepository fileVectorRepository,
            UserService userService,
            HuggingFaceClient huggingFaceClient,
            FileChunker fileChunker,
            SimpMessagingTemplate messagingTemplate,
            @Value("${app.files.max-size-bytes}") long maxSizeBytes
    ) {
        this.fileRepository = fileRepository;
        this.fileVectorRepository = fileVectorRepository;
        this.userService = userService;
        this.huggingFaceClient = huggingFaceClient;
        this.fileChunker = fileChunker;
        this.messagingTemplate = messagingTemplate;
        this.maxSizeBytes = maxSizeBytes;
    }

    @Transactional
    public FileResponse upload(Long userId, MultipartFile multipartFile) {
        validateUpload(multipartFile);
        sendProgress(userId, "uploading", "Uploading file");

        User user = userService.requireUser(userId);
        String content = readContent(multipartFile);

        FileEntity file = new FileEntity();
        file.setUser(user);
        file.setName(safeName(multipartFile.getOriginalFilename()));
        file.setContent(content);
        FileEntity saved = fileRepository.saveAndFlush(file);

        List<String> chunks = fileChunker.chunk(content);
        for (int i = 0; i < chunks.size(); i++) {
            sendProgress(userId, "embedding", "Generating embeddings... chunk " + (i + 1) + "/" + chunks.size());
            List<Double> embedding = huggingFaceClient.embed(chunks.get(i));
            fileVectorRepository.insertVector(saved.getId(), i, chunks.get(i), embedding);
        }

        log.info("Uploaded file {} for user {}", saved.getId(), userId);
        sendProgress(userId, "done", "Done ✓");
        return FileResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> list(Long userId) {
        return fileRepository.findAllByUserIdOrderByUploadedAtDesc(userId).stream()
                .map(FileResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long fileId) {
        FileEntity file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ApiException("File not found", HttpStatus.NOT_FOUND));
        fileRepository.delete(file);
        log.info("Deleted file {} for user {}", fileId, userId);
    }

    @Transactional(readOnly = true)
    public FileContentResponse content(Long userId, Long fileId) {
        FileEntity file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ApiException("File not found", HttpStatus.NOT_FOUND));
        return FileContentResponse.from(file);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("A .txt file is required", HttpStatus.BAD_REQUEST);
        }
        String name = safeName(file.getOriginalFilename());
        if (!name.toLowerCase().endsWith(".txt")) {
            throw new ApiException("Only .txt files are supported", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ApiException("File exceeds the 1MB upload limit", HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    private String readContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ApiException("Could not read uploaded file", HttpStatus.BAD_REQUEST);
        }
    }

    private String safeName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.txt";
        }
        return originalFilename.replaceAll("[\\\\/]+", "_");
    }

    private void sendProgress(Long userId, String type, String message) {
        messagingTemplate.convertAndSend(
                "/topic/upload-progress/" + userId,
                SocketEvent.of(type, message)
        );
    }
}
