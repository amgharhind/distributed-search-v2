package com.search.distributed.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final String QUERIES_KEY    = "analytics:queries";
    private static final String INTERACTION_PREFIX = "interaction::";
    private static final int    MAX_STORED_QUERIES = 500;

    private final RedisTemplate<String, String> redis;

    public AnalyticsService(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public void recordQuery(String query) {
        String normalized = query.toLowerCase().trim();
        redis.opsForZSet().incrementScore(QUERIES_KEY, normalized, 1);
        Long size = redis.opsForZSet().zCard(QUERIES_KEY);
        if (size != null && size > MAX_STORED_QUERIES) {
            redis.opsForZSet().removeRange(QUERIES_KEY, 0, size - MAX_STORED_QUERIES - 1);
        }
    }

    public List<Map<String, Object>> topQueries(int n) {
        Set<ZSetOperations.TypedTuple<String>> top =
                redis.opsForZSet().reverseRangeWithScores(QUERIES_KEY, 0, n - 1);
        if (top == null) return List.of();
        return top.stream()
                .map(t -> Map.<String, Object>of(
                        "query", t.getValue() != null ? t.getValue() : "",
                        "count", t.getScore() != null ? t.getScore().longValue() : 0L))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> topClickedIds(int n) {
        ScanOptions opts = ScanOptions.scanOptions()
                .match(INTERACTION_PREFIX + "*").count(200).build();
        List<String> keys = new ArrayList<>();
        try (var cursor = redis.scan(opts)) {
            cursor.forEachRemaining(keys::add);
        } catch (Exception e) {
            log.warn("Interaction scan failed: {}", e.getMessage());
        }

        return keys.stream()
                .map(key -> {
                    String docId = key.substring(INTERACTION_PREFIX.length());
                    String raw = redis.opsForValue().get(key);
                    long clicks = raw != null ? Long.parseLong(raw) : 0L;
                    return Map.<String, Object>of("docId", docId, "clicks", clicks);
                })
                .sorted(Comparator.comparingLong((Map<String, Object> m) ->
                        (Long) m.get("clicks")).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }
}
