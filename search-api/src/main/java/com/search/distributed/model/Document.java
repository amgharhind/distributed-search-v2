package com.search.distributed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Document {

    private String id;
    private String title;
    private String content;
    private String filename;

    @JsonProperty("fileType")
    private String fileType;

    private String author;

    @JsonProperty("createdDate")
    private String createdDate;

    private float bm25Score;
    private float semanticScore;
    private double finalScore;
    private String snippet;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public float getBm25Score() { return bm25Score; }
    public void setBm25Score(float bm25Score) { this.bm25Score = bm25Score; }

    public float getSemanticScore() { return semanticScore; }
    public void setSemanticScore(float semanticScore) { this.semanticScore = semanticScore; }

    public double getFinalScore() { return finalScore; }
    public void setFinalScore(double finalScore) { this.finalScore = finalScore; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
}