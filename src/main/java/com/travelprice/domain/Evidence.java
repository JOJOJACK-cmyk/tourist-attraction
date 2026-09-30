package com.travelprice.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"destination_id", "source_url"}))
public class Evidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Destination destination;
    @Column(nullable = false)
    private String title;
    private String sourceType;
    @Column(name="source_url", nullable = false, length = 700)
    private String url;
    @Column(nullable = false)
    private LocalDate publishedAt;
    @Column(length = 1200)
    private String note;
    protected Evidence() {}
    public Evidence(Destination destination, String title, String sourceType, String url, LocalDate publishedAt, String note) {
        this.destination=destination; this.title=title; this.sourceType=sourceType; this.url=url; this.publishedAt=publishedAt; this.note=note;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getSourceType() { return sourceType; }
    public String getUrl() { return url; }
    public LocalDate getPublishedAt() { return publishedAt; }
    public String getNote() { return note; }
}
