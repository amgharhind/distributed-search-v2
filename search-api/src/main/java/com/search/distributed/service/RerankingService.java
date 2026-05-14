package com.search.distributed.service;

import com.search.distributed.model.Document;
import com.search.distributed.model.RerankRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RerankingService {

    private static final Logger log = LoggerFactory.getLogger(RerankingService.class);

    private final WebClient webClient;
    private final long timeoutSeconds;

    public RerankingService(WebClient rerankingWebClient,
                            @Value("${reranking.timeout-seconds:5}") long timeoutSeconds) {
        this.webClient = rerankingWebClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    @CircuitBreaker(name = "reranking", fallbackMethod = "fallback")
    public List<Document> rerank(String query, List<Document> documents) {
        RerankRequest payload = RerankRequest.from(query, documents);

        Map<?, ?> response = webClient.post()
                .uri("/re-rank")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        if (response == null) return documents;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) response.get("ranked_results");
        if (ranked == null) return documents;

        return ranked.stream().map(r -> {
            Document doc = new Document();
            doc.setId((String) r.get("id"));
            doc.setContent((String) r.get("content"));
            doc.setBm25Score(((Number) r.get("bm25_score")).floatValue());
            doc.setSemanticScore(((Number) r.get("semantic_score")).floatValue());
            doc.setFinalScore(((Number) r.get("final_score")).doubleValue());
            return doc;
        }).collect(Collectors.toList());
    }

    // Circuit is open or re-ranker is slow — degrade gracefully to BM25 order
    public List<Document> fallback(String query, List<Document> documents, Throwable ex) {
        log.warn("Re-ranking unavailable ({}), returning BM25 order", ex.getMessage());
        return documents;
    }
}