package com.yourname.docvault.admin;

import com.yourname.docvault.external.HuggingFaceClient;
import com.yourname.docvault.file.FileChunker;
import com.yourname.docvault.file.FileEntity;
import com.yourname.docvault.file.FileRepository;
import com.yourname.docvault.file.FileVectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final FileRepository fileRepository;
    private final FileVectorRepository fileVectorRepository;
    private final FileChunker fileChunker;
    private final HuggingFaceClient huggingFaceClient;

    public AdminController(
            FileRepository fileRepository,
            FileVectorRepository fileVectorRepository,
            FileChunker fileChunker,
            HuggingFaceClient huggingFaceClient
    ) {
        this.fileRepository = fileRepository;
        this.fileVectorRepository = fileVectorRepository;
        this.fileChunker = fileChunker;
        this.huggingFaceClient = huggingFaceClient;
    }

    @PostMapping("/reindex")
    public Map<String, Integer> reindex() {
        int reindexed = 0;
        int failed = 0;

        for (FileEntity file : fileRepository.findAll()) {
            try {
                fileVectorRepository.deleteByFileId(file.getId());
                List<String> chunks = fileChunker.chunk(file.getContent());
                for (int i = 0; i < chunks.size(); i++) {
                    fileVectorRepository.insertVector(file.getId(), i, chunks.get(i), huggingFaceClient.embed(chunks.get(i)));
                }
                reindexed++;
                log.info("Reindexed file {}", file.getId());
            } catch (Exception ex) {
                failed++;
                log.error("Failed to reindex file {}", file.getId(), ex);
            }
        }

        return Map.of("reindexed", reindexed, "failed", failed);
    }
}
