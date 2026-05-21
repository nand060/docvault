package com.yourname.docvault.search;

public record SearchDocument(Long id, String name, String content, double similarityScore) {
    public SearchResult toResult() {
        return new SearchResult(id, name, similarityScore);
    }
}
