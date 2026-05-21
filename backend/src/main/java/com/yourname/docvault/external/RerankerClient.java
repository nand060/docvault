package com.yourname.docvault.external;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

        Map<String, Object> body = Map.of(
                "inputs", Map.of(
                        "source_sentence", query,
                        "sentences", candidates
                )
        );

        try {
            JsonNode response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(60));
            List<Double> scores = parseScores(response, candidates.size());
            log.info("Reranked {} search candidates", candidates.size());
            return scores;
        } catch (Exception ex) {
            log.error("HuggingFace reranker request failed", ex);
            return new ArrayList<>(Collections.nCopies(candidates.size(), 0.0));
        }
    }

    private List<Double> parseScores(JsonNode response, int expectedSize) {
        List<Double> scores = new ArrayList<>();
        if (response != null && response.isArray()) {
            for (JsonNode item : response) {
                if (item.isNumber()) {
                    scores.add(normalize(item.asDouble()));
                } else if (item.has("score")) {
                    scores.add(normalize(item.get("score").asDouble()));
                }
            }
        }

        while (scores.size() < expectedSize) {
            scores.add(0.0);
        }
        if (scores.size() > expectedSize) {
            return scores.subList(0, expectedSize);
        }
        return scores;
    }

    private double normalize(double score) {
        if (score >= 0.0 && score <= 1.0) {
            return score;
        }
        return 1.0 / (1.0 + Math.exp(-score));
    }
}
