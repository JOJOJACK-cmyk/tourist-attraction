package com.travelprice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.travelprice.service.TossPaymentClient;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class TossPaymentClientTest {
    HttpServer server;String base;ObjectMapper json=new ObjectMapper();
    @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.start();base="http://127.0.0.1:"+server.getAddress().getPort();}
    @AfterEach void stop(){server.stop(0);}
    @Test void sendsSecretBasicAuthAndStableIdempotencyKey()throws Exception{
        var calls=new AtomicInteger();
        server.createContext("/v1/payments/confirm",exchange->{
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Basic dGVzdF9za19leGFtcGxlOg==");
            assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo("order-123");
            var input=json.readTree(exchange.getRequestBody());assertThat(input.path("amount").asInt()).isEqualTo(3000);assertThat(input.path("paymentKey").asText()).isEqualTo("pg-key");
            calls.incrementAndGet();byte[] body="{\"status\":\"DONE\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        var client=new TossPaymentClient("test_ck_example","test_sk_example","TEST",base,json);
        assertThat(client.confirm("order-123","pg-key",3000).path("status").asText()).isEqualTo("DONE");assertThat(calls.get()).isEqualTo(1);
    }
    @Test void retrievesAlreadyApprovedOrderAfterConfirmationError(){
        server.createContext("/v1/payments/confirm",exchange->{exchange.sendResponseHeaders(409,-1);exchange.close();});
        server.createContext("/v1/payments/orders/order-123",exchange->{byte[] body="{\"status\":\"DONE\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
        assertThat(new TossPaymentClient("test_ck_example","test_sk_example","TEST",base,json).confirm("order-123","pg-key",3000).path("status").asText()).isEqualTo("DONE");
    }
    @Test void absentMixedOrWidgetKeysAreUnavailable(){
        for(var keys:new String[][]{{"","","TEST"},{"test_ck_example","live_sk_example","TEST"},{"test_gck_example","test_gsk_example","TEST"},{"live_ck_example","live_sk_example","TEST"}})
            assertThat(new TossPaymentClient(keys[0],keys[1],keys[2],base,json).available()).isFalse();
        assertThat(new TossPaymentClient("live_ck_example","live_sk_example","LIVE",base,json).available()).isTrue();
    }
}
