package com.yourname.docvault.search;

import java.util.List;

public record SearchResponse(String mode, List<SearchResult> results, String message) {
}
