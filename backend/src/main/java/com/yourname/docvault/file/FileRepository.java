package com.yourname.docvault.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    List<FileEntity> findAllByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<FileEntity> findByIdAndUserId(Long id, Long userId);
}
