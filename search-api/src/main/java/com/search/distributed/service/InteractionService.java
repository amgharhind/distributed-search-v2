package com.search.distributed.service;

import com.search.distributed.model.Document;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Boosts document ranking using click-through data stored in Redis.
 * Records a click → increments the document's interaction counter → used
 * as a personalization signal in the final ranking pass.
 */
@Service
public class InteractionService {

    private static final String KEY_PREFIX = "interaction::";
    private static final double BM25_WEIGHT = 0.7;
    private static final double INTERACTION_WEIGHT = 0.3;

    private final RedisTemplate<String, String> redis;

    public InteractionService(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public void recordClick(String documentId) {
        redis.opsForValue().increment(KEY_PREFIX + documentId);
    }

    public long getInteractionScore(String documentId) {
        String val = redis.opsForValue().get(KEY_PREFIX + documentId);
        return val != null ? Long.parseLong(val) : 0L;
    }

    public List<Document> applyInteractionBoost(List<Document> documents) {
        documents.forEach(doc -> {
            long clicks = getInteractionScore(doc.getId());
            double boosted = BM25_WEIGHT * doc.getBm25Score() + INTERACTION_WEIGHT * clicks;
            doc.setFinalScore(boosted);
        });
        return documents.stream()
                .sorted(Comparator.comparingDouble(Document::getFinalScore).reversed())
                .toList();
    }
}