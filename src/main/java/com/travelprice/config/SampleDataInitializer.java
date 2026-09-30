package com.travelprice.config;

import com.travelprice.domain.*;
import com.travelprice.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Component
public class SampleDataInitializer implements ApplicationRunner {
    private final DestinationRepository destinations;
    private final EvidenceRepository evidence;
    private final PriceMentionRepository prices;
    public SampleDataInitializer(DestinationRepository destinations, EvidenceRepository evidence, PriceMentionRepository prices) {
        this.destinations=destinations; this.evidence=evidence; this.prices=prices;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        addDestination("sokcho","속초관광수산시장","강원","시장 음식 · 특산품","속초 중앙시장, 속초관광수산시장","대포항, 오징어난전, 속초해수욕장");
        addDestination("gyeongju","경주 황리단길","경북","식당 · 카페 · 기념품","경주 황리단길","보문단지, 경주 전체");
        addDestination("jeonju","전주 한옥마을","전북","간식 · 한복 · 체험","전주 한옥마을","전주시 전체");
        addDestination("busan","부산 해운대","부산","음식 · 해변 · 주차","해운대해수욕장 주변 상권","광안리, 자갈치시장");
        addDestination("jeju","제주 동문시장","제주","시장 음식 · 특산품","제주 동문시장","서귀포매일올레시장, 제주 전체");
        addDestination("gangneung","강릉 안목해변","강원","카페 · 식당 · 주차","안목해변, 안목 커피거리","경포해변, 주문진");
        var sokcho=destinations.findBySlug("sokcho").orElseThrow();
        addEvidence(sokcho,"정이네 속초중앙시장점 방문 리뷰","방문자 리뷰","https://polle.com/lkhun71/posts/1215",LocalDate.of(2026,7,16),
                "오징어순대 가격 대비 만족을 표현한 리뷰입니다. 시장 전체 가격에 대한 작성자의 주장은 검증되지 않았습니다.","누룽지 오징어순대","구성·중량 미확인",15000);
        addEvidence(sokcho,"속초 중앙시장 먹거리 방문 후기","개인 블로그","https://030i0i.blogspot.com/2026/07/blog-post.html",LocalDate.of(2026,7,21),
                "닭강정이 이전보다 비싸졌다고 느낀 후기입니다. 인상 금액은 확인되지 않았으며 먹거리 구매 만족도 함께 표현합니다.","막걸리술빵","1봉지 · 중량 미확인",5000);
    }
    private void addDestination(String slug,String name,String region,String categories,String aliases,String excluded) {
        if(destinations.findBySlug(slug).isEmpty()) destinations.save(new Destination(slug,name,region,categories,aliases,excluded));
    }
    private void addEvidence(Destination destination,String title,String type,String url,LocalDate date,String note,String item,String unit,long amount) {
        if(evidence.existsByDestinationSlugAndUrl(destination.getSlug(),url)) return;
        var saved=evidence.save(new Evidence(destination,title,type,url,date,note));
        prices.save(new PriceMention(saved,item,unit,amount));
    }
}
