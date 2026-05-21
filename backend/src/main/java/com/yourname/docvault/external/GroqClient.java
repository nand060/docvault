package com.yourname.docvault.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourname.docvault.common.ApiException;
import com.yourname.docvault.search.SearchDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
public class GroqClient {
    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public GroqClient(
            @Value("${app.external.groq-api-key}") String apiKey,
            @Value("${app.external.groq-model}") String model,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public void streamSummary(String query, List<SearchDocument> documents, Consumer<String> tokenConsumer) {
        String systemPrompt = """
                You are a document assistant. The user asked: %s.
                The following document chunks were retrieved as the most relevant passages from the user's files.
                Your task:

                Give a detailed, grounded answer using only the retrieved passages.
                If the query asks about a name, entity, phrase, or topic that appears in the passages, treat those passages as relevant and explain everything the documents say about it.
                When multiple documents are provided, compare and combine what each document says. Mention which document each key detail came from when useful.
                If a passage gives only partial context, say what is known from the passage and what is not stated.
                Use clear paragraphs or bullets. Prefer a fuller answer over a one-sentence summary.
                Do NOT invent information not present in the provided passages.
                Do NOT add the phrase "The documents do not directly address this query" 
                """.formatted(query);

        StringBuilder userPrompt = new StringBuilder();
        for (SearchDocument document : documents) {
            userPrompt.append("Document: ").append(document.name())
                    .append(" (relevance: ")
                    .append(Math.round(document.similarityScore() * 100))
                    .append("%)\n")
                    .append(document.content()).append("\n\n");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "stream", true,
                "temperature", 0.2,
                "max_tokens", 900,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt.toString())
                )
        );

        try {
            StringBuilder buffer = new StringBuilder();
            AtomicBoolean emittedToken = new AtomicBoolean(false);
            webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(90))
                    .doOnNext(chunk -> handleChunk(chunk, buffer, tokenConsumer, emittedToken))
                    .blockLast();
            flushBuffer(buffer, tokenConsumer, emittedToken);

            if (!emittedToken.get()) {
                log.warn("Groq stream returned no tokens; falling back to non-stream completion.");
                sendFallbackSummary(body, tokenConsumer);
            }
        } catch (Exception ex) {
            log.error("Groq streaming request failed", ex);
            throw new ApiException("AI summary service unavailable", HttpStatus.BAD_GATEWAY);
        }
    }

    private void handleChunk(String chunk, StringBuilder buffer, Consumer<String> tokenConsumer, AtomicBoolean emittedToken) {
        buffer.append(chunk);
        int newlineIndex;
        while ((newlineIndex = indexOfLineBreak(buffer)) >= 0) {
            String line = buffer.substring(0, newlineIndex).trim();
            buffer.delete(0, newlineIndex + 1);
            processStreamLine(line, tokenConsumer, emittedToken);
        }
    }

    private int indexOfLineBreak(StringBuilder buffer) {
        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return -1;
    }

    private void flushBuffer(StringBuilder buffer, Consumer<String> tokenConsumer, AtomicBoolean emittedToken) {
        String remaining = buffer.toString().trim();
        if (!remaining.isEmpty()) {
            processStreamLine(remaining, tokenConsumer, emittedToken);
        }
    }

    private void processStreamLine(String trimmed, Consumer<String> tokenConsumer, AtomicBoolean emittedToken) {
        if (!trimmed.startsWith("data:")) {
            return;
        }
        String data = trimmed.substring(5).trim();
        if ("[DONE]".equals(data)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            if (!content.isMissingNode() && !content.asText().isEmpty()) {
                tokenConsumer.accept(content.asText());
                emittedToken.set(true);
            }
        } catch (Exception ex) {
            log.debug("Skipping unparsable Groq stream chunk");
        }
    }

    private void sendFallbackSummary(Map<String, Object> body, Consumer<String> tokenConsumer) {
        try {
            Map<String, Object> fallbackBody = Map.of(
                    "model", body.get("model"),
                    "messages", body.get("messages")
            );
            JsonNode response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(fallbackBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(90));
            if (response != null) {
                String content = response.path("choices").path(0).path("message").path("content").asText();
                if (content.isEmpty()) {
                    content = response.path("choices").path(0).path("text").asText();
                }
                if (!content.isEmpty()) {
                    tokenConsumer.accept(content);
                }
            }
        } catch (Exception ex) {
            log.error("Groq fallback request failed", ex);
        }
    }
}
