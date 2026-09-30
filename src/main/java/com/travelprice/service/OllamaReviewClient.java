package com.travelprice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelprice.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OllamaReviewClient {
    private final ObjectMapper mapper;
    private final ReviewPromptProvider prompts;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl, model;
    private final int timeout, context;
    private final JsonNode schema;
    private final AtomicBoolean busy = new AtomicBoolean();

    public OllamaReviewClient(ObjectMapper mapper, ReviewPromptProvider prompts,
            @Value("${app.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.ollama.model:gemma12b:latest}") String model,
            @Value("${app.ollama.timeout-seconds:300}") int timeout,
            @Value("${app.ollama.context-size:16384}") int context) throws IOException {
        this.mapper=mapper; this.prompts=prompts; this.baseUrl=baseUrl.replaceAll("/+$", "");
        this.model=model; this.timeout=timeout; this.context=context;
        try (var input=new ClassPathResource("prompts/review-extraction-schema.json").getInputStream()) {
            this.schema=mapper.readTree(input);
        }
    }

    public Map<String,Object> status() {
        try {
            var response=http.send(HttpRequest.newBuilder(URI.create(baseUrl+"/api/tags"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) return connection(false,"Ollama 서버 응답을 확인해주세요.");
            var models=mapper.readTree(response.body()).path("models");
            for(var entry:models) if(model.equals(entry.path("name").asText())) return connection(true,"연결됨");
            return connection(false,"설정한 모델이 없습니다. OLLAMA_MODEL을 ollama list의 이름으로 설정해주세요.");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); return connection(false,"연결 확인이 중단됐습니다.");
        } catch(Exception e) { return connection(false,"Ollama 앱을 실행한 뒤 다시 확인해주세요."); }
    }

    private Map<String,Object> connection(boolean available,String message) {
        return Map.of("available",available,"model",model,"message",message);
    }

    public JsonNode extract(String destination,String sourceId,LocalDate publishedAt,String body) {
        if(!busy.compareAndSet(false,true)) throw new ApiException(429,"다른 후기를 분석 중이에요. 완료 후 다시 시도해주세요.");
        try {
            var payload=Map.of("model",model,"messages",prompts.messages(destination,sourceId,publishedAt,body),
                    "stream",false,"format",schema,"options",Map.of("temperature",0.1,"num_ctx",context,"num_predict",4096));
            var request=HttpRequest.newBuilder(URI.create(baseUrl+"/api/chat"))
                    .timeout(Duration.ofSeconds(timeout)).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==404) throw new ApiException(503,"설정한 모델을 찾지 못했어요. Ollama 모델 이름을 확인해주세요.");
            if(response.statusCode()!=200) throw new ApiException(502,"Ollama 분석에 실패했어요. Ollama 실행 상태를 확인해주세요.");
            var envelope=mapper.readTree(response.body());
            if(!envelope.path("done").asBoolean(false) || "length".equals(envelope.path("done_reason").asText()))
                throw new ApiException(422,"분석 결과가 중간에 끝났어요. 후기 본문을 줄여 다시 시도해주세요.");
            return parseResult(envelope.path("message").path("content").asText(""),sourceId,publishedAt);
        } catch(ApiException e) { throw e; }
        catch(HttpTimeoutException e) { throw new ApiException(504,"분석 시간이 초과됐어요. 짧은 후기로 다시 시도해주세요."); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(504,"분석이 중단됐어요."); }
        catch(IOException e) { throw new ApiException(503,"Ollama에 연결하지 못했어요. 앱 실행 상태와 주소를 확인해주세요."); }
        finally { busy.set(false); }
    }

    JsonNode parseResult(String text,String sourceId,LocalDate publishedAt) {
        try {
            var result=mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
            validate(result,schema);
            for(var item:result.path("experiences")) {
                if(!sourceId.equals(item.path("source_id").asText())) throw new IllegalArgumentException();
                var expected=publishedAt==null?null:publishedAt.toString();
                var actual=item.path("published_at").isNull()?null:item.path("published_at").asText();
                if(!Objects.equals(expected,actual)) throw new IllegalArgumentException();
                if(!item.path("visited_at").isNull()) {
                    var date=LocalDate.parse(item.path("visited_at").asText());
                    if(date.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))) || (publishedAt!=null && date.isAfter(publishedAt)))
                        throw new IllegalArgumentException();
                }
            }
            return result;
        } catch(Exception e) { throw new ApiException(422,"분석 결과 형식이나 출처가 맞지 않아요. 다시 시도해주세요."); }
    }

    /** 모델의 구조화 출력을 그대로 신뢰하지 않고 동일한 스키마를 서버에서 확인한다. */
    private void validate(JsonNode value,JsonNode rule) {
        if(value==null) throw new IllegalArgumentException();
        var types=rule.path("type");
        if(value.isNull() && types.isArray()) {
            for(var type:types) if("null".equals(type.asText())) return;
        }
        var type=types.isArray()?types.path(0).asText():types.asText();
        switch(type) {
            case "object" -> {
                if(!value.isObject()) throw new IllegalArgumentException();
                var props=rule.path("properties");
                for(var required:rule.path("required")) if(!value.has(required.asText())) throw new IllegalArgumentException();
                var fields=value.fieldNames();
                while(fields.hasNext()) {
                    var field=fields.next(); if(!props.has(field)) throw new IllegalArgumentException();
                    validate(value.get(field),props.get(field));
                }
            }
            case "array" -> {
                if(!value.isArray() || value.size()>rule.path("maxItems").asInt(30)) throw new IllegalArgumentException();
                for(var entry:value) validate(entry,rule.path("items"));
            }
            case "string" -> {
                if(!value.isTextual() || value.asText().length()>rule.path("maxLength").asInt(1200)
                        || (rule.path("minLength").asInt(0)>0 && value.asText().isBlank())) throw new IllegalArgumentException();
                if(rule.has("enum")) {
                    boolean valid=false;for(var option:rule.path("enum")) if(option.equals(value)) valid=true;
                    if(!valid) throw new IllegalArgumentException();
                }
            }
            default -> throw new IllegalArgumentException();
        }
    }
}
