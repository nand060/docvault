package com.yourname.docvault.search;

import com.yourname.docvault.external.GroqClient;
import com.yourname.docvault.external.HuggingFaceClient;
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

@Service
public class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final HuggingFaceClient huggingFaceClient;
    private final GroqClient groqClient;
    private final VectorSearchRepository vectorSearchRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final int topK;

    public SearchService(
            HuggingFaceClient huggingFaceClient,
            GroqClient groqClient,
            VectorSearchRepository vectorSearchRepository,
            UserService userService,
            SimpMessagingTemplate messagingTemplate,
            @Value("${app.search.top-k}") int topK
    ) {
        this.huggingFaceClient = huggingFaceClient;
        this.groqClient = groqClient;
        this.vectorSearchRepository = vectorSearchRepository;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.topK = topK;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(Long userId, String query) {
        User user = userService.requireUser(userId);
        send(userId, "status", "Embedding query");
        List<Double> queryEmbedding = huggingFaceClient.embed(query);

        boolean aiMode = user.isAiAccess();
        send(userId, "status", "Searching your files");
        List<SearchDocument> documents = vectorSearchRepository.search(userId, queryEmbedding, topK, aiMode);
        List<SearchResult> results = documents.stream().map(SearchDocument::toResult).toList();

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

    private void send(Long userId, String type, Object payload) {
        messagingTemplate.convertAndSend(
                "/topic/search-results/" + userId,
                SocketEvent.of(type, payload)
        );
    }
}
