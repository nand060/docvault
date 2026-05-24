package com.yourname.docvault.search;

import com.yourname.docvault.file.FileVectorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class VectorSearchRepository {
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "do", "does", "for", "from",
            "did", "how", "i", "in", "is", "it", "me", "my", "of", "on", "or", "please", "tell", "that", "the",
            "this", "to", "was", "what", "when", "where", "who", "why", "with", "would", "you"
    );

    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchDocument> search(Long userId, String query, List<Double> queryEmbedding, int limit) {
        String sql = """
                SELECT fv.file_id,
                       COALESCE(NULLIF(fv.chunk_text, ''), f.content) AS chunk_text,
                       f.name,
                       1 - (fv.embedding <=> CAST(? AS vector)) AS similarity_score
                FROM file_vectors fv
                JOIN files f ON f.id = fv.file_id
                WHERE f.user_id = ?
                ORDER BY fv.embedding <=> CAST(? AS vector)
                LIMIT ?
                """;

        String vectorLiteral = FileVectorRepository.toVectorLiteral(queryEmbedding);
        Map<Long, SearchDocument> bestByFile = new LinkedHashMap<>();
        List<SearchDocument> chunkCandidates = jdbcTemplate.query(sql, (rs, rowNum) -> new SearchDocument(
                rs.getLong("file_id"),
                rs.getString("name"),
                rs.getString("chunk_text"),
                rs.getDouble("similarity_score")
        ), vectorLiteral, userId, vectorLiteral, limit * 10);
        addBestByFile(bestByFile, chunkCandidates);

        List<SearchDocument> lexicalCandidates = lexicalCandidates(userId, query);
        for (SearchDocument candidate : lexicalCandidates) {
            SearchDocument current = bestByFile.get(candidate.id());
            if (current == null || !containsSignificantTerm(query, current.content())) {
                bestByFile.put(candidate.id(), candidate);
            }
        }

        return bestByFile.values().stream().toList();
    }

    private void addBestByFile(Map<Long, SearchDocument> bestByFile, List<SearchDocument> candidates) {
        for (SearchDocument candidate : candidates) {
            SearchDocument current = bestByFile.get(candidate.id());
            if (current == null || candidate.similarityScore() > current.similarityScore()) {
                bestByFile.put(candidate.id(), candidate);
            }
        }
    }

    private List<SearchDocument> lexicalCandidates(Long userId, String query) {
        List<String> terms = significantTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT f.id,
                       f.name,
                       f.content
                FROM files f
                WHERE f.user_id = ?
                ORDER BY f.uploaded_at DESC
                """;

        List<SearchDocument> files = jdbcTemplate.query(sql, (rs, rowNum) -> new SearchDocument(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("content"),
                0.0
        ), userId);

        List<SearchDocument> matches = new ArrayList<>();
        for (SearchDocument file : files) {
            int matchIndex = firstMatchIndex(file.content(), terms);
            if (matchIndex >= 0) {
                matches.add(new SearchDocument(file.id(), file.name(), snippetAround(file.content(), matchIndex), 0.0));
            }
        }
        return matches;
    }

    private List<String> significantTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .map(this::normalizeTerm)
                .filter(term -> term.length() > 2)
                .filter(term -> !STOPWORDS.contains(term))
                .collect(Collectors.toCollection(HashSet::new))
                .stream()
                .toList();
    }

    private boolean containsSignificantTerm(String query, String content) {
        return firstMatchIndex(content, significantTerms(query)) >= 0;
    }

    private int firstMatchIndex(String content, List<String> terms) {
        if (content == null || content.isBlank()) {
            return -1;
        }
        String normalizedContent = content.toLowerCase();
        int bestIndex = -1;
        for (String term : terms) {
            for (String variant : variants(term)) {
                int index = normalizedContent.indexOf(variant);
                if (index >= 0 && (bestIndex < 0 || index < bestIndex)) {
                    bestIndex = index;
                }
            }
        }
        return bestIndex;
    }

    private String snippetAround(String content, int matchIndex) {
        String[] words = content.split("\\s+");
        int wordIndex = 0;
        int characters = 0;
        for (int i = 0; i < words.length; i++) {
            characters += words[i].length() + 1;
            if (characters >= matchIndex) {
                wordIndex = i;
                break;
            }
        }

        int start = Math.max(0, wordIndex - 90);
        int end = Math.min(words.length, wordIndex + 90);
        return String.join(" ", Arrays.copyOfRange(words, start, end));
    }

    private Set<String> variants(String term) {
        Set<String> variants = new HashSet<>();
        variants.add(term);
        variants.add(term + "s");
        variants.add(term + "ed");
        variants.add(term + "ing");
        if (term.endsWith("e")) {
            variants.add(term.substring(0, term.length() - 1) + "ied");
            variants.add(term.substring(0, term.length() - 1) + "ying");
        }
        return variants;
    }

    private String normalizeTerm(String term) {
        if (term.endsWith("ied") && term.length() > 3) {
            return term.substring(0, term.length() - 3) + "ie";
        }
        if (term.endsWith("ed") && term.length() > 4) {
            return term.substring(0, term.length() - 2);
        }
        if (term.endsWith("ing") && term.length() > 5) {
            return term.substring(0, term.length() - 3);
        }
        if (term.endsWith("s") && term.length() > 3) {
            return term.substring(0, term.length() - 1);
        }
        return term;
    }
}
