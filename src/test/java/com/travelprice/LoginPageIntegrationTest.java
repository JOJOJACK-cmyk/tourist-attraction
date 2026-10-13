package com.travelprice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelprice.api.LoginReturnPath;
import com.travelprice.domain.Member;
import com.travelprice.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class LoginPageIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired MemberRepository members;@Autowired PasswordEncoder encoder;
    @Test void dedicatedPageAndNavigationArePublicAndPreserveTheOriginalPage()throws Exception{
        mvc.perform(get("/login").param("next","/visit-reviews?destination=seomun&write=1"))
            .andExpect(status().isOk()).andExpect(view().name("login"))
            .andExpect(model().attribute("loginNext","/visit-reviews?destination=seomun&write=1"))
            .andExpect(content().string(containsString("id=\"auth-form\"")))
            .andExpect(content().string(containsString("data-next=\"/visit-reviews?destination=seomun&amp;write=1\"")));
        mvc.perform(get("/js/login.js")).andExpect(status().isOk());
        for(String page:new String[]{"/","/community","/chat","/visit-reviews","/destinations/seomun","/support"})mvc.perform(get(page)).andExpect(status().isOk()).andExpect(content().string(containsString("로그인 · 회원가입</a>")));
        mvc.perform(get("/visit-reviews").param("destination","seomun")).andExpect(content().string(containsString("/login?next=%2Fvisit-reviews%3Fdestination%3Dseomun")));
    }
    @Test void returnDestinationRejectsExternalAndUnexpectedPaths()throws Exception{
        for(String path:new String[]{"https://evil.example","//evil.example","/\\evil.example","/%2f%2fevil.example","/api/auth/logout","/login?next=//evil.example","/chat\r\nX-Test: yes"}){
            assertThat(LoginReturnPath.safe(path)).isEqualTo("/");
            mvc.perform(get("/login").param("next",path)).andExpect(status().isOk()).andExpect(model().attribute("loginNext","/"));
        }
        assertThat(LoginReturnPath.safe("/chat?room=12#messages")).isEqualTo("/chat?room=12#messages");
        assertThat(LoginReturnPath.safe("/destinations/seomun")).isEqualTo("/destinations/seomun");
    }
    @Test void existingPasswordLoginUsesSessionAndRotatedCsrfWithAccountNavigation()throws Exception{
        members.saveAndFlush(new Member("loginpageuser",encoder.encode("password123"),Member.Role.MEMBER));
        var first=mvc.perform(get("/api/auth/csrf")).andReturn();var session=(MockHttpSession)first.getRequest().getSession(false);var token=mapper.readTree(first.getResponse().getContentAsString());
        var credentials="{\"loginId\":\"loginpageuser\",\"password\":\"password123\"}";
        mvc.perform(post("/api/auth/login").session(session).contentType(MediaType.APPLICATION_JSON).content(credentials)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asText(),token.get("token").asText()).contentType(MediaType.APPLICATION_JSON).content(credentials)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(get("/").session(session)).andExpect(content().string(containsString("내 계정</a>"))).andExpect(content().string(not(containsString("로그인 · 회원가입</a>"))));
        mvc.perform(post("/api/auth/logout").session(session).header(token.get("headerName").asText(),token.get("token").asText())).andExpect(status().isForbidden());
        var refreshed=mvc.perform(get("/api/auth/csrf").session(session)).andReturn();var fresh=mapper.readTree(refreshed.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/logout").session(session).header(fresh.get("headerName").asText(),fresh.get("token").asText())).andExpect(status().isOk());
    }
    @Test void unavailableSocialLoginReturnsToTheDedicatedPage()throws Exception{
        mvc.perform(get("/auth/social/kakao").param("next","/visit-reviews?destination=seomun"))
            .andExpect(redirectedUrl("/login?next=%2Fvisit-reviews%3Fdestination%3Dseomun&loginError=unavailable"));
        mvc.perform(get("/auth/social/kakao").param("next","//evil.example")).andExpect(redirectedUrl("/login?next=%2F&loginError=unavailable"));
    }
}
