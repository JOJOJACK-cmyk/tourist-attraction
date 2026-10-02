package com.travelprice.chat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelprice.api.ChatModels.*;
import com.travelprice.service.ChatService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class ChatHub extends TextWebSocketHandler {
    private final Map<String,WebSocketSession> sockets=new ConcurrentHashMap<>();private final ChatService chat;private final ObjectMapper json;
    public ChatHub(ChatService chat,ObjectMapper json){this.chat=chat;this.json=json;}
    @Override public void afterConnectionEstablished(WebSocketSession raw)throws Exception{
        raw.setTextMessageSizeLimit(256);var socket=new ConcurrentWebSocketSessionDecorator(raw,5000,64*1024);sockets.put(raw.getId(),socket);
        if(!valid(socket)){remove(socket);return;}send(socket,Map.of("type","ready"));
    }
    @Override protected void handleTextMessage(WebSocketSession raw,TextMessage message)throws Exception{
        var socket=sockets.get(raw.getId());if(socket==null)return;
        if(!valid(socket)){remove(socket);return;}
        if("ping".equals(message.getPayload()))send(socket,Map.of("type","pong"));else send(socket,Map.of("type","error","message","채팅 입력 방식을 확인해주세요."));
    }
    @Override public void afterConnectionClosed(WebSocketSession socket,CloseStatus status){sockets.remove(socket.getId());}
    @Override public void handleTransportError(WebSocketSession socket,Throwable error){remove(socket);}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void event(RoomEvent event){
        for(var socket:sockets.values()){
            if(!Objects.equals(socket.getAttributes().get("room"),event.roomId()))continue;
            try{
                Long membership=(Long)socket.getAttributes().get("membership");
                if("closed".equals(event.type())){send(socket,Map.of("type","closed"));remove(socket);continue;}
                if(event.messageId()==null && Objects.equals(membership,event.affectedMembership())){send(socket,Map.of("type",event.type()));remove(socket);continue;}
                if(!valid(socket)){remove(socket);continue;}
                if("message".equals(event.type()))send(socket,Map.of("type","message","message",new MessageView(event.messageId(),event.body(),event.alias(),event.createdAt(),Objects.equals(membership,event.affectedMembership()))));
                else send(socket,Map.of("type","members"));
            }catch(Exception ignored){remove(socket);}
        }
    }
    private boolean valid(WebSocketSession socket){
        try{
            var session=(HttpSession)socket.getAttributes().get("httpSession");var context=(SecurityContext)session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            Authentication auth=context==null?null:context.getAuthentication();
            return auth!=null && auth.isAuthenticated() && Objects.equals(auth.getName(),socket.getAttributes().get("loginId")) && Objects.equals(chat.streamMembership((Long)socket.getAttributes().get("room"),auth),socket.getAttributes().get("membership"));
        }catch(RuntimeException error){return false;}
    }
    private void send(WebSocketSession socket,Object value)throws Exception{if(socket.isOpen())socket.sendMessage(new TextMessage(json.writeValueAsString(value)));}
    private void remove(WebSocketSession socket){sockets.remove(socket.getId());try{socket.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}}
}
