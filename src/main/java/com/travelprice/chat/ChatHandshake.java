package com.travelprice.chat;
import com.travelprice.service.ChatService;
import com.travelprice.api.ApiException;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;
@Component
public class ChatHandshake implements HandshakeInterceptor {
    private final ChatService chat;public ChatHandshake(ChatService chat){this.chat=chat;}
    @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attrs){
        try{
            if(!(request instanceof ServletServerHttpRequest servlet) || !(request.getPrincipal() instanceof Authentication auth))throw new ApiException(401,"로그인이 필요해요.");
            var session=servlet.getServletRequest().getSession(false);if(session==null)throw new ApiException(401,"로그인이 필요해요.");
            Long room=Long.valueOf(UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("room"));
            var membership=chat.streamMembership(room,auth);attrs.put("room",room);attrs.put("membership",membership);attrs.put("httpSession",session);attrs.put("loginId",auth.getName());return true;
        }catch(ApiException e){response.setStatusCode(HttpStatusCode.valueOf(e.getStatus()));return false;}
        catch(RuntimeException e){response.setStatusCode(HttpStatus.BAD_REQUEST);return false;}
    }
    @Override public void afterHandshake(ServerHttpRequest req,ServerHttpResponse res,WebSocketHandler handler,Exception error){}
}
