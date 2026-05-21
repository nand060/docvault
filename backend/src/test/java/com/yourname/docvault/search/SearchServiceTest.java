package com.yourname.docvault.search;

import com.yourname.docvault.external.GroqClient;
import com.yourname.docvault.external.HuggingFaceClient;
import com.yourname.docvault.external.RerankerClient;
import com.yourname.docvault.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {
    @Test
    void rerankAndFilterRemovesScoresBelowThresholdAndSortsDescending() {
        RerankerClient rerankerClient = new TestRerankerClient(List.of(0.4, 0.91, 0.5));
        SearchService searchService = new SearchService(
                null,
                rerankerClient,
                null,
                null,
                null,
                null,
                5,
                0.5
        );
        List<SearchDocument> documents = List.of(
                new SearchDocument(1L, "a.txt", "alpha", 0.9),
                new SearchDocument(2L, "b.txt", "beta", 0.8),
                new SearchDocument(3L, "c.txt", "gamma", 0.7)
        );

        List<SearchDocument> filtered = searchService.rerankAndFilter("query", documents, 0.5);

        assertThat(filtered).extracting(SearchDocument::id).containsExactly(2L, 3L);
        assertThat(filtered).extracting(SearchDocument::similarityScore).containsExactly(0.91, 0.5);
    }

    @Test
    void rerankAndFilterKeepsExactTermMatchesWhenRerankerReturnsZero() {
        RerankerClient rerankerClient = new TestRerankerClient(List.of(0.0));
        SearchService searchService = new SearchService(
                null,
                rerankerClient,
                null,
                null,
                null,
                null,
                5,
                0.05
        );
        List<SearchDocument> documents = List.of(
                new SearchDocument(1L, "nitroclaw.txt", "Nitroclaw is my favourite cat", 0.3)
        );

        List<SearchDocument> filtered = searchService.rerankAndFilter("cat", documents, 0.05);

        assertThat(filtered).extracting(SearchDocument::id).containsExactly(1L);
        assertThat(filtered).extracting(SearchDocument::similarityScore).containsExactly(0.05);
    }

    @Test
    void rerankAndFilterKeepsMeaningfulTermMatchesForNaturalLanguageQueries() {
        RerankerClient rerankerClient = new TestRerankerClient(List.of(0.0));
        SearchService searchService = new SearchService(
                null,
                rerankerClient,
                null,
                null,
                null,
                null,
                5,
                0.05
        );
        List<SearchDocument> documents = List.of(
                new SearchDocument(1L, "nitroclaw.txt", "Nitroclaw is my favourite cat", 0.3)
        );

        List<SearchDocument> filtered = searchService.rerankAndFilter("explain what nitroclaw does", documents, 0.05);

        assertThat(filtered).extracting(SearchDocument::id).containsExactly(1L);
        assertThat(filtered).extracting(SearchDocument::similarityScore).containsExactly(0.05);
    }

    @Test
    void rerankAndFilterKeepsMorphologicalTermMatches() {
        RerankerClient rerankerClient = new TestRerankerClient(List.of(0.0));
        SearchService searchService = new SearchService(
                null,
                rerankerClient,
                null,
                null,
                null,
                null,
                5,
                0.05
        );
        List<SearchDocument> documents = List.of(
                new SearchDocument(1L, "cat.txt", "It gave me a heart attack and I died there on spot.", 0.3)
        );

        List<SearchDocument> filtered = searchService.rerankAndFilter("how did i die", documents, 0.05);

        assertThat(filtered).extracting(SearchDocument::id).containsExactly(1L);
        assertThat(filtered).extracting(SearchDocument::similarityScore).containsExactly(0.05);
    }

    private static class TestRerankerClient extends RerankerClient {
        private final List<Double> scores;

        TestRerankerClient(List<Double> scores) {
            super("test-token");
            this.scores = scores;
        }

        @Override
        public List<Double> rerank(String query, List<String> candidates) {
            return scores;
        }
    }
}
