package com.travelprice.service;
import com.fasterxml.jackson.databind.*;
import com.travelprice.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
@Component
public class KakaoPaymentClient {
    public record Ready(String tid,String pcUrl,String mobileUrl){}
    public record Approval(boolean approved,String tid,String cid,String orderId,String userId,long amount){}
    private final String secretKey,mode,cid,baseUrl,siteUrl;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public KakaoPaymentClient(@Value("${app.payments.secret-key:}")String secretKey,
        @Value("${app.payments.mode:TEST}")String mode,@Value("${app.payments.cid:TC0ONETIME}")String cid,
        @Value("${app.payments.api-base-url:https://open-api.kakaopay.com}")String baseUrl,
        @Value("${app.payments.site-url:http://localhost:8080}")String siteUrl,ObjectMapper json){
        this.secretKey=secretKey;this.mode=mode;this.cid=cid;this.baseUrl=baseUrl;
        this.siteUrl=siteUrl.replaceAll("/+$","");this.json=json;
    }
    public boolean available(){
        boolean dev=secretKey.startsWith("DEV");
        return !secretKey.isBlank()&&validSiteUrl()&&(("TEST".equals(mode)&&dev&&"TC0ONETIME".equals(cid))
            ||("LIVE".equals(mode)&&!dev&&!cid.isBlank()&&!cid.startsWith("TC")));
    }
    private boolean validSiteUrl(){try{var uri=URI.create(siteUrl);return siteUrl.length()<=170&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null
        &&(uri.getPath()==null||uri.getPath().isEmpty())&&("https".equals(uri.getScheme())||("TEST".equals(mode)&&"http".equals(uri.getScheme())));}catch(Exception e){return false;}}
    public String mode(){return mode;}public String cid(){return cid;}
    public void requireAvailable(){if(!available())throw new ApiException(503,"카카오페이 결제 서비스 준비 중이에요.");}
    public Ready ready(String orderId,String userId,int amount){
        requireAvailable();
        var body=new HashMap<String,Object>();body.put("cid",cid);body.put("partner_order_id",orderId);body.put("partner_user_id",userId);
        body.put("item_name","여행온도 운영 응원");body.put("quantity",1);body.put("total_amount",amount);body.put("tax_free_amount",0);
        body.put("approval_url",siteUrl+"/support/success?orderId="+orderId);
        body.put("cancel_url",siteUrl+"/support/cancel?orderId="+orderId);
        body.put("fail_url",siteUrl+"/support/fail?orderId="+orderId);
        try{
            var result=post("ready",body);String tid=result.path("tid").asText();
            String pc=result.path("next_redirect_pc_url").asText(),mobile=result.path("next_redirect_mobile_url").asText();
            if(tid.isBlank()||tid.length()>200||!validRedirect(pc)||!validRedirect(mobile))throw new IllegalStateException();
            return new Ready(tid,pc,mobile);
        }catch(Exception e){throw new ApiException(502,"카카오페이 결제를 준비하지 못했어요. 키와 사이트 도메인 설정을 확인해주세요.");}
    }
    public Approval approve(String orderId,String userId,String tid,String token,int amount){
        requireAvailable();
        try{return approval(post("approve",Map.of("cid",cid,"tid",tid,"partner_order_id",orderId,"partner_user_id",userId,"pg_token",token,"total_amount",amount)),false);}
        catch(Exception e){return lookup(tid);}
    }
    public Approval lookup(String tid){
        requireAvailable();
        try{return approval(post("order",Map.of("cid",cid,"tid",tid)),true);}
        catch(Exception e){throw new ApiException(502,"승인 여부를 확인하지 못했어요. 새 결제를 시작하지 말고 이 화면에서 다시 확인해주세요.");}
    }
    private Approval approval(JsonNode result,boolean lookup){
        boolean approved=lookup?"SUCCESS_PAYMENT".equals(result.path("status").asText())
            :!result.path("aid").asText().isBlank()&&!result.path("approved_at").asText().isBlank();
        var amount=result.path("amount").path("total");
        return new Approval(approved&&amount.isIntegralNumber(),result.path("tid").asText(),result.path("cid").asText(),
            result.path("partner_order_id").asText(),result.path("partner_user_id").asText(),amount.isIntegralNumber()?amount.asLong():-1);
    }
    public static boolean validRedirect(String url){
        try{var uri=URI.create(url);String host=uri.getHost();return "https".equals(uri.getScheme())&&uri.getUserInfo()==null&&host!=null
            &&(uri.getPort()==-1||uri.getPort()==443)&&(host.equals("kakao.com")||host.endsWith(".kakao.com")||host.equals("kakaopay.com")||host.endsWith(".kakaopay.com"));}
        catch(Exception e){return false;}
    }
    private JsonNode post(String operation,Map<String,Object> body)throws Exception{
        try{
            var request=HttpRequest.newBuilder(URI.create(baseUrl+"/online/v1/payment/"+operation)).timeout(Duration.ofSeconds(15))
                .header("Authorization","SECRET_KEY "+secretKey).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException("Gateway unavailable");
            return json.readTree(response.body());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
    }
}
