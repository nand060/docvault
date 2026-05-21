package com.yourname.docvault.search;

import com.yourname.docvault.auth.CurrentUser;
import com.yourname.docvault.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public SearchResponse search(@CurrentUser UserPrincipal principal, @Valid @RequestBody SearchRequest request) {
        return searchService.search(principal.id(), request.query());
    }
}
