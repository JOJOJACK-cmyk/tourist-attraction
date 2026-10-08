package com.travelprice.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="visit_review",indexes={@Index(name="idx_visit_destination_status_created",columnList="destination_id,status,created_at"),@Index(name="idx_visit_author_created",columnList="author_id,created_at")})
public class VisitReview {
    public enum Status { PENDING, APPROVED, REJECTED }
    public enum Feeling { SATISFIED, UNSATISFIED, NEUTRAL, NOT_USED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id") private Member author;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="destination_id") private Destination destination;
    @Column(nullable=false) private LocalDate visitedAt;
    @Column(nullable=false,length=120) private String title;
    @Column(length=120) private String item;
    private Long spentWon;
    private Integer waitingMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Feeling foodCost;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Feeling lodgingCost;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Feeling service;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Feeling crowding;
    @Column(nullable=false,length=2000) private String goodPoints;
    @Column(nullable=false,length=2000) private String badPoints;
    @Column(length=36) private String imageKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Status status;
    @Column(length=500) private String reviewNote;
    @ManyToOne(fetch=FetchType.LAZY) private Member reviewer;
    private LocalDateTime reviewedAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(nullable=false) private LocalDateTime updatedAt;
    protected VisitReview(){}
    public VisitReview(Member author){this.author=author;this.createdAt=now();}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Seoul"));}
    public void update(Destination destination,LocalDate visitedAt,String title,String item,Long spentWon,Integer waitingMinutes,
            Feeling foodCost,Feeling lodgingCost,Feeling service,Feeling crowding,String goodPoints,String badPoints,String imageKey){
        this.destination=destination;this.visitedAt=visitedAt;this.title=title;this.item=item;this.spentWon=spentWon;this.waitingMinutes=waitingMinutes;
        this.foodCost=foodCost;this.lodgingCost=lodgingCost;this.service=service;this.crowding=crowding;this.goodPoints=goodPoints;this.badPoints=badPoints;this.imageKey=imageKey;
        this.status=Status.PENDING;this.reviewNote=null;this.reviewer=null;this.reviewedAt=null;this.updatedAt=now();
    }
    public void moderate(Status status,String note,Member reviewer){this.status=status;this.reviewNote=note;this.reviewer=reviewer;this.reviewedAt=now();this.updatedAt=now();}
    public Long getId(){return id;} public Long getVersion(){return version;} public Member getAuthor(){return author;}
    public Destination getDestination(){return destination;} public LocalDate getVisitedAt(){return visitedAt;}
    public String getTitle(){return title;} public String getItem(){return item;} public Long getSpentWon(){return spentWon;}
    public Integer getWaitingMinutes(){return waitingMinutes;} public Feeling getFoodCost(){return foodCost;}
    public Feeling getLodgingCost(){return lodgingCost;} public Feeling getService(){return service;} public Feeling getCrowding(){return crowding;}
    public String getGoodPoints(){return goodPoints;} public String getBadPoints(){return badPoints;} public String getImageKey(){return imageKey;}
    public Status getStatus(){return status;} public String getReviewNote(){return reviewNote;} public LocalDateTime getReviewedAt(){return reviewedAt;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
