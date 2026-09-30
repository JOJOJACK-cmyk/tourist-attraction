package com.travelprice.api;

import com.travelprice.api.ApiModels.*;
import com.travelprice.repository.DestinationRepository;
import com.travelprice.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class AnalysisController {
    private final DestinationRepository destinations;
    private final AnalysisService analysis;
    private final GeminiSearchClient ai;
    private final Map<String,Long> requestTimes=new ConcurrentHashMap<>();
    public AnalysisController(DestinationRepository destinations,AnalysisService analysis,GeminiSearchClient ai) {
        this.destinations=destinations; this.analysis=analysis; this.ai=ai;
    }
    @GetMapping("/destinations")
    public List<DestinationDto> destinations() {
        return destinations.findAll().stream().map(d->new DestinationDto(d.getSlug(),d.getName(),d.getRegion(),d.getCategories())).toList();
    }
    @GetMapping("/status")
    public Map<String,Boolean> status() { return Map.of("liveAvailable",ai.isAvailable()); }
    @PostMapping("/analysis")
    public AnalysisResult analyze(@Valid @RequestBody AnalysisRequest selection,HttpServletRequest request) {
        if("live".equals(selection.mode())) {
            if(!ai.isAvailable()) throw new ApiException(503,"실시간 웹 검색이 아직 연결되지 않았습니다. 검토 자료 예시를 이용해주세요.");
            var origin=request.getHeader("Origin");
            if(origin!=null) {
                try {
                    var originUri=URI.create(origin);
                    var serverUri=URI.create(request.getRequestURL().toString());
                    if(!Objects.equals(originUri.getScheme(),serverUri.getScheme()) || !Objects.equals(originUri.getRawAuthority(),serverUri.getRawAuthority())) throw new ApiException(403,"다른 사이트에서 보낸 검색 요청은 허용되지 않습니다.");
                } catch(IllegalArgumentException e) { throw new ApiException(403,"검색 요청의 출처를 확인할 수 없습니다."); }
            }
            var address=request.getRemoteAddr(); long now=System.currentTimeMillis();
            if(requestTimes.size()>5000) requestTimes.clear();
            requestTimes.compute(address,(key,last)->{
                if(last!=null && now-last<15000) throw new ApiException(429,"잠시 뒤 다시 검색해주세요.");
                return now;
            });
        }
        return analysis.analyze(selection);
    }
}
