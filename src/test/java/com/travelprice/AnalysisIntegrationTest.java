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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AnalysisIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SampleDataInitializer initializer;
    @Autowired DestinationRepository destinations;
    @Autowired EvidenceRepository evidence;
    @Autowired PriceMentionRepository prices;
    @Test void sampleIsReadFromDatabaseWithPricesAndSources() throws Exception {
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content("{\"destinationId\":\"sokcho\",\"year\":2026,\"quarter\":3,\"mode\":\"sample\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("reviewed_sample"))
                .andExpect(jsonPath("$.prices.length()").value(2)).andExpect(jsonPath("$.sources.length()").value(2));
    }
    @Test void sampleDoesNotLeakIntoAnotherPlaceOrQuarter() throws Exception {
        for(String body: new String[]{"{\"destinationId\":\"jeju\",\"year\":2026,\"quarter\":3,\"mode\":\"sample\"}","{\"destinationId\":\"sokcho\",\"year\":2026,\"quarter\":2,\"mode\":\"sample\"}"})
            mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("empty")).andExpect(jsonPath("$.sources.length()").value(0));
    }
    @Test void invalidAndFutureSelectionsAreRejected() throws Exception {
        for(String body:new String[]{"{\"destinationId\":\"unknown\",\"year\":2026,\"quarter\":3,\"mode\":\"sample\"}","{\"destinationId\":\"sokcho\",\"year\":2026,\"quarter\":5,\"mode\":\"sample\"}","{\"destinationId\":\"sokcho\",\"year\":2100,\"quarter\":1,\"mode\":\"sample\"}"})
            mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    @Test void missingKeyHasAnHonestUnavailableState() throws Exception {
        mvc.perform(get("/api/status")).andExpect(jsonPath("$.liveAvailable").value(false));
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content("{\"destinationId\":\"sokcho\",\"year\":2026,\"quarter\":3,\"mode\":\"live\"}"))
                .andExpect(status().isServiceUnavailable());
    }
    @Test void startupIsIdempotent() {
        initializer.run(new DefaultApplicationArguments());
        assertThat(destinations.count()).isEqualTo(6);
        assertThat(evidence.count()).isEqualTo(2);
        assertThat(prices.count()).isEqualTo(2);
    }
    @Test void homeAndStaticFilesAreServed() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("여행지 경험과 가격 단서")));
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/data/sokcho-review-pilot.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(30))
                .andExpect(jsonPath("$.themes.length()").value(10))
                .andExpect(jsonPath("$.dateFrom").value("2025-01-20"))
                .andExpect(jsonPath("$.dateTo").value("2026-09-13"));
    }
}
