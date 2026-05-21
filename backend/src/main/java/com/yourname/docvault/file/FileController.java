package com.yourname.docvault.file;

import com.yourname.docvault.auth.CurrentUser;
import com.yourname.docvault.auth.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public FileResponse upload(@CurrentUser UserPrincipal principal, @RequestParam("file") MultipartFile file) {
        return fileService.upload(principal.id(), file);
    }

    @GetMapping
    public List<FileResponse> list(@CurrentUser UserPrincipal principal) {
        return fileService.list(principal.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UserPrincipal principal, @PathVariable Long id) {
        fileService.delete(principal.id(), id);
    }
}
