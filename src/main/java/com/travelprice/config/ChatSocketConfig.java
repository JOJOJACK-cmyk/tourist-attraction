package com.travelprice.config;
import com.travelprice.chat.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocket
public class ChatSocketConfig implements WebSocketConfigurer {
    private final ChatHub hub;private final ChatHandshake handshake;
    public ChatSocketConfig(ChatHub hub,ChatHandshake handshake){this.hub=hub;this.handshake=handshake;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(hub,"/ws/chat").addInterceptors(handshake);}
}
