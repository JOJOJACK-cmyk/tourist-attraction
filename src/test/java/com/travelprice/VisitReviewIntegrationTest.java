package com.travelprice;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travelprice.domain.*;
import com.travelprice.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="APP_UPLOAD_DIR=/tmp/tourist-community-test-photos")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class VisitReviewIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired MemberRepository members;
    @Autowired VisitReviewRepository reviews;@Autowired CommunityPostRepository posts;
    @Autowired CommunityPhotoRepository photos;@Autowired DestinationRepository destinations;
    @BeforeEach void setup(){
        members.saveAndFlush(new Member("visitowner","unused",Member.Role.MEMBER));
        members.saveAndFlush(new Member("visitother","unused",Member.Role.MEMBER));
        members.saveAndFlush(new Member("visitadmin","unused",Member.Role.ADMIN));
    }
    ObjectNode input(){return mapper.createObjectNode().put("destinationId","seomun").put("visitedAt","2025-03-27").put("title","평일 시장 방문 경험").put("item","칼국수 두 그릇").put("spentWon",12000).put("waitingMinutes",0).put("foodCost","SATISFIED").put("lodgingCost","NOT_USED").put("service","NEUTRAL").put("crowding","NEUTRAL").put("goodPoints","가격 대비 양이 만족스러웠어요").put("badPoints","").put("firsthand",true);}
    JsonNode create(ObjectNode input)throws Exception{
        var result=mvc.perform(post("/api/visit-reviews").with(user("visitowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input.toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.author").value("익명")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("visitowner","passwordHash","authorId");
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    JsonNode approve(JsonNode r)throws Exception{return decision(r,"APPROVED","");}
    JsonNode decision(JsonNode r,String status,String note)throws Exception{
        var result=mvc.perform(patch("/api/admin/visit-reviews/"+r.get("id").asLong()).with(user("visitadmin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.createObjectNode().put("status",status).put("note",note).put("version",r.get("version").asLong()).toString())).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    @Test void pendingReviewIsPrivateAndApprovalFeedsOnlyItsDestinationAndWritingYear()throws Exception{
        var r=create(input());long id=r.get("id").asLong();
        mvc.perform(get("/api/visit-reviews")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/visit-reviews/"+id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/visit-reviews/"+id).with(user("visitother"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/visit-reviews/mine").with(user("visitowner"))).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].status").value("PENDING"));
        mvc.perform(get("/api/visit-reviews/mine").with(user("visitother"))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/visit-reviews/summary").param("destination","seomun")).andExpect(jsonPath("$.total").value(0));
        approve(r);
        int year=LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        mvc.perform(get("/api/visit-reviews").param("destination","seomun").param("year",String.valueOf(year))).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].visitedAt").value("2025-03-27"));
        mvc.perform(get("/api/visit-reviews/summary").param("destination","seomun").param("year",String.valueOf(year))).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.praiseCount").value(1)).andExpect(jsonPath("$.complaintCount").value(0)).andExpect(jsonPath("$.aspects[0].good").value(1)).andExpect(jsonPath("$.aspects[1].total").value(0)).andExpect(jsonPath("$.aspects[3].bad").value(0));
        mvc.perform(get("/api/visit-reviews/summary").param("destination","sokcho")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/visit-reviews/summary").param("destination","seomun").param("year",String.valueOf(year-1))).andExpect(jsonPath("$.total").value(0));
    }
    @Test void authorEditsResetApprovalAndStaleModerationCannotPublishUnreviewedChanges()throws Exception{
        var approved=approve(create(input()));long id=approved.get("id").asLong();
        var edit=input().put("version",approved.get("version").asLong()).put("badPoints","인기 시간이라 기다림이 아쉬웠어요").put("crowding","UNSATISFIED");
        var changed=mvc.perform(put("/api/visit-reviews/"+id).with(user("visitowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.reviewNote").isEmpty()).andReturn();
        mvc.perform(get("/api/visit-reviews/summary").param("destination","seomun")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(patch("/api/admin/visit-reviews/"+id).with(user("visitadmin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.createObjectNode().put("status","APPROVED").put("version",approved.get("version").asLong()).toString())).andExpect(status().isConflict());
        var fresh=mapper.readTree(changed.getResponse().getContentAsString());approve(fresh);
        mvc.perform(get("/api/visit-reviews/summary").param("destination","seomun")).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.praiseCount").value(1)).andExpect(jsonPath("$.complaintCount").value(1));
    }
    @Test void rejectionNoteIsPrivateAndAuthorCanCorrectAndResubmit()throws Exception{
        var r=create(input());var rejected=decision(r,"REJECTED","방문 조건을 조금 더 적어주세요");long id=r.get("id").asLong();
        mvc.perform(get("/api/visit-reviews/"+id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/visit-reviews/mine").with(user("visitowner"))).andExpect(jsonPath("$.content[0].reviewNote").value("방문 조건을 조금 더 적어주세요"));
        mvc.perform(put("/api/visit-reviews/"+id).with(user("visitowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input().put("version",rejected.get("version").asLong()).toString())).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.reviewNote").isEmpty());
        var fresh=mvc.perform(get("/api/visit-reviews/"+id).with(user("visitowner"))).andReturn();
        var approved=decision(mapper.readTree(fresh.getResponse().getContentAsString()),"APPROVED","관리자 내부 검토 메모");
        mvc.perform(get("/api/visit-reviews/"+id)).andExpect(jsonPath("$.reviewNote").isEmpty());
        mvc.perform(get("/api/visit-reviews/"+id).with(user("visitowner"))).andExpect(jsonPath("$.reviewNote").value("관리자 내부 검토 메모"));
        mvc.perform(delete("/api/visit-reviews/"+id).param("version",approved.get("version").asText()).with(user("visitowner")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/visit-reviews/summary").param("destination","seomun")).andExpect(jsonPath("$.total").value(0));
    }
    @Test void authorizationAndCsrfProtectOwnerAndModeratorOperations()throws Exception{
        mvc.perform(post("/api/visit-reviews").contentType(MediaType.APPLICATION_JSON).content(input().toString())).andExpect(status().isForbidden());
        mvc.perform(post("/api/visit-reviews").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input().toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/visit-reviews/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/visit-reviews").with(user("visitowner"))).andExpect(status().isForbidden());
        var r=approve(create(input()));long id=r.get("id").asLong();
        var edit=input().put("version",r.get("version").asLong());
        mvc.perform(put("/api/visit-reviews/"+id).with(user("visitother")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/visit-reviews/"+id).param("version",r.get("version").asText()).with(user("visitother")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/visit-reviews/"+id).with(user("visitowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(delete("/api/visit-reviews/"+id).param("version","999").with(user("visitowner")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/visit-reviews/"+id).with(user("visitadmin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
    }
    @Test void validationRejectsInventedDatesInvalidAmountsAndMissingExperienceConfirmation()throws Exception{
        for(ObjectNode bad:new ObjectNode[]{input().put("visitedAt","2099-01-01"),input().put("visitedAt","1999-12-31"),input().put("spentWon",-1),input().put("waitingMinutes",1441),input().put("firsthand",false),input().put("destinationId","unknown"),input().put("goodPoints","   ").put("badPoints",""),input().put("title","  ")})mvc.perform(post("/api/visit-reviews").with(user("visitowner")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bad.toString())).andExpect(status().isBadRequest());
        var r=create(input());
        mvc.perform(patch("/api/admin/visit-reviews/"+r.get("id").asLong()).with(user("visitadmin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.createObjectNode().put("status","REJECTED").put("note","").put("version",r.get("version").asLong()).toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/visit-reviews").param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/visit-reviews/summary").param("destination","unknown")).andExpect(status().isBadRequest());
    }
    @Test void optionalPhotosFollowApprovalAndOtherOwnersCannotAttachThem()throws Exception{
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var uploaded=mvc.perform(multipart("/api/community/images").file(new MockMultipartFile("file","photo.png","image/png",bytes.toByteArray())).with(user("visitowner")).with(csrf())).andExpect(status().isCreated()).andReturn();
        var key=mapper.readTree(uploaded.getResponse().getContentAsString()).get("imageKey").asText();
        mvc.perform(post("/api/visit-reviews").with(user("visitother")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input().put("imageKey",key).toString())).andExpect(status().isForbidden());
        var r=create(input().put("imageKey",key));
        mvc.perform(get("/api/community/images/"+key)).andExpect(status().isNotFound());
        mvc.perform(get("/api/community/images/"+key).with(user("visitowner"))).andExpect(status().isOk());
        var approved=approve(r);mvc.perform(get("/api/community/images/"+key)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        var rejected=decision(approved,"REJECTED","내용을 보완해주세요");mvc.perform(get("/api/community/images/"+key)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/visit-reviews/"+r.get("id").asLong()).param("version",rejected.get("version").asText()).with(user("visitowner")).with(csrf())).andExpect(status().isNoContent());
        assertThat(photos.findById(key)).isEmpty();
    }
    @Test void paginationAndSharedCommunityPhotosRemainConsistent()throws Exception{
        var photo=new CommunityPhoto("00000000-0000-0000-0000-000000000001",members.findByLoginId("visitowner").orElseThrow(),"png");photos.saveAndFlush(photo);
        var owner=members.findByLoginId("visitowner").orElseThrow();posts.saveAndFlush(new CommunityPost(owner,destinations.findBySlug("seomun").orElseThrow(),CommunityPost.Kind.GENERAL,CommunityPost.Category.FOOD,"공유 사진 게시글","방문 사진을 공유하고 있어요",null,photo.getId()));
        var r=create(input().put("imageKey",photo.getId()));
        mvc.perform(delete("/api/visit-reviews/"+r.get("id").asLong()).param("version",r.get("version").asText()).with(user("visitowner")).with(csrf())).andExpect(status().isNoContent());assertThat(photos.findById(photo.getId())).isPresent();
        for(int i=0;i<13;i++)create(input().put("title","방문 후기 "+i));
        mvc.perform(get("/api/visit-reviews/mine").with(user("visitowner"))).andExpect(jsonPath("$.content.length()").value(12)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/visit-reviews/mine").param("page","1").with(user("visitowner"))).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/admin/visit-reviews").with(user("visitadmin").roles("ADMIN"))).andExpect(jsonPath("$.totalElements").value(13));
    }
    @Test void publicAndAdminPagesRenderWithRoleAppropriateAccess()throws Exception{
        mvc.perform(get("/visit-reviews")).andExpect(status().isOk()).andExpect(content().string(containsString("직접 방문 후기 작성")));
        mvc.perform(get("/js/visit-reviews.js")).andExpect(status().isOk());
        mvc.perform(get("/admin/visit-reviews")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/visit-reviews").with(user("visitowner"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/visit-reviews").with(user("visitadmin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("data-moderation=\"true\"")));
    }
}
