package com.yourname.docvault.external;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class RerankerClient {
    private static final Logger log = LoggerFactory.getLogger(RerankerClient.class);

    private final WebClient webClient;

    public RerankerClient(@Value("${app.external.huggingface-token}") String token) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api-inference.huggingface.co/models/BAAI/bge-reranker-base")
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    public List<Double> rerank(String query, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<List<String>> inputs = candidates.stream()
                .map(candidate -> List.of(query, candidate))
                .toList();

        try {
            JsonNode response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new RerankRequest(inputs))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(responseBody -> {
                                        log.error("HuggingFace reranker request failed with HTTP {}: {}",
                                                clientResponse.statusCode().value(), responseBody);
                                        return Mono.error(new IllegalStateException("Reranker request failed with HTTP "
                                                + clientResponse.statusCode().value()));
                                    }))
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(60));
            List<Double> scores = parseScores(response, candidates.size());
            log.info("Reranked {} search candidates", candidates.size());
            log.debug("Reranker scores by candidate: {}", scores);
            return scores;
        } catch (Exception ex) {
            log.error("HuggingFace reranker request failed", ex);
            return new ArrayList<>(Collections.nCopies(candidates.size(), 0.0));
        }
    }

    private List<Double> parseScores(JsonNode response, int expectedSize) {
        List<Double> scores = new ArrayList<>();
        try {
            if (response != null && response.isArray()) {
                for (JsonNode item : response) {
                    if (item.isArray() && !item.isEmpty() && item.get(0).has("score")) {
                        scores.add(item.get(0).get("score").asDouble());
                    } else {
                        throw new IllegalArgumentException("Unexpected reranker response item: " + item);
                    }
                }
            }

            if (scores.size() != expectedSize) {
                throw new IllegalArgumentException("Expected " + expectedSize + " reranker scores but received " + scores.size());
            }
            return scores;
        } catch (Exception ex) {
            log.error("Failed to parse HuggingFace reranker response: {}", response, ex);
            return new ArrayList<>(Collections.nCopies(expectedSize, 0.0));
        }
    }

    private record RerankRequest(List<List<String>> inputs) {
    }
}
