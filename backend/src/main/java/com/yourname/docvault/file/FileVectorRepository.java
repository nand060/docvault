package com.yourname.docvault.file;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FileVectorRepository {
    private final JdbcTemplate jdbcTemplate;

    public FileVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertVector(Long fileId, int chunkIndex, String chunkText, List<Double> embedding) {
        jdbcTemplate.update(
                """
                        INSERT INTO file_vectors (file_id, chunk_index, chunk_text, embedding)
                        VALUES (?, ?, ?, CAST(? AS vector))
                        """,
                fileId,
                chunkIndex,
                chunkText,
                toVectorLiteral(embedding)
        );
    }

    public void deleteByFileId(Long fileId) {
        jdbcTemplate.update("DELETE FROM file_vectors WHERE file_id = ?", fileId);
    }

    public static String toVectorLiteral(List<Double> embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding.get(i));
        }
        return builder.append(']').toString();
    }
}
