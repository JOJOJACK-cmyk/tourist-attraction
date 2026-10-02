package com.travelprice.domain;
import jakarta.persistence.*;
@Entity @Table(name="chat_membership",uniqueConstraints={@UniqueConstraint(name="uk_chat_room_member",columnNames={"room_id","member_id"}),@UniqueConstraint(name="uk_chat_room_alias",columnNames={"room_id","alias"})})
public class ChatMembership {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="room_id") private ChatRoom room;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="member_id") private Member member;
    @Column(nullable=false,length=16) private String alias;
    @Column(nullable=false) private boolean active=true;
    @Column(nullable=false) private boolean blocked;
    protected ChatMembership(){}
    public ChatMembership(ChatRoom room,Member member,String alias){this.room=room;this.member=member;this.alias=alias;}
    public Long getId(){return id;}public ChatRoom getRoom(){return room;}public Member getMember(){return member;}public String getAlias(){return alias;}public boolean isActive(){return active;}public boolean isBlocked(){return blocked;}
    public void leave(){active=false;}public void kick(){active=false;blocked=true;}public void rejoin(String alias){this.alias=alias;active=true;}
}
