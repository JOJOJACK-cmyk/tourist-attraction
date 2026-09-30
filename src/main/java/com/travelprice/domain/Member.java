package com.travelprice.domain;
import jakarta.persistence.*;

@Entity
@Table(name="community_member")
public class Member {
    public enum Role { MEMBER, ADMIN }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=30) private String loginId;
    @Column(nullable=false,length=100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private Role role;
    protected Member() {}
    public Member(String loginId,String passwordHash,Role role){this.loginId=loginId;this.passwordHash=passwordHash;this.role=role;}
    public Long getId(){return id;} public String getLoginId(){return loginId;}
    public String getPasswordHash(){return passwordHash;} public Role getRole(){return role;}
    public boolean isAdmin(){return role==Role.ADMIN;}
}
