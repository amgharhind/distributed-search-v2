package com.search.distributed.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record RerankRequest(String query, List<Map<String, Object>> documents) {

    public static RerankRequest from(String query, List<Document> documents) {
        List<Map<String, Object>> payload = documents.stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "content", d.getContent() != null ? d.getContent() : "",
                        "bm25_score", d.getBm25Score()
                ))
                .collect(Collectors.toList());
        return new RerankRequest(query, payload);
    }
}