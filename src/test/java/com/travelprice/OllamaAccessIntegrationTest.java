package com.travelprice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelprice.domain.Member;
import com.travelprice.repository.MemberRepository;
import com.travelprice.service.OllamaReviewClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OllamaAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;
    @MockitoBean OllamaReviewClient ollama;
    static final String INPUT="{\"destinationId\":\"sokcho\",\"body\":\"주차가 편리했어요\"}";
    @BeforeEach void setup(){
        if(!members.existsByLoginId("ollama_admin"))members.save(new Member("ollama_admin",encoder.encode("password123"),Member.Role.ADMIN));
        if(!members.existsByLoginId("ollama_member"))members.save(new Member("ollama_member",encoder.encode("password123"),Member.Role.MEMBER));
    }
    @Test void anonymousCannotAccessPageScriptStatusOrExtraction()throws Exception{
        for(String path:new String[]{"/admin/reviews","/js/local-review.js","/api/reviews/status"})mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reviews/extract").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isUnauthorized());
        verifyNoInteractions(ollama);
    }
    @Test void memberCannotAccessPageScriptStatusOrExtraction()throws Exception{
        for(String path:new String[]{"/admin/reviews","/js/local-review.js","/api/reviews/status"})mvc.perform(get(path).with(user("ollama_member").roles("MEMBER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/reviews/extract").with(user("ollama_member").roles("MEMBER")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isForbidden());
        verifyNoInteractions(ollama);
    }
    @Test void onlyAdminSeesNavigationAndDedicatedAnalysisPage()throws Exception{
        mvc.perform(get("/")).andExpect(content().string(not(containsString("/admin/reviews")))).andExpect(content().string(not(containsString("local-review-form"))));
        mvc.perform(get("/").with(user("ollama_member").roles("MEMBER"))).andExpect(content().string(not(containsString("/admin/reviews"))));
        mvc.perform(get("/").with(user("ollama_admin").roles("ADMIN"))).andExpect(content().string(containsString("/admin/reviews"))).andExpect(content().string(not(containsString("local-review-form"))));
        mvc.perform(get("/community").with(user("ollama_admin").roles("ADMIN"))).andExpect(content().string(containsString("/admin/reviews")));
        mvc.perform(get("/admin/reviews").with(user("ollama_admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("local-review-form")));
    }
    @Test void adminCanUseOllamaButExtractionRequiresCsrf()throws Exception{
        when(ollama.status()).thenReturn(Map.of("available",true));
        mvc.perform(get("/api/reviews/status").with(user("ollama_admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        verify(ollama).status();clearInvocations(ollama);
        mvc.perform(post("/api/reviews/extract").with(user("ollama_admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isForbidden());
        verifyNoInteractions(ollama);
        when(ollama.extract(eq("속초관광수산시장"),anyString(),isNull(),eq("주차가 편리했어요"))).thenReturn(mapper.readTree("{\"experiences\":[],\"limitations\":[]}"));
        mvc.perform(post("/api/reviews/extract").with(user("ollama_admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isOk()).andExpect(jsonPath("$.destination").value("속초관광수산시장"));
        verify(ollama).extract(eq("속초관광수산시장"),anyString(),isNull(),eq("주차가 편리했어요"));
    }
}
