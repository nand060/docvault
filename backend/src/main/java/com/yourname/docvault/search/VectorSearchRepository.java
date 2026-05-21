package com.yourname.docvault.search;

import com.yourname.docvault.file.FileVectorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class VectorSearchRepository {
    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchDocument> search(Long userId, List<Double> queryEmbedding, int limit) {
        String sql = """
                SELECT fv.file_id,
                       fv.chunk_text,
                       f.name,
                       1 - (fv.embedding <=> CAST(? AS vector)) AS similarity_score
                FROM file_vectors fv
                JOIN files f ON f.id = fv.file_id
                WHERE f.user_id = ?
                ORDER BY fv.embedding <=> CAST(? AS vector)
                LIMIT ?
                """;

        String vectorLiteral = FileVectorRepository.toVectorLiteral(queryEmbedding);
        List<SearchDocument> chunkCandidates = jdbcTemplate.query(sql, (rs, rowNum) -> new SearchDocument(
                rs.getLong("file_id"),
                rs.getString("name"),
                rs.getString("chunk_text"),
                rs.getDouble("similarity_score")
        ), vectorLiteral, userId, vectorLiteral, limit * 3);

        Map<Long, SearchDocument> bestByFile = new LinkedHashMap<>();
        for (SearchDocument candidate : chunkCandidates) {
            SearchDocument current = bestByFile.get(candidate.id());
            if (current == null || candidate.similarityScore() > current.similarityScore()) {
                bestByFile.put(candidate.id(), candidate);
            }
        }

        return bestByFile.values().stream().limit(limit).toList();
    }
}
