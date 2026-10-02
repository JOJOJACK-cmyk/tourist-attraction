package com.travelprice;
import com.fasterxml.jackson.databind.*;
import com.travelprice.domain.SupportOrder;
import com.travelprice.repository.SupportOrderRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:supporttest;MODE=MySQL;DB_CLOSE_DELAY=-1") @AutoConfigureMockMvc @ActiveProfiles("test")
class SupportIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired SupportOrderRepository orders;
    @BeforeEach void setup(){orders.deleteAll();}
    String input(int amount,String request){return "{\"amount\":"+amount+",\"requestId\":\""+request+"\"}";}
    JsonNode create(MockHttpSession session,int amount,String key)throws Exception{return json.readTree(mvc.perform(post("/api/support/orders").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input(amount,key))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    @Test void demoIsPublicClearlyLabeledAndProtectedByCsrf()throws Exception{
        mvc.perform(get("/support")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("모의 후원 · 실제 청구 없음")));
        mvc.perform(get("/")).andExpect(content().string(org.hamcrest.Matchers.containsString("커피 한 잔으로 응원하기")));
        mvc.perform(post("/api/support/orders").contentType(MediaType.APPLICATION_JSON).content(input(3000,UUID.randomUUID().toString()))).andExpect(status().isForbidden());
        var result=create(new MockHttpSession(),3000,UUID.randomUUID().toString());assertThat(result.get("mode").asText()).isEqualTo("DEMO");assertThat(result.toString()).doesNotContain("ownerKey","requestKey","cardNumber");
        mvc.perform(post("/api/support/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input(1,UUID.randomUUID().toString()))).andExpect(status().isBadRequest());
    }
    @Test void createAndCompletionAreIdempotentWithImmutableServerAmount()throws Exception{
        var session=new MockHttpSession();var key=UUID.randomUUID().toString();var first=create(session,3000,key);var second=create(session,3000,key);assertThat(second.get("id")).isEqualTo(first.get("id"));assertThat(orders.count()).isEqualTo(1);
        mvc.perform(post("/api/support/orders").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input(5000,key))).andExpect(status().isConflict());
        String path="/api/support/orders/"+first.get("id").asText()+"/result";
        for(int i=0;i<2;i++)mvc.perform(post(path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\",\"amount\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.amount").value(3000));
        mvc.perform(post(path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"CANCEL\"}")).andExpect(status().isConflict());
        assertThat(orders.count()).isEqualTo(1);
    }
    @Test void anotherSessionCannotReadOrFinishAnOrder()throws Exception{
        var owner=new MockHttpSession();var other=new MockHttpSession();var order=create(owner,1000,UUID.randomUUID().toString());var path="/api/support/orders/"+order.get("id").asText();
        mvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        mvc.perform(post(path+"/result").session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}")).andExpect(status().isNotFound());
        mvc.perform(get(path).session(owner)).andExpect(jsonPath("$.status").value("READY"));
    }
    @Test void cancellationFailureAndExpirationAreTerminal()throws Exception{
        var session=new MockHttpSession();
        for(var entry:new String[][]{{"CANCEL","CANCELLED"},{"FAIL","FAILED"}}){var order=create(session,5000,UUID.randomUUID().toString());String path="/api/support/orders/"+order.get("id").asText()+"/result";
            mvc.perform(post(path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\""+entry[0]+"\"}")).andExpect(jsonPath("$.status").value(entry[1]));
            mvc.perform(post(path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}")).andExpect(status().isConflict());
        }
        var expired=orders.saveAndFlush(new SupportOrder((String)session.getAttribute("supportDemoOwner"),UUID.randomUUID().toString(),3000,Instant.now().minusSeconds(3600)));
        mvc.perform(get("/api/support/orders/"+expired.getId()).session(session)).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(post("/api/support/orders/"+expired.getId()+"/result").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}")).andExpect(status().isConflict());
    }
}
