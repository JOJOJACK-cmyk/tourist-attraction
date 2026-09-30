package com.travelprice.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelprice.repository.DestinationRepository;
import com.travelprice.service.OllamaReviewClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/reviews")
public class ReviewExtractionController {
    private final OllamaReviewClient ollama;
    private final DestinationRepository destinations;
    public ReviewExtractionController(OllamaReviewClient ollama,DestinationRepository destinations) {
        this.ollama=ollama; this.destinations=destinations;
    }
    @GetMapping("/status")
    public Map<String,Object> status() { return ollama.status(); }

    @PostMapping("/extract")
    public ExtractionResponse extract(@Valid @RequestBody ExtractionRequest input) {
        var destination=destinations.findBySlug(input.destinationId())
                .orElseThrow(()->new ApiException(400,"관광지를 선택해주세요."));
        if(input.publishedAt()!=null && input.publishedAt().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))))
            throw new ApiException(400,"후기 작성일은 오늘까지 선택할 수 있어요.");
        var sourceId="review-"+UUID.randomUUID();
        var analysis=ollama.extract(destination.getName(),sourceId,input.publishedAt(),input.body());
        return new ExtractionResponse(destination.getName(),sourceId,analysis);
    }
    public record ExtractionRequest(@NotBlank String destinationId,LocalDate publishedAt,
                                    @NotBlank @Size(max=4000) String body) {}
    public record ExtractionResponse(String destination,String sourceId,JsonNode analysis) {}
}
