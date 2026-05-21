package com.yourname.docvault.file;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileChunkerTest {
    private final FileChunker fileChunker = new FileChunker();

    @Test
    void emptyStringReturnsNoChunks() {
        assertThat(fileChunker.chunk("")).isEmpty();
    }

    @Test
    void shortTextReturnsSingleChunk() {
        assertThat(fileChunker.chunk("one two three")).containsExactly("one two three");
    }

    @Test
    void longTextSplitsIntoOverlappingChunks() {
        String text = numberedWords(650);

        List<String> chunks = fileChunker.chunk(text);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).split(" ")).hasSize(300);
        assertThat(chunks.get(1).split(" ")).hasSize(300);
        assertThat(chunks.get(2).split(" ")).hasSize(150);
    }

    @Test
    void consecutiveChunksOverlapByFiftyWords() {
        List<String> chunks = fileChunker.chunk(numberedWords(350));

        List<String> firstTail = List.of(chunks.get(0).split(" ")).subList(250, 300);
        List<String> secondHead = List.of(chunks.get(1).split(" ")).subList(0, 50);
        assertThat(secondHead).containsExactlyElementsOf(firstTail);
    }

    private String numberedWords(int count) {
        return String.join(" ", IntStream.rangeClosed(1, count)
                .mapToObj(number -> "w" + number)
                .toList());
    }
}
