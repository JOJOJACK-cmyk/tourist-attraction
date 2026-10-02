package com.travelprice.domain;
import jakarta.persistence.*;

@Entity
@Table(name="community_member",uniqueConstraints=@UniqueConstraint(name="uk_member_social_identity",columnNames={"social_provider","social_subject"}))
public class Member {
    public enum Role { MEMBER, ADMIN }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=30) private String loginId;
    @Column(nullable=false,length=100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private Role role;
    @Column(name="social_provider",length=16) private String socialProvider;
    @Column(name="social_subject",length=64) private String socialSubject;
    protected Member() {}
    public Member(String loginId,String passwordHash,Role role){this.loginId=loginId;this.passwordHash=passwordHash;this.role=role;}
    public static Member social(String loginId,String passwordHash,String provider,String subject){
        var member=new Member(loginId,passwordHash,Role.MEMBER);member.socialProvider=provider;member.socialSubject=subject;return member;
    }
    public boolean isSocial(){return socialProvider!=null;}
    public String getSocialProvider(){return socialProvider;}
    public Long getId(){return id;} public String getLoginId(){return loginId;}
    public String getPasswordHash(){return passwordHash;} public Role getRole(){return role;}
    public boolean isAdmin(){return role==Role.ADMIN;}
}
