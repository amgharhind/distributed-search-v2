package com.search.distributed.controller;

import com.search.distributed.service.AnalyticsService;
import com.search.distributed.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/analytics")
@Tag(name = "Analytics", description = "Search query and click-through analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final DocumentService  documentService;

    public AnalyticsController(AnalyticsService analyticsService, DocumentService documentService) {
        this.analyticsService = analyticsService;
        this.documentService  = documentService;
    }

    @GetMapping
    @Operation(summary = "Top searched queries and most-clicked documents")
    public Map<String, Object> analytics(@RequestParam(defaultValue = "10") int n) {
        List<Map<String, Object>> topQueries = analyticsService.topQueries(n);

        List<Map<String, Object>> topClicked = analyticsService.topClickedIds(n).stream()
                .map(entry -> {
                    String docId = (String) entry.get("docId");
                    String title = documentService.getById(docId)
                            .map(d -> d.getTitle() != null ? d.getTitle() : d.getFilename())
                            .orElse(docId.substring(0, Math.min(8, docId.length())) + "…");
                    Map<String, Object> enriched = new LinkedHashMap<>(entry);
                    enriched.put("title", title);
                    return enriched;
                })
                .collect(Collectors.toList());

        return Map.of("topQueries", topQueries, "topClicked", topClicked);
    }
}
