package com.travelprice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** 후기 추출에 사용할 시스템 메시지와 사용자 입력을 준비한다. 모델 호출은 하지 않는다. */
@Component
public class ReviewPromptProvider {
    private final ObjectMapper mapper;
    private final String systemPrompt;

    public ReviewPromptProvider(ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        var resource = new ClassPathResource("prompts/review-extraction-system.txt");
        try (var input = resource.getInputStream()) {
            this.systemPrompt = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
        if (systemPrompt.isBlank()) throw new IOException("후기 추출 시스템 프롬프트가 비어 있습니다.");
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    /** 날짜가 없는 후기는 published_at에 null을 전달한다. 출처 ID는 닉네임 대신 내부 ID를 쓴다. */
    public List<ChatMessage> messages(String destination, String sourceId, LocalDate publishedAt, String body) {
        if (destination == null || destination.isBlank() || sourceId == null || sourceId.isBlank()
                || body == null || body.isBlank()) {
            throw new IllegalArgumentException("관광지, 출처 ID, 후기 본문이 필요합니다.");
        }
        var input = new ReviewInput(destination, sourceId, publishedAt == null ? null : publishedAt.toString(), body);
        try {
            return List.of(new ChatMessage("system", systemPrompt),
                    new ChatMessage("user", mapper.writeValueAsString(input)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("후기 입력을 JSON으로 만들지 못했습니다.", e);
        }
    }

    public record ChatMessage(String role, String content) {}
    public record ReviewInput(String destination, String source_id, String published_at, String body) {}
}
