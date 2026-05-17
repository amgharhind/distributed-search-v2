package com.search.distributed.controller;

import com.search.distributed.model.Document;
import com.search.distributed.model.DocumentRequest;
import com.search.distributed.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/documents")
@Tag(name = "Documents", description = "CRUD operations for documents in the Elasticsearch index")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload and index a file (PDF, DOCX, TXT, HTML, JSON, XML, CSV, MD…)")
    public Document upload(@RequestParam("file") MultipartFile file,
                           @RequestParam(required = false) String author,
                           @RequestParam(required = false) String title) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("file must not be empty");
        return documentService.upload(file, author, title);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Index a new document")
    public Document create(@RequestBody DocumentRequest request) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return documentService.create(request);
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Bulk-index multiple documents in one request")
    public Map<String, Object> bulkCreate(@RequestBody List<DocumentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("request body must contain at least one document");
        }
        List<Document> created = documentService.bulkCreate(requests);
        return Map.of("indexed", created.size(), "documents", created);
    }

    @GetMapping
    @Operation(summary = "List all documents with pagination")
    public List<Document> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documentService.list(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a document by ID")
    public ResponseEntity<Document> getById(@PathVariable String id) {
        return documentService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing document (full replace)")
    public ResponseEntity<Document> update(@PathVariable String id,
                                           @RequestBody DocumentRequest request) {
        try {
            return ResponseEntity.ok(documentService.update(id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document by ID")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        boolean deleted = documentService.delete(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("deleted", id));
    }
}
