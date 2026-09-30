package com.travelprice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.travelprice.api.ApiException;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class OllamaReviewClientTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private HttpServer server;
    private OllamaReviewClient client;
    private final AtomicReference<String> request=new AtomicReference<>();
    private String chatResponse;
    private int chatStatus=200;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/tags",exchange->{
            byte[] bytes="{\"models\":[{\"name\":\"gemma12b:latest\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/api/chat",exchange->{
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes=chatResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(chatStatus,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        client=new OllamaReviewClient(mapper,new ReviewPromptProvider(mapper),
                "http://127.0.0.1:"+server.getAddress().getPort(),"gemma12b:latest",10,16384);
    }
    @AfterEach void stop() { server.stop(0); }

    private String valid() throws Exception {
        var result=mapper.readTree("""
            {"experiences":[{"source_id":"r1","category":"parking","topic":"access_convenience",
            "sentiment":"negative","experience_type":"direct","place":null,"item":null,
            "summary":"오르막 이동이 불편했다는 경험","condition":"유아차 동행","reason":null,
            "alternative":null,"check_before_visit":null,"published_at":"2026-09-01","visited_at":null,
            "support":"주차장부터 입구까지 유아차 이동이 힘들었다고 설명함","uncertainties":[]}],"limitations":[]}
            """);
        return result.toString();
    }
    @Test void sendsSystemPromptAndSchemaAndReturnsValidatedExperiences() throws Exception {
        chatResponse=mapper.writeValueAsString(java.util.Map.of("done",true,"message",java.util.Map.of("content",valid())));
        var output=client.extract("속초관광수산시장","r1",LocalDate.of(2026,9,1),"주차장에서 유아차 이동이 힘들었다.");
        var sent=mapper.readTree(request.get());
        assertThat(sent.path("model").asText()).isEqualTo("gemma12b:latest");
        assertThat(sent.path("stream").asBoolean()).isFalse();
        assertThat(sent.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(sent.path("messages").path(0).path("content").asText()).contains("amenities");
        assertThat(sent.path("format").path("properties").has("experiences")).isTrue();
        assertThat(output.path("experiences").path(0).path("category").asText()).isEqualTo("parking");
        assertThat(client.status().get("available")).isEqualTo(true);
    }
    @Test void rejectsMalformedJsonInvalidSourceUnknownCategoryAndChangedDate() throws Exception {
        for(var content:new String[]{"not json",valid().replace("r1","r2"),valid().replace("parking","unknown"),valid().replace("2026-09-01","2026-08-01"),valid()+" {}"}) {
            assertThatThrownBy(()->client.parseResult(content,"r1",LocalDate.of(2026,9,1))).isInstanceOf(ApiException.class);
        }
    }
    @Test void handlesMissingModelAndTruncatedResponseThenAllowsAnotherRequest() throws Exception {
        chatStatus=404;chatResponse="{}";
        assertThatThrownBy(()->client.extract("속초","r1",null,"후기")).isInstanceOf(ApiException.class).hasMessageContaining("모델");
        chatStatus=200;chatResponse="{\"done\":true,\"done_reason\":\"length\",\"message\":{\"content\":\"{}\"}}";
        assertThatThrownBy(()->client.extract("속초","r1",null,"후기")).isInstanceOf(ApiException.class).hasMessageContaining("중간");
        chatResponse=mapper.writeValueAsString(java.util.Map.of("done",true,"message",java.util.Map.of("content","{\"experiences\":[],\"limitations\":[]}")));
        assertThat(client.extract("속초","r1",null,"후기").path("experiences").size()).isZero();
    }
}
