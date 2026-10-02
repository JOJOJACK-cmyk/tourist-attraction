package com.travelprice.api;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
public final class ChatModels {
    private ChatModels(){}
    public record RoomInput(@NotBlank @Size(min=2,max=60)String name,@NotBlank @Size(min=2,max=16)String alias){}
    public record JoinInput(@NotBlank @Size(min=2,max=16)String alias){}
    public record MessageInput(@NotBlank @Size(max=1000)String body){}
    public record Participant(Long id,String alias,boolean owner,boolean mine){}
    public record RoomView(Long id,String name,boolean closed,boolean owner,int memberCount,String invitePath,List<Participant> participants){}
    public record InviteView(String name,boolean closed,int memberCount){}
    public record MessageView(Long id,String body,String alias,LocalDateTime createdAt,boolean mine){}
    public record History(List<MessageView> messages,Long nextBefore){}
    /** Internal event contains a room-local membership ID, never an account identifier. */
    public record RoomEvent(Long roomId,String type,Long affectedMembership,Long messageId,String body,String alias,LocalDateTime createdAt){}
}
