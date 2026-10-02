package com.travelprice.service;
import com.fasterxml.jackson.databind.*;
import com.travelprice.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class TossPaymentClient {
    private final String clientKey,secretKey,mode,baseUrl;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public TossPaymentClient(@Value("${app.payments.client-key:}")String clientKey,
        @Value("${app.payments.secret-key:}")String secretKey,@Value("${app.payments.mode:TEST}")String mode,
        @Value("${app.payments.api-base-url:https://api.tosspayments.com}")String baseUrl,ObjectMapper json){
        this.clientKey=clientKey;this.secretKey=secretKey;this.mode=mode;this.baseUrl=baseUrl;this.json=json;
    }
    public boolean available(){String prefix="LIVE".equals(mode)?"live_":"test_";return Set.of("TEST","LIVE").contains(mode)&&clientKey.startsWith(prefix+"ck_")&&secretKey.startsWith(prefix+"sk_");}
    public String clientKey(){return available()?clientKey:"";}public String mode(){return mode;}
    public void requireAvailable(){if(!available())throw new ApiException(503,"결제 서비스 준비 중이에요.");}
    public JsonNode confirm(String orderId,String paymentKey,int amount){
        requireAvailable();
        try{
            var body=json.writeValueAsString(Map.of("orderId",orderId,"paymentKey",paymentKey,"amount",amount));
            var response=http.send(request("/v1/payments/confirm").header("Idempotency-Key",orderId)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==200)return json.readTree(response.body());
            // Recover an approval whose response or local database commit was lost.
            return lookup(orderId);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw pending();}
        catch(ApiException e){throw e;}
        catch(Exception e){try{return lookup(orderId);}catch(Exception ignored){throw pending();}}
    }
    private JsonNode lookup(String orderId){
        try{
            var response=http.send(request("/v1/payments/orders/"+orderId).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==200)return json.readTree(response.body());
        }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception ignored){}
        throw pending();
    }
    private HttpRequest.Builder request(String path){
        String auth=Base64.getEncoder().encodeToString((secretKey+":").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(Duration.ofSeconds(15))
            .header("Authorization","Basic "+auth).header("Content-Type","application/json");
    }
    private ApiException pending(){return new ApiException(502,"승인 여부를 확인하지 못했어요. 새 결제를 시작하지 말고 이 화면에서 다시 확인해주세요.");}
}
