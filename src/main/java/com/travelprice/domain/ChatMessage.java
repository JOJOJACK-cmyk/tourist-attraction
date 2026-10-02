package com.travelprice.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="chat_message",indexes=@Index(name="idx_chat_message_room_id",columnList="room_id,id"))
public class ChatMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="room_id") private ChatRoom room;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sender_id") private ChatMembership sender;
    @Column(nullable=false,length=16) private String alias;
    @Column(nullable=false,length=1000) private String body;
    @Column(nullable=false) private LocalDateTime createdAt;
    protected ChatMessage(){}
    public ChatMessage(ChatRoom room,ChatMembership sender,String body){this.room=room;this.sender=sender;this.alias=sender.getAlias();this.body=body;createdAt=LocalDateTime.now(ZoneId.of("Asia/Seoul"));}
    public Long getId(){return id;}public ChatMembership getSender(){return sender;}public String getAlias(){return alias;}public String getBody(){return body;}public LocalDateTime getCreatedAt(){return createdAt;}
}
