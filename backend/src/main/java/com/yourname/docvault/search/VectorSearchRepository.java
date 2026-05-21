package com.yourname.docvault.search;

import com.yourname.docvault.file.FileVectorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VectorSearchRepository {
    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchDocument> search(Long userId, List<Double> queryEmbedding, int limit, boolean includeContent) {
        String contentColumn = includeContent ? "f.content" : "''";
        String sql = """
                SELECT f.id,
                       f.name,
                       %s AS content,
                       1 - (fv.embedding <=> CAST(? AS vector)) AS similarity_score
                FROM file_vectors fv
                JOIN files f ON f.id = fv.file_id
                WHERE f.user_id = ?
                ORDER BY fv.embedding <=> CAST(? AS vector)
                LIMIT ?
                """.formatted(contentColumn);

        String vectorLiteral = FileVectorRepository.toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SearchDocument(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("content"),
                rs.getDouble("similarity_score")
        ), vectorLiteral, userId, vectorLiteral, limit);
    }
}
