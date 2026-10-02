package com.travelprice.service;
import com.travelprice.api.*;
import com.travelprice.api.ChatModels.*;
import com.travelprice.domain.*;
import com.travelprice.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @Transactional
public class ChatService {
    private final ChatRoomRepository rooms;private final ChatMembershipRepository memberships;private final ChatMessageRepository messages;private final MemberService members;private final ApplicationEventPublisher events;
    public ChatService(ChatRoomRepository rooms,ChatMembershipRepository memberships,ChatMessageRepository messages,MemberService members,ApplicationEventPublisher events){this.rooms=rooms;this.memberships=memberships;this.messages=messages;this.members=members;this.events=events;}
    @Transactional(readOnly=true) public List<RoomView> list(Authentication auth){var member=members.require(auth);return memberships.findByMemberIdAndActiveTrueOrderByIdDesc(member.getId()).stream().map(m->view(m.getRoom(),member,false)).toList();}
    public RoomView create(RoomInput input,Authentication auth){var member=members.require(auth);var name=trim(input.name(),2,60);var alias=trim(input.alias(),2,16);var room=rooms.saveAndFlush(new ChatRoom(name,member));memberships.saveAndFlush(new ChatMembership(room,member,alias));return view(room,member,true);}
    @Transactional(readOnly=true) public InviteView invite(String token,Authentication auth){members.require(auth);var room=token(token);return new InviteView(room.getName(),room.isClosed(),(int)memberships.countByRoomIdAndActiveTrue(room.getId()));}
    public RoomView join(String token,JoinInput input,Authentication auth){
        var member=members.require(auth);var found=token(token);var room=locked(found.getId());open(room);var alias=trim(input.alias(),2,16);
        var existing=memberships.findByRoomIdAndMemberId(room.getId(),member.getId()).orElse(null);
        if(existing!=null && existing.isBlocked())throw new ApiException(403,"이 방에 다시 참여할 수 없어요.");
        if(existing!=null && existing.isActive())return view(room,member,true);
        if(memberships.countByRoomIdAndActiveTrue(room.getId())>=50)throw new ApiException(409,"참여 인원이 가득 찼어요.");
        if((existing==null || !alias.equals(existing.getAlias())) && memberships.existsByRoomIdAndAlias(room.getId(),alias))throw new ApiException(409,"이 방에서 이미 사용 중인 이름이에요.");
        if(existing==null)memberships.saveAndFlush(new ChatMembership(room,member,alias));else{existing.rejoin(alias);memberships.flush();}
        changed(room.getId(),"members",null);return view(room,member,true);
    }
    @Transactional(readOnly=true) public RoomView detail(Long id,Authentication auth){var member=members.require(auth);var membership=active(id,member);return view(membership.getRoom(),member,true);}
    @Transactional(readOnly=true) public History history(Long id,Long before,Authentication auth){
        var member=members.require(auth);var viewer=active(id,member);if(before!=null && before<1)throw new ApiException(400,"대화 위치를 확인해주세요.");
        var rows=before==null?messages.findTop50ByRoomIdOrderByIdDesc(id):messages.findTop50ByRoomIdAndIdLessThanOrderByIdDesc(id,before);
        var sorted=new ArrayList<>(rows);Collections.reverse(sorted);
        return new History(sorted.stream().map(m->message(m,viewer)).toList(),rows.size()==50?sorted.get(0).getId():null);
    }
    public MessageView send(Long id,MessageInput input,Authentication auth){
        var member=members.require(auth);var room=locked(id);open(room);var sender=active(id,member);
        var saved=messages.saveAndFlush(new ChatMessage(room,sender,trim(input.body(),1,1000)));
        events.publishEvent(new RoomEvent(id,"message",sender.getId(),saved.getId(),saved.getBody(),saved.getAlias(),saved.getCreatedAt()));return message(saved,sender);
    }
    public void leave(Long id,Authentication auth){var member=members.require(auth);var room=locked(id);var membership=active(id,member);if(room.getOwner().getId().equals(member.getId()) && !room.isClosed())throw new ApiException(409,"방장은 방을 닫은 뒤 나갈 수 있어요.");membership.leave();changed(id,"left",membership.getId());}
    public void kick(Long id,Long participantId,Authentication auth){
        var member=members.require(auth);var room=locked(id);owner(room,member);open(room);var target=memberships.findById(participantId).orElseThrow(()->new ApiException(404,"참여자가 없어요."));
        if(!target.getRoom().getId().equals(id) || !target.isActive())throw new ApiException(404,"참여자가 없어요.");
        if(target.getMember().getId().equals(member.getId()))throw new ApiException(400,"방장 자신을 내보낼 수 없어요.");target.kick();changed(id,"removed",target.getId());
    }
    public void close(Long id,Authentication auth){var member=members.require(auth);var room=locked(id);owner(room,member);room.close();changed(id,"closed",null);}
    @Transactional(readOnly=true) public Long streamMembership(Long id,Authentication auth){var member=members.require(auth);var membership=active(id,member);open(membership.getRoom());return membership.getId();}
    private ChatRoom token(String token){if(token==null||!token.matches("[a-f0-9]{64}"))throw new ApiException(404,"초대 링크를 확인해주세요.");return rooms.findByInviteToken(token).orElseThrow(()->new ApiException(404,"초대 링크를 확인해주세요."));}
    private ChatRoom locked(Long id){return rooms.lock(id).orElseThrow(()->new ApiException(404,"채팅방이 없어요."));}
    private ChatMembership active(Long id,Member member){return memberships.findByRoomIdAndMemberId(id,member.getId()).filter(m->m.isActive()&&!m.isBlocked()).orElseThrow(()->new ApiException(403,"참여한 방에서만 대화를 볼 수 있어요."));}
    private void open(ChatRoom room){if(room.isClosed())throw new ApiException(409,"닫힌 채팅방이에요.");}
    private void owner(ChatRoom room,Member member){active(room.getId(),member);if(!room.getOwner().getId().equals(member.getId()))throw new ApiException(403,"방장만 관리할 수 있어요.");}
    private RoomView view(ChatRoom room,Member viewer,boolean full){
        var participants=full?memberships.findByRoomIdAndActiveTrueOrderByIdAsc(room.getId()).stream().map(m->new Participant(m.getId(),m.getAlias(),m.getMember().getId().equals(room.getOwner().getId()),m.getMember().getId().equals(viewer.getId()))).toList():List.<Participant>of();
        boolean own=room.getOwner().getId().equals(viewer.getId());
        return new RoomView(room.getId(),room.getName(),room.isClosed(),own,(int)memberships.countByRoomIdAndActiveTrue(room.getId()),full&&own&&!room.isClosed()?"/chat?invite="+room.getInviteToken():null,participants);
    }
    private MessageView message(ChatMessage m,ChatMembership viewer){return new MessageView(m.getId(),m.getBody(),m.getAlias(),m.getCreatedAt(),m.getSender().getId().equals(viewer.getId()));}
    private void changed(Long room,String type,Long membership){events.publishEvent(new RoomEvent(room,type,membership,null,null,null,null));}
    private static String trim(String value,int min,int max){if(value==null||value.trim().length()<min||value.trim().length()>max)throw new ApiException(400,"입력한 이름과 내용을 확인해주세요.");return value.trim();}
}
