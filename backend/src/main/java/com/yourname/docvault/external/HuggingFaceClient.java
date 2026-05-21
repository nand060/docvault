package com.yourname.docvault.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.yourname.docvault.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HuggingFaceClient {
    private static final Logger log = LoggerFactory.getLogger(HuggingFaceClient.class);
    private static final String MODEL_URL =
            "https://router.huggingface.co/hf-inference/models/sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

    private final WebClient webClient;

    public HuggingFaceClient(@Value("${app.external.huggingface-token}") String token) {
        this.webClient = WebClient.builder()
                .baseUrl(MODEL_URL)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    public List<Double> embed(String text) {
        try {
            JsonNode response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("inputs", text))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(750)).filter(this::isRetryable))
                    .block(Duration.ofSeconds(45));

            List<Double> embedding = parseEmbedding(response);
            if (embedding.size() != 384) {
                throw new ApiException("Embedding provider returned " + embedding.size() + " dimensions", HttpStatus.BAD_GATEWAY);
            }
            return embedding;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("HuggingFace embedding request failed", ex);
            throw new ApiException("Embedding service unavailable", HttpStatus.BAD_GATEWAY);
        }
    }

    private boolean isRetryable(Throwable throwable) {
        return throwable instanceof WebClientResponseException.TooManyRequests
                || throwable instanceof WebClientResponseException.ServiceUnavailable;
    }

    private List<Double> parseEmbedding(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new ApiException("Invalid embedding response", HttpStatus.BAD_GATEWAY);
        }
        if (node.get(0).isNumber()) {
            List<Double> values = new ArrayList<>();
            node.forEach(value -> values.add(value.asDouble()));
            return values;
        }

        if (node.get(0).isArray()) {
            int dimensions = node.get(0).size();
            double[] sums = new double[dimensions];
            int rows = 0;
            for (JsonNode row : node) {
                if (!row.isArray() || row.size() != dimensions) {
                    throw new ApiException("Invalid embedding response shape", HttpStatus.BAD_GATEWAY);
                }
                for (int i = 0; i < dimensions; i++) {
                    sums[i] += row.get(i).asDouble();
                }
                rows++;
            }
            List<Double> averaged = new ArrayList<>(dimensions);
            for (double sum : sums) {
                averaged.add(sum / rows);
            }
            return averaged;
        }

        throw new ApiException("Invalid embedding response", HttpStatus.BAD_GATEWAY);
    }
}
