package com.travelprice;
import com.fasterxml.jackson.databind.*;
import com.travelprice.domain.SupportOrder;
import com.travelprice.repository.SupportOrderRepository;
import com.travelprice.service.TossPaymentClient;
import com.travelprice.api.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:supporttest;MODE=MySQL;DB_CLOSE_DELAY=-1") @AutoConfigureMockMvc @ActiveProfiles("test")
class SupportIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired SupportOrderRepository orders;
    @MockitoBean TossPaymentClient gateway;
    @BeforeEach void setup(){orders.deleteAll();when(gateway.available()).thenReturn(true);when(gateway.mode()).thenReturn("TEST");when(gateway.clientKey()).thenReturn("test_ck_example");}
    JsonNode create(MockHttpSession session)throws Exception{return json.readTree(mvc.perform(post("/api/support/orders").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":3000,\"requestId\":\""+UUID.randomUUID()+"\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    String body(String key,int amount){return "{\"paymentKey\":\""+key+"\",\"amount\":"+amount+"}";}
    JsonNode approved(String id,int amount){return json.createObjectNode().put("status","DONE").put("orderId",id).put("paymentKey","pg-key").put("totalAmount",amount).put("currency","KRW");}
    void confirm(String id,MockHttpSession session,int amount,int status)throws Exception{mvc.perform(post("/api/support/orders/"+id+"/confirm").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("pg-key",amount))).andExpect(status().is(status));}
    @Test void approvalIsServerVerifiedAndIdempotent()throws Exception{
        var session=new MockHttpSession();var order=create(session);String id=order.path("id").asText();
        when(gateway.confirm(id,"pg-key",3000)).thenReturn(approved(id,3000));confirm(id,session,3000,200);confirm(id,session,3000,200);
        verify(gateway,times(1)).confirm(id,"pg-key",3000);assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(SupportOrder.Status.SUCCEEDED);
    }
    @Test void forgedAmountOtherSessionAndFakeSuccessCannotCredit()throws Exception{
        var session=new MockHttpSession();var id=create(session).path("id").asText();
        confirm(id,session,1,400);confirm(id,new MockHttpSession(),3000,404);verify(gateway,never()).confirm(anyString(),anyString(),anyInt());
        mvc.perform(post("/api/support/orders/"+id+"/result").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/support/orders/"+id+"/confirm").session(session).contentType(MediaType.APPLICATION_JSON).content(body("pg-key",3000))).andExpect(status().isForbidden());
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(SupportOrder.Status.READY);
    }
    @Test void mismatchedGatewayResponseStaysPendingAndCanRetry()throws Exception{
        var session=new MockHttpSession();var id=create(session).path("id").asText();
        when(gateway.confirm(id,"pg-key",3000)).thenReturn(approved(id,1));confirm(id,session,3000,502);
        assertThat(orders.findById(id).orElseThrow().isConfirming()).isTrue();
        mvc.perform(post("/api/support/orders/"+id+"/confirm").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("different-key",3000))).andExpect(status().isConflict());
        doReturn(approved(id,3000)).when(gateway).confirm(id,"pg-key",3000);confirm(id,session,3000,200);
    }
    @Test void gatewayTimeoutDoesNotPretendFailureOrSuccess()throws Exception{
        var session=new MockHttpSession();var id=create(session).path("id").asText();
        when(gateway.confirm(id,"pg-key",3000)).thenThrow(new ApiException(502,"확인 중"));confirm(id,session,3000,502);
        assertThat(orders.findById(id).orElseThrow().getPaymentKey()).isEqualTo("pg-key");
        doReturn(approved(id,3000)).when(gateway).confirm(id,"pg-key",3000);confirm(id,session,3000,200);
    }
    @Test void legacyDemoAndExpiredOrdersCannotBeApproved()throws Exception{
        var session=new MockHttpSession();create(session);String owner=(String)session.getAttribute("supportDemoOwner");
        var legacy=orders.saveAndFlush(new SupportOrder(owner,UUID.randomUUID().toString(),3000,Instant.now()));confirm(legacy.getId(),session,3000,409);
        var expired=new SupportOrder(owner,UUID.randomUUID().toString(),3000,Instant.now().minusSeconds(3600));expired.configure("TEST");orders.saveAndFlush(expired);confirm(expired.getId(),session,3000,409);
        verify(gateway,never()).confirm(anyString(),anyString(),anyInt());
    }
    @Test void unavailableKeysDisablePayments()throws Exception{
        when(gateway.available()).thenReturn(false);when(gateway.clientKey()).thenReturn("");doThrow(new ApiException(503,"준비 중")).when(gateway).requireAvailable();
        mvc.perform(get("/api/support/orders/config")).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.clientKey").value(""));
        mvc.perform(post("/api/support/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":3000,\"requestId\":\""+UUID.randomUUID()+"\"}")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/support/success")).andExpect(status().isOk());
    }
}
