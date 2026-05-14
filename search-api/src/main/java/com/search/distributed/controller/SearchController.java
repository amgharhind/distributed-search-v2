package com.search.distributed.controller;

import com.search.distributed.model.Document;
import com.search.distributed.model.SearchResult;
import com.search.distributed.service.CacheService;
import com.search.distributed.service.InteractionService;
import com.search.distributed.service.RerankingService;
import com.search.distributed.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v2/search")
@Tag(name = "Search", description = "Distributed search endpoints with optional caching and re-ranking")
public class SearchController {

    private final SearchService searchService;
    private final RerankingService rerankingService;
    private final CacheService cacheService;
    private final InteractionService interactionService;

    public SearchController(SearchService searchService, RerankingService rerankingService,
                            CacheService cacheService, InteractionService interactionService) {
        this.searchService = searchService;
        this.rerankingService = rerankingService;
        this.cacheService = cacheService;
        this.interactionService = interactionService;
    }

    @GetMapping
    @Operation(summary = "Full-text search with optional caching and semantic re-ranking")
    public SearchResult search(
            @RequestParam String query,
            @RequestParam(defaultValue = "content") String field,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "true") boolean cache,
            @RequestParam(defaultValue = "true") boolean rerank) {

        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");

        long start = System.currentTimeMillis();
        String cacheKey = CacheService.buildKey(query, field, fileType, sortField, page, size);

        if (cache) {
            Optional<List<Document>> cached = cacheService.get(cacheKey);
            if (cached.isPresent()) {
                return new SearchResult(cached.get(), cached.get().size(), true, false,
                        System.currentTimeMillis() - start);
            }
        }

        List<Document> results = searchService.search(query, field, fileType, sortField, sortOrder, page, size);

        boolean reranked = false;
        if (rerank && !results.isEmpty()) {
            results = rerankingService.rerank(query, results);
            reranked = true;
        }

        if (cache) cacheService.put(cacheKey, results);

        return new SearchResult(results, results.size(), false, reranked, System.currentTimeMillis() - start);
    }

    @GetMapping("/wildcard")
    @Operation(summary = "Wildcard search (e.g. pattern=mach*)")
    public List<Document> wildcard(@RequestParam String pattern,
                                   @RequestParam(defaultValue = "content") String field) {
        return searchService.wildcardSearch(pattern, field);
    }

    @GetMapping("/exact-phrase")
    @Operation(summary = "Exact phrase search")
    public List<Document> exactPhrase(@RequestParam String phrase,
                                      @RequestParam(defaultValue = "content") String field) {
        return searchService.exactPhraseSearch(phrase, field);
    }

    @GetMapping("/proximity")
    @Operation(summary = "Proximity search — words within N positions of each other")
    public List<Document> proximity(@RequestParam String words,
                                    @RequestParam(defaultValue = "content") String field,
                                    @RequestParam(defaultValue = "5") int slop) {
        return searchService.proximitySearch(words, field, slop);
    }

    @GetMapping("/range")
    @Operation(summary = "Range search on a numeric or date field")
    public List<Document> range(@RequestParam String field,
                                @RequestParam(required = false) String gte,
                                @RequestParam(required = false) String lte) {
        return searchService.rangeSearch(field, gte, lte);
    }

    @PostMapping("/interaction/{documentId}")
    @Operation(summary = "Record a click on a document to improve personalised ranking")
    public void recordInteraction(@PathVariable String documentId) {
        interactionService.recordClick(documentId);
    }
}