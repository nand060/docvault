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

    public void insertVector(Long fileId, List<Double> embedding) {
        jdbcTemplate.update(
                "INSERT INTO file_vectors (file_id, embedding) VALUES (?, CAST(? AS vector))",
                fileId,
                toVectorLiteral(embedding)
        );
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
