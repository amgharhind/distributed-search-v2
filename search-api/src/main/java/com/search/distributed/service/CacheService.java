package com.search.distributed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.search.distributed.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);
    private static final long TTL_SECONDS = 300;
    private static final String KEY_PREFIX = "search::";

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper mapper;

    public CacheService(RedisTemplate<String, String> redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public Optional<List<Document>> get(String cacheKey) {
        String raw = redis.opsForValue().get(cacheKey);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(raw, new TypeReference<>() {}));
        } catch (Exception e) {
            log.warn("Cache deserialisation failed for key {}: {}", cacheKey, e.getMessage());
            redis.delete(cacheKey);
            return Optional.empty();
        }
    }

    public void put(String cacheKey, List<Document> documents) {
        try {
            redis.opsForValue().set(cacheKey, mapper.writeValueAsString(documents), TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache key {}: {}", cacheKey, e.getMessage());
        }
    }

    public void evict(String cacheKey) {
        redis.delete(cacheKey);
    }

    // Safe O(N) scan — never blocks Redis like KEYS *
    public List<String> scanKeys(String pattern) {
        List<String> result = new ArrayList<>();
        ScanOptions opts = ScanOptions.scanOptions().match(KEY_PREFIX + pattern).count(100).build();
        try (var cursor = redis.scan(opts)) {
            cursor.forEachRemaining(result::add);
        } catch (Exception e) {
            log.warn("Redis scan failed: {}", e.getMessage());
        }
        return result;
    }

    public void evictAll() {
        List<String> keys = scanKeys("*");
        if (!keys.isEmpty()) redis.delete(keys);
    }

    public static String buildKey(String query, String field, String fileType,
                                  String sortField, int page, int size) {
        return String.format("%s%s::%s::%s::%s::%d::%d",
                KEY_PREFIX, query, field, fileType, sortField, page, size);
    }
}