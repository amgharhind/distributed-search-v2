package com.search.distributed.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import com.search.distributed.exception.SearchException;
import com.search.distributed.model.Document;
import com.search.distributed.model.DocumentRequest;
import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final String INDEX = "documents";

    private final ElasticsearchClient esClient;
    private final CacheService cacheService;

    public DocumentService(ElasticsearchClient esClient, CacheService cacheService) {
        this.esClient = esClient;
        this.cacheService = cacheService;
    }

    @PostConstruct
    public void ensureIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX)).value();
            if (!exists) {
                esClient.indices().create(c -> c
                        .index(INDEX)
                        .mappings(m -> m
                                .properties("title",       p -> p.text(t -> t))
                                .properties("content",     p -> p.text(t -> t))
                                .properties("filename",    p -> p.keyword(k -> k))
                                .properties("fileType",    p -> p.keyword(k -> k))
                                .properties("author",      p -> p.keyword(k -> k))
                                .properties("createdDate", p -> p.keyword(k -> k))
                        ));
                log.info("Created Elasticsearch index '{}'", INDEX);
            }
        } catch (Exception e) {
            log.warn("Could not ensure index '{}' at startup: {}", INDEX, e.getMessage());
        }
    }

    public Document create(DocumentRequest request) {
        String id = UUID.randomUUID().toString();
        Document doc = request.toDocument(id);
        try {
            IndexResponse response = esClient.index(i -> i
                    .index(INDEX)
                    .id(id)
                    .document(doc));
            log.debug("Indexed document id={} result={}", id, response.result());
            cacheService.evictAll();
            return doc;
        } catch (IOException e) {
            throw new SearchException("Failed to index document", e);
        }
    }

    public List<Document> bulkCreate(List<DocumentRequest> requests) {
        List<Document> docs = requests.stream()
                .map(r -> r.toDocument(UUID.randomUUID().toString()))
                .collect(Collectors.toList());

        List<BulkOperation> ops = docs.stream()
                .map(doc -> BulkOperation.of(b -> b
                        .index(IndexOperation.of(i -> i
                                .index(INDEX)
                                .id(doc.getId())
                                .document(doc)))))
                .collect(Collectors.toList());

        try {
            BulkResponse response = esClient.bulk(b -> b.operations(ops));
            if (response.errors()) {
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .forEach(item -> log.warn("Bulk index error for id={}: {}", item.id(), item.error().reason()));
            }
            cacheService.evictAll();
            return docs;
        } catch (IOException e) {
            throw new SearchException("Bulk index failed", e);
        }
    }

    public Optional<Document> getById(String id) {
        try {
            GetResponse<Document> response = esClient.get(g -> g.index(INDEX).id(id), Document.class);
            if (!response.found() || response.source() == null) return Optional.empty();
            Document doc = response.source();
            doc.setId(response.id());
            return Optional.of(doc);
        } catch (IOException e) {
            throw new SearchException("Failed to fetch document id=" + id, e);
        }
    }

    public List<Document> list(int page, int size) {
        try {
            SearchResponse<Document> response = esClient.search(s -> s
                    .index(INDEX)
                    .from(page * size)
                    .size(size)
                    .query(q -> q.matchAll(m -> m)),
                    Document.class);

            return response.hits().hits().stream()
                    .map(hit -> {
                        Document doc = hit.source();
                        if (doc == null) return null;
                        doc.setId(hit.id());
                        return doc;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (ElasticsearchException e) {
            if ("index_not_found_exception".equals(e.error().type())) return List.of();
            throw new SearchException("Failed to list documents", e);
        } catch (IOException e) {
            throw new SearchException("Failed to list documents", e);
        }
    }

    public Document update(String id, DocumentRequest request) {
        // Verify the document exists before updating
        getById(id).orElseThrow(() ->
                new IllegalArgumentException("Document not found: " + id));

        Document updated = request.toDocument(id);
        try {
            esClient.update(u -> u
                    .index(INDEX)
                    .id(id)
                    .doc(updated),
                    Document.class);
            cacheService.evictAll();
            return updated;
        } catch (IOException e) {
            throw new SearchException("Failed to update document id=" + id, e);
        }
    }

    public boolean delete(String id) {
        try {
            DeleteResponse response = esClient.delete(d -> d.index(INDEX).id(id));
            boolean deleted = response.result() == Result.Deleted;
            if (deleted) cacheService.evictAll();
            return deleted;
        } catch (IOException e) {
            throw new SearchException("Failed to delete document id=" + id, e);
        }
    }

    public Document upload(MultipartFile file, String author, String titleOverride) throws IOException {
        String filename  = file.getOriginalFilename();
        String fileType  = detectType(filename);
        byte[] bytes     = file.getBytes();
        String content   = extractContent(bytes, fileType);

        DocumentRequest req = new DocumentRequest();
        req.setFilename(filename);
        req.setTitle(titleOverride != null && !titleOverride.isBlank() ? titleOverride : stripExt(filename));
        req.setContent(content.isBlank() ? filename : content);
        req.setFileType(fileType);
        req.setAuthor(author);
        req.setCreatedDate(LocalDate.now().toString());

        return create(req);
    }

    private String detectType(String filename) {
        if (filename == null) return "txt";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "txt";
    }

    private String stripExt(String filename) {
        if (filename == null) return "Untitled";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String extractContent(byte[] bytes, String type) {
        try {
            return switch (type) {
                case "pdf"       -> extractPdf(bytes);
                case "docx","doc"-> extractDocx(bytes);
                default          -> new String(bytes, StandardCharsets.UTF_8);
            };
        } catch (Exception e) {
            log.warn("Text extraction failed for type={}: {}", type, e.getMessage());
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc).trim();
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining("\n"));
        }
    }
}
