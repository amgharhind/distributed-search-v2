package com.search.distributed.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentRequest {

    private String title;
    private String content;
    private String filename;
    private String fileType;
    private String author;
    private String createdDate;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    public Document toDocument(String id) {
        Document doc = new Document();
        doc.setId(id);
        doc.setTitle(this.title);
        doc.setContent(this.content);
        doc.setFilename(this.filename);
        doc.setFileType(this.fileType);
        doc.setAuthor(this.author);
        doc.setCreatedDate(this.createdDate);
        return doc;
    }
}
