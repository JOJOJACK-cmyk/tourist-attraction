package com.travelprice.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelprice.api.ApiModels.AnalysisRequest;
import com.travelprice.api.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GeminiSearchClientTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final GeminiSearchClient client=new GeminiSearchClient(mapper,"","gemini-2.5-flash");
    private final AnalysisRequest selection=new AnalysisRequest("sokcho",2026,3,"live");
    @Test void unsupportedAnswersAndUnsafeSourcesAreRejected() throws Exception {
        var noSources=mapper.readTree("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"근거 없는 결론\"}]}}]}");
        assertThatThrownBy(()->client.parseResponse(noSources,selection)).isInstanceOf(ApiException.class);
        var unsafe=mapper.readTree("""
            {"candidates":[{"content":{"parts":[{"text":"요약"}]},"groundingMetadata":{"groundingChunks":[{"web":{"uri":"javascript:alert(1)"}}],"groundingSupports":[{"segment":{"text":"요약"},"groundingChunkIndices":[0]}]}}]}
            """);
        assertThatThrownBy(()->client.parseResponse(unsafe,selection)).isInstanceOf(ApiException.class);
    }
    @Test void citationsKeepOriginalIndicesAndUnknownDatesStayUnknown() throws Exception {
        var payload=mapper.readTree("""
            {"candidates":[{"content":{"parts":[{"text":"참고 요약"}]},"groundingMetadata":{"groundingChunks":[{"web":{"uri":"javascript:alert(1)"}},{"web":{"uri":"https://example.com/review","title":"방문 후기"}}],"groundingSupports":[{"segment":{"text":"참고 요약"},"groundingChunkIndices":[0,1,99]}]}}]}
            """);
        var result=client.parseResponse(payload,selection);
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).id()).isEqualTo(2);
        assertThat(result.sources().get(0).date()).isNull();
        assertThat(result.findings().get(0).sourceIds()).containsExactly(2L);
    }
}
