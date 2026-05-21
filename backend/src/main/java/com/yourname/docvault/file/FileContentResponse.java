package com.yourname.docvault.file;

import java.time.Instant;

public record FileContentResponse(Long id, String name, String content, Instant uploadedAt) {
    public static FileContentResponse from(FileEntity file) {
        return new FileContentResponse(file.getId(), file.getName(), file.getContent(), file.getUploadedAt());
    }
}
