package com.travelprice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelprice.domain.Member;
import com.travelprice.repository.MemberRepository;
import com.travelprice.service.SocialMemberService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class NicknameIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired MemberRepository members;@Autowired PasswordEncoder encoder;@Autowired SocialMemberService social;
    @BeforeEach void setup(){members.saveAndFlush(new Member("nickowner",encoder.encode("password123"),Member.Role.MEMBER));members.saveAndFlush(new Member("nickother",encoder.encode("password123"),Member.Role.MEMBER));}
    String nickname(String name)throws Exception{return mapper.writeValueAsString(Map.of("nickname",name));}
    void set(String name)throws Exception{mvc.perform(patch("/api/auth/profile").with(user("nickowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(nickname(name))).andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value(name.trim())).andExpect(jsonPath("$.loginId").value("nickowner"));}
    @Test void nicknameIsOptionalForLegacySignupAndCanBeSetAtRegistration()throws Exception{
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"nicknew\",\"password\":\"password123\",\"nickname\":\"봄여행자\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/auth/me").with(user("nicknew"))).andExpect(jsonPath("$.nickname").value("봄여행자")).andExpect(jsonPath("$.displayName").value("봄여행자"));
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"nicklegacy\",\"password\":\"password123\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/auth/me").with(user("nicklegacy"))).andExpect(jsonPath("$.nickname").value("")).andExpect(jsonPath("$.displayName").value("익명"));
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"nickduplicate\",\"password\":\"password123\",\"nickname\":\"봄여행자\"}")).andExpect(status().isConflict());
        assertThat(members.findByLoginId("nickduplicate")).isEmpty();
    }
    @Test void ownProfileUpdateRequiresLoginCsrfAndDoesNotChangeLoginIdentityOrOtherMembers()throws Exception{
        mvc.perform(patch("/api/auth/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(nickname("봄여행자"))).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/auth/profile").with(user("nickowner")).contentType(MediaType.APPLICATION_JSON).content(nickname("봄여행자"))).andExpect(status().isForbidden());
        String hash=members.findByLoginId("nickowner").orElseThrow().getPasswordHash();
        var id=members.findByLoginId("nickother").orElseThrow().getId();
        mvc.perform(patch("/api/auth/profile").with(user("nickowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"봄여행자\",\"memberId\":"+id+",\"role\":\"ADMIN\",\"loginId\":\"nickother\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("봄여행자")).andExpect(jsonPath("$.admin").value(false));
        assertThat(members.findByLoginId("nickother").orElseThrow().getNickname()).isNull();
        var owner=members.findByLoginId("nickowner").orElseThrow();assertThat(owner.getPasswordHash()).isEqualTo(hash);assertThat(owner.getLoginId()).isEqualTo("nickowner");assertThat(owner.getRole()).isEqualTo(Member.Role.MEMBER);
        set("여름여행자");mvc.perform(get("/api/auth/me").with(user("nickowner"))).andExpect(jsonPath("$.nickname").value("여름여행자"));
    }
    @Test void duplicateNormalizationSelfRenamingAndInvalidNicknameValidation()throws Exception{
        set("Travel_12");set("travel_12");
        for(String value:new String[]{"TRAVEL_12","Ｔｒａｖｅｌ＿１２"})mvc.perform(patch("/api/auth/profile").with(user("nickother")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(nickname(value))).andExpect(status().isConflict());
        for(String value:new String[]{"a"," ","두 이름","<script>","😀😀","abcdefghijklmnopq","관리자","Admin","익명"})mvc.perform(patch("/api/auth/profile").with(user("nickother")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(nickname(value))).andExpect(status().isBadRequest());
        set("  가을여행자  ");
    }
    @Test void publicPostsAndCommentsUseCurrentNicknameWithoutLeakingLoginId()throws Exception{
        String body="{\"kind\":\"GENERAL\",\"category\":\"OTHER\",\"title\":\"여행 이야기\",\"body\":\"시장에 다녀온 경험을 나눠요\"}";
        var post=mvc.perform(post("/api/community/posts").with(user("nickowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(jsonPath("$.author").value("익명")).andReturn();long id=mapper.readTree(post.getResponse().getContentAsString()).get("id").asLong();
        set("겨울여행자");
        mvc.perform(post("/api/community/posts/"+id+"/comments").with(user("nickowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"맛있는 먹거리가 많았어요\"}" )).andExpect(jsonPath("$.author").value("겨울여행자"));
        mvc.perform(get("/api/community/posts/"+id)).andExpect(jsonPath("$.author").value("겨울여행자")).andExpect(jsonPath("$.mine").value(false)).andExpect(content().string(not(containsString("nickowner"))));
        set("새여행자");mvc.perform(get("/api/community/posts/"+id+"/comments")).andExpect(jsonPath("$[0].author").value("새여행자"));
        mvc.perform(put("/api/community/posts/"+id).with(user("nickother")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }
    @Test void naverAndGoogleMembersCanSetTheirOwnNickname()throws Exception{
        for(String provider:new String[]{"naver","google"}){
            var member=social.resolve(provider,"nickname-test-account");
            var principal=new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")),Map.of("localLoginId",member.getLoginId()),"localLoginId");
            var display=provider.equals("naver")?"네이버여행자":"구글여행자";
            mvc.perform(patch("/api/auth/profile").with(oauth2Login().oauth2User(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(nickname(display))).andExpect(jsonPath("$.nickname").value(display));
            mvc.perform(get("/api/auth/me").with(oauth2Login().oauth2User(principal))).andExpect(jsonPath("$.authProvider").value(provider)).andExpect(jsonPath("$.displayName").value(display));
        }
    }
    @Test void visitReviewsKeepAnonymousDisplayAfterNicknameIsSet()throws Exception{
        set("봄여행자");
        var input="{\"destinationId\":\"seomun\",\"visitedAt\":\"2026-10-01\",\"title\":\"시장 방문 경험\",\"foodCost\":\"SATISFIED\",\"lodgingCost\":\"NOT_USED\",\"service\":\"NEUTRAL\",\"crowding\":\"NEUTRAL\",\"goodPoints\":\"가격 대비 양이 만족스러웠어요\",\"firsthand\":true}";
        mvc.perform(post("/api/visit-reviews").with(user("nickowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isCreated()).andExpect(jsonPath("$.author").value("익명"));
    }
}
