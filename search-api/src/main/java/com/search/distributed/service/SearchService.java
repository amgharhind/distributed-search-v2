package com.search.distributed.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.search.distributed.exception.SearchException;
import com.search.distributed.model.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final String INDEX = "documents";

    private final ElasticsearchClient esClient;

    public SearchService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public List<Document> search(String query, String field, String fileType,
                                 String sortField, String sortOrder, int page, int size) {
        String searchField = field != null ? field : "content";
        try {
            SearchResponse<Document> response = esClient.search(s -> {
                s.index(INDEX).from(page * size).size(size);

                s.query(q -> q.bool(b -> {
                    b.must(m -> m.match(mq -> mq.field(searchField).query(query)));
                    if (fileType != null && !fileType.isBlank()) {
                        b.filter(f -> f.term(t -> t.field("fileType").value(fileType)));
                    }
                    return b;
                }));

                if (sortField != null && !sortField.isBlank()) {
                    SortOrder order = "desc".equalsIgnoreCase(sortOrder) ? SortOrder.Desc : SortOrder.Asc;
                    s.sort(so -> so.field(f -> f.field(sortField).order(order)));
                }

                return s;
            }, Document.class);

            return mapHits(response);
        } catch (IOException e) {
            throw new SearchException("General search failed", e);
        }
    }

    public List<Document> wildcardSearch(String pattern, String field) {
        if (pattern == null || pattern.isBlank()) throw new IllegalArgumentException("pattern must not be blank");
        try {
            SearchResponse<Document> response = esClient.search(s -> s
                    .index(INDEX)
                    .query(q -> q.wildcard(w -> w.field(field != null ? field : "content").value(pattern))),
                    Document.class);
            return mapHits(response);
        } catch (IOException e) {
            throw new SearchException("Wildcard search failed", e);
        }
    }

    public List<Document> exactPhraseSearch(String phrase, String field) {
        if (phrase == null || phrase.isBlank()) throw new IllegalArgumentException("phrase must not be blank");
        try {
            SearchResponse<Document> response = esClient.search(s -> s
                    .index(INDEX)
                    .query(q -> q.matchPhrase(mp -> mp.field(field != null ? field : "content").query(phrase))),
                    Document.class);
            return mapHits(response);
        } catch (IOException e) {
            throw new SearchException("Exact phrase search failed", e);
        }
    }

    public List<Document> proximitySearch(String words, String field, int slop) {
        if (words == null || words.isBlank()) throw new IllegalArgumentException("words must not be blank");
        try {
            SearchResponse<Document> response = esClient.search(s -> s
                    .index(INDEX)
                    .query(q -> q.matchPhrase(mp -> mp
                            .field(field != null ? field : "content")
                            .query(words)
                            .slop(slop))),
                    Document.class);
            return mapHits(response);
        } catch (IOException e) {
            throw new SearchException("Proximity search failed", e);
        }
    }

    public List<Document> rangeSearch(String field, String gte, String lte) {
        if (field == null || field.isBlank()) throw new IllegalArgumentException("field must not be blank");
        try {
            SearchResponse<Document> response = esClient.search(s -> s
                    .index(INDEX)
                    .query(q -> q.range(r -> {
                        r.field(field);
                        if (gte != null) r.gte(co.elastic.clients.json.JsonData.of(gte));
                        if (lte != null) r.lte(co.elastic.clients.json.JsonData.of(lte));
                        return r;
                    })),
                    Document.class);
            return mapHits(response);
        } catch (IOException e) {
            throw new SearchException("Range search failed", e);
        }
    }

    private List<Document> mapHits(SearchResponse<Document> response) {
        return response.hits().hits().stream()
                .map(hit -> {
                    Document doc = hit.source();
                    if (doc == null) return null;
                    doc.setId(hit.id());
                    doc.setBm25Score(hit.score() != null ? hit.score().floatValue() : 0f);
                    return doc;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}