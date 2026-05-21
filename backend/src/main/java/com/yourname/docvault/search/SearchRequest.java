package com.yourname.docvault.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(@NotBlank @Size(max = 2000) String query) {
}
