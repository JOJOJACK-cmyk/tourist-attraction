package com.travelprice;
import com.fasterxml.jackson.databind.*;
import com.travelprice.domain.Member;
import com.travelprice.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:chatreal;MODE=MySQL;DB_CLOSE_DELAY=-1") @ActiveProfiles("test")
class ChatRealtimeIntegrationTest {
    @LocalServerPort int port;@Autowired ObjectMapper json;@Autowired MemberRepository members;@Autowired ChatRoomRepository rooms;@Autowired ChatMembershipRepository memberships;@Autowired ChatMessageRepository messages;@Autowired PasswordEncoder encoder;
    @BeforeEach void setup(){messages.deleteAll();memberships.deleteAll();rooms.deleteAll();members.deleteAll();for(var name:new String[]{"alice","bob","charlie"})members.save(new Member(name,encoder.encode("password123"),Member.Role.MEMBER));}
    final class Client {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();JsonNode csrf;
        Client(String user)throws Exception{csrf=get("/api/auth/csrf");request("POST","/api/auth/login",Map.of("loginId",user,"password","password123"),200);csrf=get("/api/auth/csrf");}
        JsonNode get(String path)throws Exception{return request("GET",path,null,200);}
        JsonNode request(String method,String path,Object body,int expected)throws Exception{
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(5));
            if(!"GET".equals(method))builder.header(csrf.get("headerName").asText(),csrf.get("token").asText());
            if(body!=null)builder.header("Content-Type","application/json");
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);return response.body().isEmpty()?null:json.readTree(response.body());
        }
        CompletableFuture<WebSocket> socket(long room,Listener listener,String origin){
            var cookie=cookies.getCookieStore().getCookies().stream().map(c->c.getName()+"="+c.getValue()).reduce((a,b)->a+"; "+b).orElse("");
            return client.newWebSocketBuilder().header("Cookie",cookie).header("Origin",origin).buildAsync(URI.create("ws://localhost:"+port+"/ws/chat?room="+room),listener);
        }
    }
    static final class Listener implements WebSocket.Listener {
        final CountDownLatch closed=new CountDownLatch(1);final BlockingQueue<String> queue=new LinkedBlockingQueue<>();final StringBuilder buffer=new StringBuilder();
        @Override public void onOpen(WebSocket socket){socket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last){buffer.append(text);if(last){queue.add(buffer.toString());buffer.setLength(0);}socket.request(1);return null;}
        @Override public CompletionStage<?> onClose(WebSocket socket,int status,String reason){closed.countDown();return null;}
        JsonNode next(ObjectMapper json,String type)throws Exception{var value=queue.poll(5,TimeUnit.SECONDS);assertThat(value).as("Expected websocket event "+type).isNotNull();var event=json.readTree(value);assertThat(event.get("type").asText()).isEqualTo(type);return event;}
    }
    @Test void realSocketsDeliverCommittedMessagesAndDisconnectRemovedMembers()throws Exception{
        var alice=new Client("alice");var bob=new Client("bob");var charlie=new Client("charlie");
        var room=alice.request("POST","/api/chat/rooms",Map.of("name","우리 여행","alias","여행장"),201);long id=room.get("id").asLong();var invite=room.get("invitePath").asText().split("invite=")[1];
        var joined=bob.request("POST","/api/chat/invites/"+invite+"/join",Map.of("alias","친구"),200);long participant=joined.get("participants").get(1).get("id").asLong();
        var other=charlie.request("POST","/api/chat/rooms",Map.of("name","다른 여행","alias","다른장"),201);
        var al=new Listener();var bl=new Listener();var cl=new Listener();var origin="http://localhost:"+port;
        var aws=alice.socket(id,al,origin).get(5,TimeUnit.SECONDS);var bws=bob.socket(id,bl,origin).get(5,TimeUnit.SECONDS);var cws=charlie.socket(other.get("id").asLong(),cl,origin).get(5,TimeUnit.SECONDS);
        try{
            al.next(json,"ready");bl.next(json,"ready");cl.next(json,"ready");
            alice.request("POST","/api/chat/rooms/"+id+"/messages",Map.of("body","내일 함께 출발해요"),201);
            assertThat(al.next(json,"message").get("message").get("mine").asBoolean()).isTrue();
            var received=bl.next(json,"message").get("message");assertThat(received.get("body").asText()).isEqualTo("내일 함께 출발해요");assertThat(received.get("mine").asBoolean()).isFalse();
            assertThat(cl.queue.poll(250,TimeUnit.MILLISECONDS)).isNull();
            assertThat(bob.get("/api/chat/rooms/"+id+"/messages").get("messages").size()).isEqualTo(1);
            alice.request("DELETE","/api/chat/rooms/"+id+"/participants/"+participant,null,204);bl.next(json,"removed");al.next(json,"members");
            bob.request("POST","/api/chat/rooms/"+id+"/messages",Map.of("body","다시 보내기"),403);
            assertThatThrownBy(()->bob.socket(id,new Listener(),origin).get(5,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertThatThrownBy(()->alice.socket(id,new Listener(),"https://foreign.example").get(5,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            alice.request("POST","/api/chat/rooms/"+id+"/close",null,204);al.next(json,"closed");
            alice.request("POST","/api/chat/rooms/"+id+"/messages",Map.of("body","닫힌 방"),409);
        }finally{aws.abort();bws.abort();cws.abort();}
    }
    @Test void logoutRevokesAnAlreadyOpenSocket()throws Exception{
        var alice=new Client("alice");var room=alice.request("POST","/api/chat/rooms",Map.of("name","로그아웃 방","alias","여행장"),201);var listener=new Listener();var ws=alice.socket(room.get("id").asLong(),listener,"http://localhost:"+port).get(5,TimeUnit.SECONDS);
        try{listener.next(json,"ready");alice.request("POST","/api/auth/logout",null,200);if(!ws.isOutputClosed()){try{ws.sendText("ping",true).get(5,TimeUnit.SECONDS);}catch(ExecutionException alreadyClosed){assertThat(alreadyClosed.getCause()).isInstanceOf(java.io.IOException.class);}}assertThat(listener.closed.await(5,TimeUnit.SECONDS)).isTrue();assertThat(listener.queue.poll()).isNull();assertThat(messages.count()).isZero();}finally{ws.abort();}
    }
}
