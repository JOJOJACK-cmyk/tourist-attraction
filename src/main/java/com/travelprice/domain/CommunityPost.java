package com.travelprice.domain;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="community_post",indexes=@Index(name="idx_community_destination_created",columnList="destination_id,created_at"))
public class CommunityPost {
    public enum Kind { REVIEW, REPORT, QUESTION }
    public enum Category { FOOD, LODGING, PARKING, TRANSPORT, ADMISSION, SHOPPING, RENTAL, AMENITIES, OTHER }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id") private Member author;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="destination_id") private Destination destination;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=15) private Category category;
    @Column(nullable=false,length=120) private String title;
    @Column(nullable=false,length=10000) private String body;
    private LocalDate visitedAt;
    @Column(length=36) private String imageKey;
    @Column(nullable=false) private boolean hidden;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(nullable=false) private LocalDateTime updatedAt;
    protected CommunityPost() {}
    public CommunityPost(Member author,Destination destination,Kind kind,Category category,String title,String body,LocalDate visitedAt,String imageKey){
        this.author=author;this.createdAt=LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        update(destination,kind,category,title,body,visitedAt,imageKey);
    }
    public void update(Destination destination,Kind kind,Category category,String title,String body,LocalDate visitedAt,String imageKey){
        this.destination=destination;this.kind=kind;this.category=category;this.title=title;this.body=body;this.visitedAt=visitedAt;this.imageKey=imageKey;this.updatedAt=LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
    public void setHidden(boolean hidden){this.hidden=hidden;}
    public Long getId(){return id;} public Member getAuthor(){return author;} public Destination getDestination(){return destination;}
    public Kind getKind(){return kind;} public Category getCategory(){return category;} public String getTitle(){return title;}
    public String getBody(){return body;} public LocalDate getVisitedAt(){return visitedAt;} public String getImageKey(){return imageKey;}
    public boolean isHidden(){return hidden;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
