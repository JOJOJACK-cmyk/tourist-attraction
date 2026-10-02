package com.travelprice;

import com.travelprice.domain.Member;
import com.travelprice.repository.*;
import com.travelprice.service.SocialMemberService;
import com.travelprice.api.SocialLoginController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"KAKAO_CLIENT_ID=test-kakao","KAKAO_CLIENT_SECRET=test-kakao-secret","GOOGLE_CLIENT_ID=test-google","GOOGLE_CLIENT_SECRET=test-google-secret"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class SocialLoginIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SocialMemberService social;
    @Autowired MemberRepository members;
    @Autowired CommunityPostRepository posts;
    @Autowired CommunityCommentRepository comments;
    @Autowired CommunityPhotoRepository photos;
    @Autowired PasswordEncoder encoder;
    @BeforeEach void cleanup(){comments.deleteAll();posts.deleteAll();photos.deleteAll();members.deleteAll();}
    @Test void identitiesAreStableSeparateAndAlwaysNewRegularMembers(){
        var first=social.resolve("google","12345");var again=social.resolve("google","12345");
        var other=social.resolve("kakao","12345");var caseSensitive=social.resolve("google","abc");var capital=social.resolve("google","ABC");
        assertThat(first.getId()).isEqualTo(again.getId()).isNotEqualTo(other.getId());
        assertThat(caseSensitive.getId()).isNotEqualTo(capital.getId());
        assertThat(first.getRole()).isEqualTo(Member.Role.MEMBER);assertThat(members.count()).isEqualTo(4);
        assertThatThrownBy(()->social.resolve("naver","12345")).isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(()->social.resolve("google","")).isInstanceOf(OAuth2AuthenticationException.class);
    }
    @Test void socialSessionCanWriteAnonymouslyButCannotUseAdminOrPasswordLogin()throws Exception{
        var member=social.resolve("kakao","provider-account");
        var principal=new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")),Map.of("localLoginId",member.getLoginId()),"localLoginId");
        mvc.perform(get("/api/auth/me").with(oauth2Login().oauth2User(principal)))
            .andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.authProvider").value("kakao")).andExpect(jsonPath("$.loginId").value("카카오 계정"));
        mvc.perform(post("/api/community/posts").with(oauth2Login().oauth2User(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"kind\":\"GENERAL\",\"category\":\"OTHER\",\"title\":\"소셜 로그인 글\",\"body\":\"소셜 계정으로 작성한 이야기\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.author").value("익명")).andExpect(jsonPath("$.mine").value(true));
        mvc.perform(get("/admin/reviews").with(oauth2Login().oauth2User(principal))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"loginId\":\""+member.getLoginId()+"\",\"password\":\"password123\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void configuredProvidersUseAuthorizationCodeRedirectsAndSafeBoardReturn()throws Exception{
        mvc.perform(get("/api/auth/social/providers")).andExpect(jsonPath("$.kakao").value(true)).andExpect(jsonPath("$.google").value(true))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        var start=mvc.perform(get("/auth/social/kakao").param("board","question")).andExpect(redirectedUrl("/oauth2/authorization/kakao")).andReturn();
        assertThat(start.getRequest().getSession().getAttribute(SocialLoginController.RETURN_BOARD)).isEqualTo("question");
        mvc.perform(get("/oauth2/authorization/kakao")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location",org.hamcrest.Matchers.startsWith("https://kauth.kakao.com/oauth/authorize?")))
            .andExpect(header().string("Location",org.hamcrest.Matchers.containsString("state=")));
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location",org.hamcrest.Matchers.startsWith("https://accounts.google.com/o/oauth2/v2/auth?")));
        mvc.perform(get("/auth/social/unknown").param("board","https://evil.example"))
            .andExpect(redirectedUrl("/community?board=free&loginError=unavailable#board"));
        var request=new MockHttpServletRequest();request.getSession().setAttribute(SocialLoginController.RETURN_BOARD,"question");
        assertThat(SocialLoginController.returnPath(request,false)).isEqualTo("/community?board=question#board");
        assertThat(request.getSession().getAttribute(SocialLoginController.RETURN_BOARD)).isNull();
    }
    @Test void unsolicitedCallbackCannotCreateAMember()throws Exception{
        mvc.perform(get("/login/oauth2/code/kakao").param("code","untrusted").param("state","untrusted"))
            .andExpect(redirectedUrl("/community?board=free&loginError=social#board"));
        assertThat(members.count()).isZero();
    }
}
