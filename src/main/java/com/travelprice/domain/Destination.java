package com.travelprice.domain;

import jakarta.persistence.*;

@Entity
public class Destination {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String slug;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String region;
    private String categories;
    private String aliases;
    private String excludedPlaces;
    protected Destination() {}
    public Destination(String slug, String name, String region, String categories, String aliases, String excludedPlaces) {
        this.slug=slug; this.name=name; this.region=region; this.categories=categories; this.aliases=aliases; this.excludedPlaces=excludedPlaces;
    }
    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getRegion() { return region; }
    public String getCategories() { return categories; }
    public String getAliases() { return aliases; }
    public String getExcludedPlaces() { return excludedPlaces; }
}
