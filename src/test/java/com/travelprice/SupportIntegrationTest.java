package com.travelprice;
import com.fasterxml.jackson.databind.*;
import com.travelprice.domain.SupportOrder;
import com.travelprice.repository.SupportOrderRepository;
import com.travelprice.service.KakaoPaymentClient;
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
    @MockitoBean KakaoPaymentClient gateway;
    @BeforeEach void setup(){orders.deleteAll();when(gateway.available()).thenReturn(true);when(gateway.mode()).thenReturn("TEST");when(gateway.cid()).thenReturn("TC0ONETIME");
        when(gateway.ready(anyString(),anyString(),eq(3000))).thenReturn(new KakaoPaymentClient.Ready("tid-123","https://online-payment.kakaopay.com/pc","https://online-payment.kakaopay.com/mobile"));
        when(gateway.lookup("tid-123")).thenReturn(new KakaoPaymentClient.Approval(false,"tid-123","TC0ONETIME","","",3000));}
    String input(String request,int amount){return "{\"amount\":"+amount+",\"requestId\":\""+request+"\"}";}
    JsonNode create(MockHttpSession session)throws Exception{return json.readTree(mvc.perform(post("/api/support/orders").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input(UUID.randomUUID().toString(),3000))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    void ready(String id,MockHttpSession session,boolean mobile)throws Exception{mvc.perform(post("/api/support/orders/"+id+"/ready").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"mobile\":"+mobile+"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.redirectUrl").value("https://online-payment.kakaopay.com/"+(mobile?"mobile":"pc")));}
    KakaoPaymentClient.Approval approved(String id,MockHttpSession session,long amount){return new KakaoPaymentClient.Approval(true,"tid-123","TC0ONETIME",id,(String)session.getAttribute("supportDemoOwner"),amount);}
    void confirm(String id,MockHttpSession session,int status)throws Exception{mvc.perform(post("/api/support/orders/"+id+"/confirm").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"pgToken\":\"pg-token\"}")).andExpect(status().is(status));}
    @Test void approvalIsServerVerifiedAndIdempotent()throws Exception{
        var session=new MockHttpSession();var id=create(session).path("id").asText();ready(id,session,false);ready(id,session,true);
        verify(gateway,times(1)).ready(id,(String)session.getAttribute("supportDemoOwner"),3000);
        when(gateway.approve(eq(id),anyString(),eq("tid-123"),eq("pg-token"),eq(3000))).thenReturn(approved(id,session,3000));
        confirm(id,session,200);confirm(id,session,200);verify(gateway,times(1)).approve(eq(id),anyString(),anyString(),anyString(),eq(3000));
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(SupportOrder.Status.SUCCEEDED);
    }
    @Test void concurrentCallbacksOnlyApproveOnce()throws Exception{
        var session=new MockHttpSession();var id=create(session).path("id").asText();ready(id,session,false);
        when(gateway.approve(eq(id),anyString(),anyString(),anyString(),anyInt())).thenReturn(approved(id,session,3000));
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var first=executor.submit(()->{confirm(id,session,200);return true;});
            var second=executor.submit(()->{confirm(id,session,200);return true;});
            assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(second.get(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }finally{executor.shutdownNow();}
        verify(gateway,times(1)).approve(eq(id),anyString(),anyString(),anyString(),anyInt());
    }
    @Test void otherSessionFakeSuccessAndCsrfCannotCredit()throws Exception{
        var session=new MockHttpSession();var id=create(session).path("id").asText();ready(id,session,false);
        confirm(id,new MockHttpSession(),404);
        mvc.perform(post("/api/support/orders/"+id+"/ready").session(new MockHttpSession()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/support/orders/"+id+"/result").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/support/orders/"+id+"/confirm").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"pgToken\":\"pg-token\"}")).andExpect(status().isForbidden());
        verify(gateway,never()).approve(anyString(),anyString(),anyString(),anyString(),anyInt());
    }
    @Test void providerResponseMustMatchAllStoredIdentifiersAndAmount()throws Exception{
        var session=new MockHttpSession();var id=create(session).path("id").asText();ready(id,session,false);String user=(String)session.getAttribute("supportDemoOwner");
        for(var response:new KakaoPaymentClient.Approval[]{new KakaoPaymentClient.Approval(true,"wrong","TC0ONETIME",id,user,3000),new KakaoPaymentClient.Approval(true,"tid-123","OTHER",id,user,3000),new KakaoPaymentClient.Approval(true,"tid-123","TC0ONETIME","wrong",user,3000),new KakaoPaymentClient.Approval(true,"tid-123","TC0ONETIME",id,"wrong",3000),approved(id,session,1),new KakaoPaymentClient.Approval(false,"tid-123","TC0ONETIME",id,user,3000)}){
            when(gateway.approve(eq(id),anyString(),anyString(),anyString(),anyInt())).thenReturn(response);confirm(id,session,502);
            assertThat(orders.findById(id).orElseThrow().isConfirming()).isTrue();}
        mvc.perform(post("/api/support/orders/"+id+"/confirm").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"pgToken\":\"different\"}")).andExpect(status().isConflict());
    }
    @Test void uncertainApprovalRecoversThroughOrderLookupWithoutSecondApproval()throws Exception{
        var session=new MockHttpSession();var id=create(session).path("id").asText();ready(id,session,false);
        when(gateway.approve(eq(id),anyString(),anyString(),anyString(),anyInt())).thenThrow(new ApiException(502,"확인 중"));confirm(id,session,502);
        assertThat(orders.findById(id).orElseThrow().isConfirming()).isTrue();
        when(gateway.lookup("tid-123")).thenReturn(approved(id,session,3000));
        mvc.perform(post("/api/support/orders/"+id+"/reconcile").session(session).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        confirm(id,session,200);verify(gateway,times(1)).approve(eq(id),anyString(),anyString(),anyString(),anyInt());
    }
    @Test void legacyTossAndExpiredOrdersCannotBeApproved()throws Exception{
        var session=new MockHttpSession();create(session);String owner=(String)session.getAttribute("supportDemoOwner");
        var legacy=orders.saveAndFlush(new SupportOrder(owner,UUID.randomUUID().toString(),3000,Instant.now()));confirm(legacy.getId(),session,409);
        var toss=new SupportOrder(owner,UUID.randomUUID().toString(),3000,Instant.now());toss.configure("TEST");orders.saveAndFlush(toss);confirm(toss.getId(),session,409);
        var expired=new SupportOrder(owner,UUID.randomUUID().toString(),3000,Instant.now().minusSeconds(3600));expired.configureKakao("TEST","TC0ONETIME");orders.saveAndFlush(expired);confirm(expired.getId(),session,409);
        verify(gateway,never()).approve(anyString(),anyString(),anyString(),anyString(),anyInt());
    }
    @Test void unavailableKeysAndUnpreparedOrdersCannotStartApproval()throws Exception{
        var session=new MockHttpSession();var id=create(session).path("id").asText();confirm(id,session,409);
        when(gateway.available()).thenReturn(false);doThrow(new ApiException(503,"준비 중")).when(gateway).requireAvailable();
        mvc.perform(get("/api/support/orders/config")).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.provider").value("KAKAOPAY")).andExpect(jsonPath("$.secretKey").doesNotExist());
        mvc.perform(post("/api/support/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input(UUID.randomUUID().toString(),3000))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/support/success")).andExpect(status().isOk());mvc.perform(get("/support/cancel")).andExpect(status().isOk());
    }
    @Test void creationReusesRequestAndRejectsChangedOrUnlistedAmounts()throws Exception{
        var session=new MockHttpSession();String key=UUID.randomUUID().toString();
        for(int i=0;i<2;i++)mvc.perform(post("/api/support/orders").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input(key,3000))).andExpect(status().isCreated());
        assertThat(orders.count()).isEqualTo(1);
        mvc.perform(post("/api/support/orders").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input(key,5000))).andExpect(status().isConflict());
        mvc.perform(post("/api/support/orders").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input(UUID.randomUUID().toString(),1))).andExpect(status().isBadRequest());
    }
}
