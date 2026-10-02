package com.travelprice.service;
import com.fasterxml.jackson.databind.*;
import com.travelprice.api.ApiModels.*;
import com.travelprice.api.ApiException;
import com.travelprice.domain.Destination;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class VisitorSearchIntegrationTest {
    ObjectMapper json=new ObjectMapper();HttpServer server;String base;
    Destination destination=new Destination("sokcho","속초관광수산시장","강원","먹거리","속초시장,속초 중앙시장","대포항,오징어난전,고성");
    AnalysisRequest selection=new AnalysisRequest("sokcho",2026,"live");
    @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.start();base="http://127.0.0.1:"+server.getAddress().getPort();}
    @AfterEach void stop(){server.stop(0);}
    @Test void searchesFourFieldsSeparatelyAndKeepsFailureAndEvidenceGapSeparate()throws Exception{
        var calls=new AtomicInteger();server.createContext("/v1beta/models/gemini-2.5-flash:generateContent",exchange->{
            var request=json.readTree(exchange.getRequestBody());var prompt=request.path("contents").path(0).path("parts").path(0).path("text").asText();calls.incrementAndGet();
            assertThat(prompt).contains("site:gall.dcinside.com","site:fmkorea.com","site:theqoo.net","속초시장","작성 기간: 2026-01-01");assertThat(request.path("tools").path(0).has("google_search")).isTrue();
            if(prompt.contains("이번 조사 분야: 혼잡·대기")){exchange.sendResponseHeaders(503,-1);exchange.close();return;}
            if(prompt.contains("이번 조사 분야: 시장 주변 숙박비")){reply(exchange,"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"근거 없는 숙박 평가\"}]},\"groundingMetadata\":{\"webSearchQueries\":[\"숙소 검색\"]}}]}");return;}
            String claim=prompt.contains("이번 조사 분야: 식비")?"품목에 따라 가격 체감이 달랐어요.":"한 점포의 친절한 응대 경험이 있어요.";
            var root=json.createObjectNode();var candidate=root.putArray("candidates").addObject();candidate.putObject("content").putArray("parts").addObject().put("text",claim+" 근거 없는 100% 만족 주장");
            var metadata=candidate.putObject("groundingMetadata");metadata.putArray("webSearchQueries").add("속초시장 후기 site:theqoo.net");metadata.putArray("groundingChunks").addObject().putObject("web").put("uri","https://theqoo.net/travel/123");
            var support=metadata.putArray("groundingSupports").addObject();support.putObject("segment").put("text",claim);support.putArray("groundingChunkIndices").add(0);
            reply(exchange,json.writeValueAsString(root));
        });
        var client=new GeminiSearchClient(json,"example","gemini-2.5-flash",base);var result=client.analyze(destination,selection,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31));
        assertThat(calls.get()).isEqualTo(4);assertThat(result.aspects()).hasSize(4);assertThat(result.sources()).hasSize(1);assertThat(result.summary()).doesNotContain("100%");
        assertThat(result.aspects().stream().filter(a->a.key().equals("lodging_cost")).findFirst().orElseThrow().status()).isEqualTo("insufficient");
        assertThat(result.aspects().stream().filter(a->a.key().equals("crowding")).findFirst().orElseThrow().status()).isEqualTo("failed");
        assertThat(result.coverage().get(0).requestedQueries()).hasSize(4);assertThat(result.coverage().get(0).executedQueries()).hasSize(1);
        assertThat(result.aspects().get(0).findings().get(0).sourceIds()).containsExactly(1L);assertThat(result.aspects().get(2).findings().get(0).sourceIds()).containsExactly(1L);
    }
    @Test void allNetworkFailuresAreNotPresentedAsNoComplaints(){
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent",exchange->{exchange.sendResponseHeaders(429,-1);exchange.close();});
        assertThatThrownBy(()->new GeminiSearchClient(json,"example","gemini-2.5-flash",base).analyze(destination,selection,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31))).isInstanceOf(ApiException.class);
    }
    @Test void parserRejectsUnlinkedClaimsAndMergesDuplicateGroundingLinks()throws Exception{
        var root=json.readTree("""
        {"candidates":[{"content":{"parts":[{"text":"식비 체감은 갈렸어요. 근거 없는 결론"}]},"groundingMetadata":{"groundingChunks":[{"web":{"uri":"https://example.com/one"}},{"web":{"uri":"https://example.com/two"}},{"web":{"uri":"https://secret@example.com/three"}}],"groundingSupports":[{"segment":{"text":"식비 체감은 갈렸어요."},"groundingChunkIndices":[0]},{"segment":{"text":"식비 체감은 갈렸어요."},"groundingChunkIndices":[1]},{"segment":{"text":"응답에 없는 문장"},"groundingChunkIndices":[1]},{"segment":{"text":"근거 없는 결론"},"groundingChunkIndices":[2,0.5]}]}}]}
        """);
        var result=new GeminiSearchClient(json,"","gemini-2.5-flash").parseResponse(root,selection);assertThat(result.findings()).hasSize(1);assertThat(result.findings().get(0).sourceIds()).containsExactly(1L,2L);assertThat(result.summary()).doesNotContain("근거 없는","응답에 없는");
    }
    void reply(HttpExchange exchange,String body)throws java.io.IOException{var bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();}
}
