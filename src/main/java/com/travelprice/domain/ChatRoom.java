package com.travelprice.domain;
import jakarta.persistence.*;
import java.time.*;
import java.security.SecureRandom;
import java.util.HexFormat;
@Entity @Table(name="chat_room")
public class ChatRoom {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=60) private String name;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="owner_id") private Member owner;
    @Column(nullable=false,unique=true,length=64) private String inviteToken;
    @Column(nullable=false) private boolean closed;
    @Column(nullable=false) private LocalDateTime createdAt;
    protected ChatRoom(){}
    public ChatRoom(String name,Member owner){this.name=name;this.owner=owner;var bytes=new byte[32];new SecureRandom().nextBytes(bytes);inviteToken=HexFormat.of().formatHex(bytes);createdAt=LocalDateTime.now(ZoneId.of("Asia/Seoul"));}
    public Long getId(){return id;}public String getName(){return name;}public Member getOwner(){return owner;}public String getInviteToken(){return inviteToken;}public boolean isClosed(){return closed;}public LocalDateTime getCreatedAt(){return createdAt;}
    public void close(){closed=true;}
}
