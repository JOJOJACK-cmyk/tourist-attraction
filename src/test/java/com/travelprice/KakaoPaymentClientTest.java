package com.travelprice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import com.travelprice.service.KakaoPaymentClient;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class KakaoPaymentClientTest {
    HttpServer server;String base;ObjectMapper json=new ObjectMapper();
    @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.start();base="http://127.0.0.1:"+server.getAddress().getPort();}
    @AfterEach void stop(){server.stop(0);}
    KakaoPaymentClient client(){return new KakaoPaymentClient("DEV_example","TEST","TC0ONETIME",base,"http://localhost:8080",json);}
    void reply(HttpExchange exchange,String body)throws java.io.IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();}
    String approval(){return "{\"aid\":\"aid-123\",\"tid\":\"tid-123\",\"cid\":\"TC0ONETIME\",\"partner_order_id\":\"order-123\",\"partner_user_id\":\"user-123\",\"approved_at\":\"2026-10-02T12:00:00\",\"amount\":{\"total\":3000}}";}
    @Test void readyUsesSecretHeaderStoredAmountAndFixedCallbackOrigin(){
        var calls=new AtomicInteger();server.createContext("/online/v1/payment/ready",exchange->{
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("SECRET_KEY DEV_example");
            var input=json.readTree(exchange.getRequestBody());assertThat(input.path("total_amount").asInt()).isEqualTo(3000);assertThat(input.path("partner_user_id").asText()).isEqualTo("user-123");
            assertThat(input.path("approval_url").asText()).isEqualTo("http://localhost:8080/support/success?orderId=order-123");
            assertThat(input.path("cancel_url").asText()).contains("/support/cancel?orderId=order-123");calls.incrementAndGet();
            reply(exchange,"{\"tid\":\"tid-123\",\"next_redirect_pc_url\":\"https://online-payment.kakaopay.com/pc\",\"next_redirect_mobile_url\":\"https://online-payment.kakaopay.com/mobile\"}");});
        var result=client().ready("order-123","user-123",3000);assertThat(result.tid()).isEqualTo("tid-123");assertThat(calls.get()).isEqualTo(1);
    }
    @Test void approvesWithServerIdentifiersAndParsesActualKakaoResponse(){
        server.createContext("/online/v1/payment/approve",exchange->{var input=json.readTree(exchange.getRequestBody());assertThat(input.path("pg_token").asText()).isEqualTo("pg-token");assertThat(input.path("tid").asText()).isEqualTo("tid-123");assertThat(input.path("total_amount").asInt()).isEqualTo(3000);reply(exchange,approval());});
        var result=client().approve("order-123","user-123","tid-123","pg-token",3000);assertThat(result.approved()).isTrue();assertThat(result.amount()).isEqualTo(3000);
    }
    @Test void retrievesAlreadyApprovedOrderAfterLostApprovalResponse(){
        server.createContext("/online/v1/payment/approve",exchange->{exchange.sendResponseHeaders(409,-1);exchange.close();});
        server.createContext("/online/v1/payment/order",exchange->{reply(exchange,approval().replace("{\"aid\"","{\"status\":\"SUCCESS_PAYMENT\",\"aid\""));});
        assertThat(client().approve("order-123","user-123","tid-123","pg-token",3000).approved()).isTrue();
    }
    @Test void orderLookupDoesNotTreatReadyOrCancelledAsSuccess(){
        server.createContext("/online/v1/payment/order",exchange->{reply(exchange,approval().replace("{\"aid\"","{\"status\":\"CANCEL_PAYMENT\",\"aid\""));});
        assertThat(client().lookup("tid-123").approved()).isFalse();
    }
    @Test void validatesTestLiveConfigurationAndRedirectHost(){
        assertThat(client().available()).isTrue();
        assertThat(new KakaoPaymentClient("","TEST","TC0ONETIME",base,"http://localhost:8080",json).available()).isFalse();
        assertThat(new KakaoPaymentClient("DEV_example","LIVE","TC0ONETIME",base,"https://example.com",json).available()).isFalse();
        assertThat(new KakaoPaymentClient("LIVE_example","LIVE","OPERATING1",base,"http://example.com",json).available()).isFalse();
        assertThat(new KakaoPaymentClient("LIVE_example","LIVE","OPERATING1",base,"https://example.com",json).available()).isTrue();
        for(String url:new String[]{"https://kakaopay.com.evil.test/pay","https://evil.test/pay","http://online-payment.kakaopay.com/pay","https://user@online-payment.kakaopay.com/pay","javascript:alert(1)"})assertThat(KakaoPaymentClient.validRedirect(url)).isFalse();
    }
}
