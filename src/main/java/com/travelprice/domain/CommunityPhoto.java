package com.travelprice.domain;
import jakarta.persistence.*;

@Entity
public class CommunityPhoto {
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) private Member owner;
    @Column(nullable=false,length=10) private String extension;
    protected CommunityPhoto(){}
    public CommunityPhoto(String id,Member owner,String extension){this.id=id;this.owner=owner;this.extension=extension;}
    public String getId(){return id;} public Member getOwner(){return owner;} public String getExtension(){return extension;}
}
