package com.yourname.docvault.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FileChunker {
    private static final Logger log = LoggerFactory.getLogger(FileChunker.class);
    static final int CHUNK_WORDS = 300;
    static final int OVERLAP_WORDS = 50;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            log.info("Chunked empty text into 0 chunks");
            return List.of();
        }

        String normalized = text.trim().replaceAll("\\s+", " ");
        String[] words = normalized.split(" ");
        if (words.length <= CHUNK_WORDS) {
            log.info("Chunked {} words into 1 chunk", words.length);
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        int step = CHUNK_WORDS - OVERLAP_WORDS;
        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(start + CHUNK_WORDS, words.length);
            chunks.add(String.join(" ", List.of(words).subList(start, end)));
            if (end == words.length) {
                break;
            }
        }

        log.info("Chunked {} words into {} chunks", words.length, chunks.size());
        return chunks;
    }
}
