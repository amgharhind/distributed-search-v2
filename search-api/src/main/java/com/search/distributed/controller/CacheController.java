package com.search.distributed.controller;

import com.search.distributed.service.CacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/cache")
@Tag(name = "Cache", description = "Redis cache management")
public class CacheController {

    private final CacheService cacheService;

    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @GetMapping("/keys")
    @Operation(summary = "List all cache keys (uses safe SCAN, not KEYS *)")
    public List<String> keys(@RequestParam(defaultValue = "*") String pattern) {
        return cacheService.scanKeys(pattern);
    }

    @DeleteMapping
    @Operation(summary = "Evict a specific cache entry by key")
    public ResponseEntity<Map<String, String>> evict(@RequestParam String key) {
        cacheService.evict(key);
        return ResponseEntity.ok(Map.of("evicted", key));
    }

    @DeleteMapping("/all")
    @Operation(summary = "Clear all search cache entries")
    public ResponseEntity<Map<String, String>> evictAll() {
        cacheService.evictAll();
        return ResponseEntity.ok(Map.of("status", "cache cleared"));
    }
}