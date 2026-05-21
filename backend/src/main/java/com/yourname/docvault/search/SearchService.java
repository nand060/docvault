package com.yourname.docvault.search;

import com.yourname.docvault.external.GroqClient;
import com.yourname.docvault.external.HuggingFaceClient;
import com.yourname.docvault.external.RerankerClient;
import com.yourname.docvault.user.User;
import com.yourname.docvault.user.UserService;
import com.yourname.docvault.websocket.SocketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

@Service
public class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final HuggingFaceClient huggingFaceClient;
    private final RerankerClient rerankerClient;
    private final GroqClient groqClient;
    private final VectorSearchRepository vectorSearchRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final int topK;
    private final double rerankerThreshold;

    public SearchService(
            HuggingFaceClient huggingFaceClient,
            RerankerClient rerankerClient,
            GroqClient groqClient,
            VectorSearchRepository vectorSearchRepository,
            UserService userService,
            SimpMessagingTemplate messagingTemplate,
            @Value("${search.top-k:${app.search.top-k:5}}") int topK,
            @Value("${search.reranker.threshold:0.1}") double rerankerThreshold
    ) {
        this.huggingFaceClient = huggingFaceClient;
        this.rerankerClient = rerankerClient;
        this.groqClient = groqClient;
        this.vectorSearchRepository = vectorSearchRepository;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.topK = topK;
        this.rerankerThreshold = rerankerThreshold;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(Long userId, String query) {
        User user = userService.requireUser(userId);
        send(userId, "status", "Embedding query");
        List<Double> queryEmbedding = huggingFaceClient.embed(query);

        boolean aiMode = user.isAiAccess();
        send(userId, "status", "Searching your files");
        List<SearchDocument> documents = vectorSearchRepository.search(userId, queryEmbedding, topK);
        documents = rerankAndFilter(query, documents, rerankerThreshold);
        List<SearchResult> results = documents.stream().map(SearchDocument::toResult).toList();

        if (documents.isEmpty()) {
            String message = "No documents matched your query.";
            if (aiMode) {
                send(userId, "done", message);
                log.info("No reranked search results for AI query by user {}", userId);
                return new SearchResponse("ai", List.of(), message);
            }
            send(userId, "results", results);
            log.info("No reranked search results for user {}", userId);
            return new SearchResponse("semantic", results, message);
        }

        if (!aiMode) {
            send(userId, "results", results);
            log.info("Returned {} search results for user {}", results.size(), userId);
            return new SearchResponse("semantic", results, "Search complete");
        }

        send(userId, "ai-start", results);
        groqClient.streamSummary(query, documents, token -> send(userId, "ai-token", token));
        send(userId, "done", "AI summary complete");
        log.info("Streamed AI search summary for user {}", userId);
        return new SearchResponse("ai", results, "AI summary streamed over websocket");
    }

    List<SearchDocument> rerankAndFilter(String query, List<SearchDocument> documents, double threshold) {
        if (documents.isEmpty()) {
            return List.of();
        }

        List<Double> scores = rerankerClient.rerank(query, documents.stream().map(SearchDocument::content).toList());
        List<SearchDocument> reranked = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            double score = i < scores.size() ? scores.get(i) : 0.0;
            SearchDocument document = documents.get(i);
            if (score >= threshold) {
                reranked.add(new SearchDocument(document.id(), document.name(), document.content(), score));
            }
        }
        reranked.sort(Comparator.comparingDouble(SearchDocument::similarityScore).reversed());
        return reranked;
    }

    private void send(Long userId, String type, Object payload) {
        messagingTemplate.convertAndSend(
                "/topic/search-results/" + userId,
                SocketEvent.of(type, payload)
        );
    }
}
