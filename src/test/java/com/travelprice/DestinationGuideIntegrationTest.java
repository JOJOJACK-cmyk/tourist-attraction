package com.travelprice;

import com.travelprice.service.DestinationGuideCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class DestinationGuideIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DestinationGuideCatalog guides;

    @Test void everyAdditionalGuideRendersPubliclyWithItsOwnReviewLink() throws Exception {
        for (String slug : new String[]{"gyeongju", "jeonju", "busan", "jeju", "gangneung", "seomun", "yeosu", "damyang"}) {
            var guide = guides.find(slug).orElseThrow();
            mvc.perform(get("/destinations/" + slug)).andExpect(status().isOk())
                .andExpect(view().name("destination-guide"))
                .andExpect(content().string(containsString(guide.name())))
                .andExpect(content().string(containsString("/?destination=" + slug + "#report")))
                .andExpect(content().string(containsString("비용과 방문 경험 체크")))
                .andExpect(content().string(not(containsString("후기 분석 관리"))));
            assertThat(guide.budget()).hasSize(4);
            assertThat(guide.sources()).isNotEmpty().allSatisfy(source -> assertThat(source.url()).startsWith("https://"));
        }
    }
    @Test void unknownGuideReturnsNotFoundInsteadOfAnUnrelatedDestination() throws Exception {
        mvc.perform(get("/destinations/not-a-place")).andExpect(status().isNotFound());
    }
}
