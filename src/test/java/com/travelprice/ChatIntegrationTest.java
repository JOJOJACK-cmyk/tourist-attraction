package com.travelprice;
import com.fasterxml.jackson.databind.*;
import com.travelprice.domain.Member;
import com.travelprice.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:chatapi;MODE=MySQL;DB_CLOSE_DELAY=-1") @AutoConfigureMockMvc @ActiveProfiles("test")
class ChatIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired MemberRepository members;@Autowired ChatRoomRepository rooms;@Autowired ChatMembershipRepository memberships;@Autowired ChatMessageRepository messages;@Autowired PasswordEncoder encoder;
    @BeforeEach void setup(){messages.deleteAll();memberships.deleteAll();rooms.deleteAll();members.deleteAll();for(var name:new String[]{"alice","bob","charlie"})members.save(new Member(name,encoder.encode("password123"),Member.Role.MEMBER));}
    JsonNode create(String name)throws Exception{return json.readTree(mvc.perform(post("/api/chat/rooms").with(user(name)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"주말 여행\",\"alias\":\"방장\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    String invite(JsonNode room){return room.get("invitePath").asText().split("invite=")[1];}
    @Test void roomsArePrivateAndWritesRequireLoginAndCsrf()throws Exception{
        mvc.perform(get("/chat")).andExpect(status().isOk());mvc.perform(get("/api/chat/rooms")).andExpect(status().isUnauthorized());mvc.perform(get("/ws/chat").param("room","1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/rooms").with(user("alice")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        var room=create("alice");long id=room.get("id").asLong();
        mvc.perform(get("/api/chat/rooms").with(user("bob"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/chat/rooms/"+id).with(user("bob"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/chat/rooms/"+id+"/messages").with(user("bob"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/chat/rooms/"+id+"/messages").with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"밖에서 보내는 글\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/chat/invites/not-a-token").with(user("bob"))).andExpect(status().isNotFound());
    }
    @Test void inviteJoinHistoryAndOwnerControlsPreserveMessages()throws Exception{
        var room=create("alice");long id=room.get("id").asLong();String token=invite(room);
        mvc.perform(get("/api/chat/invites/"+token).with(user("bob"))).andExpect(jsonPath("$.name").value("주말 여행"));
        var joined=mvc.perform(post("/api/chat/invites/"+token+"/join").with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"동행\"}"))
            .andExpect(jsonPath("$.owner").value(false)).andExpect(jsonPath("$.memberCount").value(2)).andExpect(jsonPath("$.invitePath").isEmpty()).andReturn();
        long participant=json.readTree(joined.getResponse().getContentAsString()).get("participants").get(1).get("id").asLong();
        mvc.perform(post("/api/chat/invites/"+token+"/join").with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"동행\"}")).andExpect(jsonPath("$.memberCount").value(2));
        mvc.perform(post("/api/chat/invites/"+token+"/join").with(user("charlie")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"동행\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/chat/rooms/"+id+"/messages").with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"내일 몇 시에 만날까요?\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.mine").value(true)).andExpect(jsonPath("$.alias").value("동행"));
        mvc.perform(get("/api/chat/rooms/"+id+"/messages").with(user("alice"))).andExpect(jsonPath("$.messages[0].mine").value(false)).andExpect(jsonPath("$.messages[0].body").value("내일 몇 시에 만날까요?"));
        mvc.perform(post("/api/chat/rooms/"+id+"/close").with(user("bob")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/chat/rooms/"+id+"/membership").with(user("alice")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/chat/rooms/"+id+"/participants/"+participant).with(user("alice")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/chat/rooms/"+id+"/messages").with(user("bob"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/chat/invites/"+token+"/join").with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"다시 참여\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/chat/rooms/"+id+"/close").with(user("alice")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/chat/rooms/"+id+"/messages").with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"닫힌 방에 글\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/chat/rooms/"+id+"/messages").with(user("alice"))).andExpect(jsonPath("$.messages.length()").value(1));
        mvc.perform(post("/api/chat/invites/"+token+"/join").with(user("charlie")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"새 동행\"}")).andExpect(status().isConflict());
        assertThat(messages.count()).isEqualTo(1);
    }
    @Test void leavingAndRejoiningWorksAndMessagePaginationIsStable()throws Exception{
        var room=create("alice");long id=room.get("id").asLong();String token=invite(room);
        for(int i=0;i<51;i++)mvc.perform(post("/api/chat/rooms/"+id+"/messages").with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"여행 대화 "+i+"\"}")).andExpect(status().isCreated());
        var latest=json.readTree(mvc.perform(get("/api/chat/rooms/"+id+"/messages").with(user("alice"))).andExpect(jsonPath("$.messages.length()").value(50)).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/chat/rooms/"+id+"/messages").param("before",latest.get("nextBefore").asText()).with(user("alice"))).andExpect(jsonPath("$.messages.length()").value(1)).andExpect(jsonPath("$.messages[0].body").value("여행 대화 0"));
        mvc.perform(post("/api/chat/rooms/"+id+"/messages").with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"   \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat/invites/"+token+"/join").with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"동행\"}"));
        mvc.perform(delete("/api/chat/rooms/"+id+"/membership").with(user("bob")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/chat/rooms").with(user("bob"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/chat/invites/"+token+"/join").with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"동행\"}")).andExpect(jsonPath("$.memberCount").value(2));
    }
}
