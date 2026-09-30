package com.travelprice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelprice.domain.Member;
import com.travelprice.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="APP_UPLOAD_DIR=/tmp/tourist-community-test-photos")
@AutoConfigureMockMvc @ActiveProfiles("test")
class CommunityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired CommunityPostRepository posts;
    @Autowired CommunityCommentRepository comments;
    @Autowired CommunityPhotoRepository photos;
    @Autowired PasswordEncoder encoder;
    @BeforeEach void setup(){
        comments.deleteAll();posts.deleteAll();photos.deleteAll();members.deleteAll();
        members.save(new Member("alice",encoder.encode("password123"),Member.Role.MEMBER));
        members.save(new Member("bob",encoder.encode("password123"),Member.Role.MEMBER));
        members.save(new Member("admin",encoder.encode("password123"),Member.Role.ADMIN));
    }
    String input(String title,String kind,String category,String image){
        return "{\"destinationId\":\"sokcho\",\"kind\":\""+kind+"\",\"category\":\""+category+"\",\"title\":\""+title+"\",\"body\":\"주차 요금과 시설을 확인했어요\",\"imageKey\":"+(image==null?"null":"\""+image+"\"")+"}";
    }
    long create(String title,String kind,String category,String image)throws Exception{
        var result=mvc.perform(post("/api/community/posts").with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input(title,kind,category,image)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.author").value("익명")).andExpect(jsonPath("$.mine").value(true)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("alice","passwordHash","authorId");
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void registrationSessionLoginAndLogoutUseRealCsrfTokens()throws Exception{
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        var initial=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)initial.getRequest().getSession(false);
        var token=mapper.readTree(initial.getResponse().getContentAsString());
        String credentials="{\"loginId\":\"charlie\",\"password\":\"password123\"}";
        mvc.perform(post("/api/auth/signup").session(session).header(token.get("headerName").asText(),token.get("token").asText()).contentType(MediaType.APPLICATION_JSON).content(credentials)).andExpect(status().isCreated());
        var hash=members.findByLoginId("charlie").orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("password123");assertThat(encoder.matches("password123",hash)).isTrue();
        mvc.perform(post("/api/auth/signup").session(session).header(token.get("headerName").asText(),token.get("token").asText()).contentType(MediaType.APPLICATION_JSON).content(credentials)).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asText(),token.get("token").asText()).contentType(MediaType.APPLICATION_JSON).content(credentials)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.loginId").value("charlie"));
        mvc.perform(post("/api/community/posts").session(session).header(token.get("headerName").asText(),token.get("token").asText()).contentType(MediaType.APPLICATION_JSON).content(input("주차 경험","REVIEW","PARKING",null))).andExpect(status().isForbidden());
        var refreshed=mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        var fresh=mapper.readTree(refreshed.getResponse().getContentAsString());
        mvc.perform(post("/api/community/posts").session(session).header(fresh.get("headerName").asText(),fresh.get("token").asText()).contentType(MediaType.APPLICATION_JSON).content(input("주차 경험","REVIEW","PARKING",null))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/logout").session(session).header(fresh.get("headerName").asText(),fresh.get("token").asText())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(jsonPath("$.authenticated").value(false));
    }
    @Test void postsEnforceOwnershipFiltersPaginationAndAnonymousResponses()throws Exception{
        mvc.perform(post("/api/community/posts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input("주차 경험","REVIEW","PARKING",null))).andExpect(status().isUnauthorized());
        long id=create("무료 주차 경험","REVIEW","PARKING",null);
        mvc.perform(put("/api/community/posts/"+id).with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input("다른 사람 수정","REPORT","FOOD",null))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/community/posts/"+id).with(user("bob")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/community/posts/"+id).with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input("숙소 문의","QUESTION","LODGING",null))).andExpect(status().isOk());
        mvc.perform(get("/api/community/posts").param("kind","QUESTION").param("category","LODGING").param("destination","sokcho").param("q","숙소"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].mine").value(false));
        mvc.perform(get("/api/community/posts").param("destination","jeju")).andExpect(jsonPath("$.totalElements").value(0));
        for(int i=0;i<12;i++)create("추가 후기 "+i,"REPORT","TRANSPORT",null);
        mvc.perform(get("/api/community/posts")).andExpect(jsonPath("$.content.length()").value(12)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/community/posts").param("page","1")).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/community/posts").param("q","%" )).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/community/posts").param("page","-1")).andExpect(status().isBadRequest());
    }
    @Test void commentsFollowParentAndDeletionPermissions()throws Exception{
        long id=create("입장료 경험","REVIEW","ADMISSION",null);
        var result=mvc.perform(post("/api/community/posts/"+id+"/comments").with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"저도 같은 경험을 했어요\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.author").value("익명")).andReturn();
        long comment=mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(delete("/api/community/posts/"+id+"/comments/"+comment).with(user("alice")).with(csrf())).andExpect(status().isForbidden());
        long another=create("두 번째 후기","REVIEW","OTHER",null);
        mvc.perform(delete("/api/community/posts/"+another+"/comments/"+comment).with(user("bob")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/community/posts/"+id+"/comments")).andExpect(jsonPath("$[0].canDelete").value(false));
        mvc.perform(delete("/api/community/posts/"+id).with(user("alice")).with(csrf())).andExpect(status().isNoContent());
        assertThat(comments.count()).isZero();
        mvc.perform(get("/api/community/posts/"+id)).andExpect(status().isNotFound());
    }
    @Test void adminVisibilitySurvivesOwnerEditsAndBlocksPublicComments()throws Exception{
        long id=create("교통 경험","REPORT","TRANSPORT",null);
        mvc.perform(patch("/api/community/posts/"+id+"/visibility").with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/community/posts/"+id+"/visibility").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/community/posts/"+id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/community/posts")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/community/posts").param("includeHidden","true")).andExpect(status().isForbidden());
        mvc.perform(get("/api/community/posts").param("includeHidden","true").with(user("admin"))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put("/api/community/posts/"+id).with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input("수정한 후기","REPORT","TRANSPORT",null))).andExpect(jsonPath("$.hidden").value(true));
        mvc.perform(post("/api/community/posts/"+id+"/comments").with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"댓글\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/community/posts/"+id).with(user("admin")).with(csrf())).andExpect(status().isNoContent());
    }
    @Test void photosRequireValidImagesAndRespectVisibilityAndOwnership()throws Exception{
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var file=new MockMultipartFile("file","test.png","image/png",bytes.toByteArray());
        mvc.perform(multipart("/api/community/images").file(file).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/community/images").file(new MockMultipartFile("file","fake.png","image/png","fake".getBytes())).with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
        var uploaded=mvc.perform(multipart("/api/community/images").file(file).with(user("alice")).with(csrf())).andExpect(status().isCreated()).andReturn();
        String key=mapper.readTree(uploaded.getResponse().getContentAsString()).get("imageKey").asText();
        mvc.perform(get("/api/community/images/"+key)).andExpect(status().isNotFound());
        mvc.perform(post("/api/community/posts").with(user("bob")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input("사진 사용","REVIEW","OTHER",key))).andExpect(status().isForbidden());
        long id=create("사진 후기","REVIEW","OTHER",key);
        mvc.perform(get("/api/community/images/"+key)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(patch("/api/community/posts/"+id+"/visibility").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/community/images/"+key)).andExpect(status().isNotFound());
        mvc.perform(get("/api/community/images/"+key).with(user("alice"))).andExpect(status().isOk());
        mvc.perform(patch("/api/community/posts/"+id+"/visibility").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":false}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/community/images/"+key)).andExpect(status().isOk());
        mvc.perform(put("/api/community/posts/"+id).with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input("사진 제거","REVIEW","OTHER",null))).andExpect(status().isOk());
        assertThat(photos.findById(key)).isEmpty();
        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("/tmp/tourist-community-test-photos",key+".png"))).isFalse();
        mvc.perform(get("/api/community/images/"+key).with(user("alice"))).andExpect(status().isNotFound());
    }
    @Test void communityPageIsServed()throws Exception{
        mvc.perform(get("/community")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("경험 공유")));
        mvc.perform(get("/js/community.js")).andExpect(status().isOk());
    }
}
