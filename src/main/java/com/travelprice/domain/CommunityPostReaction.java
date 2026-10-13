package com.travelprice.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name="community_post_reaction",uniqueConstraints=@UniqueConstraint(columnNames={"post_id","member_id"}),
    indexes=@Index(columnList="member_id,saved"))
public class CommunityPostReaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="post_id",nullable=false)
    @OnDelete(action=OnDeleteAction.CASCADE) private CommunityPost post;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="member_id",nullable=false)
    @OnDelete(action=OnDeleteAction.CASCADE) private Member member;
    @Column(nullable=false) private boolean helpful;
    @Column(nullable=false) private boolean saved;
    protected CommunityPostReaction(){}
    public CommunityPostReaction(CommunityPost post,Member member){this.post=post;this.member=member;}
    public CommunityPost getPost(){return post;}
    public boolean isHelpful(){return helpful;}
    public boolean isSaved(){return saved;}
    public void setHelpful(boolean value){helpful=value;}
    public void setSaved(boolean value){saved=value;}
}
