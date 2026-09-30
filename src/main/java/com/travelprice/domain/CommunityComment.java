package com.travelprice.domain;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="community_comment",indexes=@Index(name="idx_comment_post",columnList="post_id"))
public class CommunityComment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="post_id") private CommunityPost post;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id") private Member author;
    @Column(nullable=false,length=2000) private String body;
    @Column(nullable=false) private LocalDateTime createdAt;
    protected CommunityComment(){}
    public CommunityComment(CommunityPost post,Member author,String body){this.post=post;this.author=author;this.body=body;createdAt=LocalDateTime.now(ZoneId.of("Asia/Seoul"));}
    public Long getId(){return id;} public CommunityPost getPost(){return post;} public Member getAuthor(){return author;}
    public String getBody(){return body;} public LocalDateTime getCreatedAt(){return createdAt;}
}
