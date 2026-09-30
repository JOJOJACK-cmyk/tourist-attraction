package com.travelprice.service;

import com.travelprice.api.ApiModels.*;
import com.travelprice.api.ApiException;
import com.travelprice.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class AnalysisService {
    private final DestinationRepository destinations;
    private final EvidenceRepository evidence;
    private final PriceMentionRepository prices;
    private final GeminiSearchClient ai;
    public AnalysisService(DestinationRepository destinations,EvidenceRepository evidence,PriceMentionRepository prices,GeminiSearchClient ai) {
        this.destinations=destinations; this.evidence=evidence; this.prices=prices; this.ai=ai;
    }
    public AnalysisResult analyze(AnalysisRequest request) {
        var destination=destinations.findBySlug(request.destinationId()).orElseThrow(()->new ApiException(400,"관광지를 다시 선택해주세요."));
        var start=LocalDate.of(request.year(),1,1);
        var today=LocalDate.now(ZoneId.of("Asia/Seoul"));
        if(start.isAfter(today)) throw new ApiException(400,"아직 시작하지 않은 연도는 조회할 수 없습니다.");
        if("live".equals(request.mode())) return ai.analyze(destination,request,start,start.plusYears(1).minusDays(1));
        return sample(request,start,start.plusYears(1).minusDays(1));
    }
    @Transactional(readOnly=true)
    public AnalysisResult sample(AnalysisRequest request,LocalDate start,LocalDate end) {
        var docs=evidence.findByDestinationSlugAndPublishedAtBetweenOrderByPublishedAtDesc(request.destinationId(),start,end);
        if(docs.isEmpty()) return new AnalysisResult("empty",request.destinationId(),request.year(),"이 지역·연도의 검토 자료가 아직 없어요",
                "속초관광수산시장 2026년을 선택하면 검토한 자료 예시를 볼 수 있습니다.","자료 없음",List.of(),List.of(),List.of(),"",null,"");
        var sources=docs.stream().map(d->new Source(d.getId(),"익명 후기 "+d.getId(),d.getSourceType(),d.getPublishedAt().toString(),d.getUrl())).toList();
        var mentions=prices.findByEvidenceIdIn(docs.stream().map(d->d.getId()).toList()).stream().map(p->new Price(p.getItem(),p.getUnit(),p.getAmount(),p.getEvidence().getId(),p.getEvidence().getPublishedAt().toString())).toList();
        var findings=docs.stream().map(d->new Finding(d.getNote(),List.of(d.getId()))).toList();
        return new AnalysisResult("reviewed_sample",request.destinationId(),request.year(),"가격 상승 체감과 구매 만족이 함께 언급돼요",
                "검토한 일부 후기에는 가격이 이전보다 높게 느껴진다는 반응과 구매에 만족했다는 반응이 함께 있습니다. 소수의 글만으로 관광지 전체의 물가 분위기를 판단하기는 어렵습니다.","자료 부족",mentions,findings,sources,
                "글 작성일 기준입니다. 방문일·중량·현재 판매 가격은 확인되지 않았으며, 전체 후기의 대표 표본이 아닙니다. 일반 상권·다른 관광지·지난해와의 비교 자료는 아직 없습니다.","2026-09-30","");
    }
}
