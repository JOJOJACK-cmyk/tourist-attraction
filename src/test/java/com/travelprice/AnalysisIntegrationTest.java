package com.travelprice;

import com.travelprice.config.SampleDataInitializer;
import com.travelprice.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AnalysisIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SampleDataInitializer initializer;
    @Autowired DestinationRepository destinations;
    @Autowired EvidenceRepository evidence;
    @Autowired PriceMentionRepository prices;
    @Test void sampleIsReadFromDatabaseWithPricesAndSources() throws Exception {
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content("{\"destinationId\":\"sokcho\",\"year\":2026,\"mode\":\"sample\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("reviewed_sample"))
                .andExpect(jsonPath("$.prices.length()").value(2)).andExpect(jsonPath("$.sources.length()").value(2));
    }
    @Test void sampleDoesNotLeakIntoAnotherPlaceOrYear() throws Exception {
        for(String body: new String[]{"{\"destinationId\":\"jeju\",\"year\":2026,\"mode\":\"sample\"}","{\"destinationId\":\"sokcho\",\"year\":2025,\"mode\":\"sample\"}"})
            mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("empty")).andExpect(jsonPath("$.sources.length()").value(0));
    }
    @Test void invalidAndFutureSelectionsAreRejected() throws Exception {
        for(String body:new String[]{"{\"destinationId\":\"unknown\",\"year\":2026,\"mode\":\"sample\"}","{\"destinationId\":\"sokcho\",\"year\":2019,\"mode\":\"sample\"}","{\"destinationId\":\"sokcho\",\"year\":2100,\"mode\":\"sample\"}"})
            mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    @Test void missingKeyHasAnHonestUnavailableState() throws Exception {
        mvc.perform(get("/api/status")).andExpect(jsonPath("$.liveAvailable").value(false));
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content("{\"destinationId\":\"sokcho\",\"year\":2026,\"mode\":\"live\"}"))
                .andExpect(status().isServiceUnavailable());
    }
    @Test void startupIsIdempotent() {
        initializer.run(new DefaultApplicationArguments());
        assertThat(destinations.count()).isEqualTo(6);
        assertThat(evidence.count()).isEqualTo(2);
        assertThat(prices.count()).isEqualTo(2);
    }
    @Test void invalidLocalReviewsAreRejectedBeforeCallingOllama() throws Exception {
        for(String body: new String[]{
            "{\"destinationId\":\"sokcho\",\"body\":\"\"}",
            "{\"destinationId\":\"unknown\",\"body\":\"후기\"}",
            "{\"destinationId\":\"sokcho\",\"publishedAt\":\"2100-01-01\",\"body\":\"후기\"}"
        })
            mvc.perform(post("/api/reviews/extract").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
    }
    @Test void sokchoIntroductionIsPublicAndLinksToTravelPlanning() throws Exception {
        mvc.perform(get("/destinations/sokcho")).andExpect(status().isOk())
                .andExpect(view().name("destination-sokcho"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("속초시장 대표 먹거리")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("특산품과 가져갈 만한 선물")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("중앙로147번길 12")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/#report\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("후기 분석 관리"))));
    }
    @Test void homeAndStaticFilesAreServed() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("여행지 종합 후기")));
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/js/review-insights.js")).andExpect(status().isOk());
        mvc.perform(get("/data/sokcho-prompt-pilot.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("assistant_prompt_pilot"))
                .andExpect(jsonPath("$.reviews.length()").value(30))
                .andExpect(jsonPath("$.reviews[5].analysis.experiences[1].category").value("parking"));
        mvc.perform(get("/js/local-review.js").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/data/sokcho-review-pilot.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(30))
                .andExpect(jsonPath("$.themes.length()").value(10))
                .andExpect(jsonPath("$.dateFrom").value("2025-01-20"))
                .andExpect(jsonPath("$.dateTo").value("2026-09-13"))
                .andExpect(jsonPath("$.sources[0].author").doesNotExist())
                .andExpect(jsonPath("$.sources[0].title").value("익명 후기 1"));
    }
}
