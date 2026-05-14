package com.search.distributed.model;

import java.util.List;

public record SearchResult(
        List<Document> documents,
        long totalHits,
        boolean fromCache,
        boolean reranked,
        long latencyMs
) {}