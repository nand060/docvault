package com.yourname.docvault.file;

import java.time.Instant;

public record FileResponse(Long id, String name, Instant uploadedAt) {
    public static FileResponse from(FileEntity file) {
        return new FileResponse(file.getId(), file.getName(), file.getUploadedAt());
    }
}
