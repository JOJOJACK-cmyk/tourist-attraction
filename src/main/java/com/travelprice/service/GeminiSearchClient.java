package com.travelprice.service;
import com.fasterxml.jackson.databind.*;
import com.travelprice.api.ApiModels.*;
import com.travelprice.api.ApiException;
import com.travelprice.domain.Destination;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
@Component
public class GeminiSearchClient {
    private final ObjectMapper mapper;private final String apiKey,model,apiBase;
    private final VisitorSearchPlan plan=new VisitorSearchPlan();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final AtomicBoolean busy=new AtomicBoolean();
    public GeminiSearchClient(ObjectMapper mapper,String apiKey,String model){this(mapper,apiKey,model,"https://generativelanguage.googleapis.com");}
    @Autowired public GeminiSearchClient(ObjectMapper mapper,@Value("${app.ai.api-key:}")String apiKey,@Value("${app.ai.model:gemini-2.5-flash}")String model,@Value("${app.ai.api-base-url:https://generativelanguage.googleapis.com}")String apiBase){this.mapper=mapper;this.apiKey=apiKey;this.model=model;this.apiBase=apiBase;}
    public boolean isAvailable(){return apiKey!=null&&!apiKey.isBlank();}
    private record Search(VisitorSearchPlan.Aspect aspect,AnalysisResult result,List<String> executed,String status){}
    public AnalysisResult analyze(Destination destination,AnalysisRequest selection,LocalDate start,LocalDate end){
        if(!isAvailable())throw new ApiException(503,"실시간 웹 검색이 아직 연결되지 않았습니다. 검토 후기를 이용해주세요.");
        if(!model.matches("[a-zA-Z0-9.-]+"))throw new ApiException(503,"AI 모델 설정을 확인해주세요.");
        if(!busy.compareAndSet(false,true))throw new ApiException(429,"다른 관광지의 후기를 검색 중이에요. 잠시 뒤 다시 시도해주세요.");
        try{
            var today=LocalDate.now(ZoneId.of("Asia/Seoul"));var until=end.isAfter(today)?today:end;
            var pending=new ArrayList<CompletableFuture<Search>>();
            for(var aspect:plan.aspects(destination,selection.year())){
                var payload=Map.of("contents",List.of(Map.of("parts",List.of(Map.of("text",plan.prompt(destination,aspect,start,until))))),"tools",List.of(Map.of("google_search",Map.of())),"generationConfig",Map.of("temperature",0.2,"maxOutputTokens",4096));
                var request=HttpRequest.newBuilder(URI.create(apiBase+"/v1beta/models/"+model+":generateContent")).timeout(Duration.ofSeconds(60))
                    .header("Content-Type","application/json").header("x-goog-api-key",apiKey).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
                pending.add(http.sendAsync(request,HttpResponse.BodyHandlers.ofString()).handle((response,error)->{
                    if(error!=null||response.statusCode()!=200)return new Search(aspect,null,List.of(),"failed");
                    JsonNode root;try{root=mapper.readTree(response.body());}catch(Exception e){return new Search(aspect,null,List.of(),"failed");}
                    var queries=new ArrayList<String>();root.path("candidates").path(0).path("groundingMetadata").path("webSearchQueries").forEach(q->{if(q.isTextual()&&!q.asText().isBlank())queries.add(q.asText());});
                    try{return new Search(aspect,parseResponse(root,selection),List.copyOf(queries),"grounded");}
                    catch(ApiException e){return new Search(aspect,null,List.copyOf(queries),"insufficient");}
                }));
            }
            var searches=pending.stream().map(CompletableFuture::join).toList();
            if(searches.stream().allMatch(s->s.status().equals("failed")))throw new ApiException(502,"후기 검색에 연결하지 못했어요. 서버의 키·모델·사용량을 확인해주세요.");
            var sources=new ArrayList<Source>();var byUrl=new HashMap<String,Long>();var aspects=new ArrayList<VisitorAspect>();var coverage=new ArrayList<SearchCoverage>();var suggestions=new StringBuilder();
            for(var search:searches){
                var findings=new ArrayList<Finding>();var mapped=new HashMap<Long,Long>();
                if(search.result()!=null){
                    for(var source:search.result().sources()){long id=byUrl.computeIfAbsent(source.url(),url->{long next=sources.size()+1;sources.add(new Source(next,"익명 출처 "+next,source.type(),null,url));return next;});mapped.put(source.id(),id);}
                    for(var finding:search.result().findings()){var ids=finding.sourceIds().stream().map(mapped::get).filter(Objects::nonNull).distinct().toList();if(!ids.isEmpty())findings.add(new Finding(finding.text(),ids));}
                    if(!search.result().searchSuggestions().isBlank())suggestions.append(search.result().searchSuggestions()).append("\n");
                }
                String summary=findings.isEmpty()?(search.status().equals("failed")?"이 항목의 검색을 완료하지 못했어요.":"이 항목은 판단할 후기가 부족해요."):String.join(" ",findings.stream().limit(3).map(Finding::text).toList());
                aspects.add(new VisitorAspect(search.aspect().key(),search.aspect().label(),summary,List.copyOf(findings),search.status()));
                coverage.add(new SearchCoverage(search.aspect().key(),search.status(),search.aspect().queries(),search.executed(),mapped.values().stream().distinct().toList().size()));
            }
            String summary=String.join(" ",aspects.stream().filter(a->a.status().equals("grounded")).map(a->a.label()+": "+a.findings().get(0).text()).toList());
            return new AnalysisResult("live",selection.destinationId(),selection.year(),destination.getName()+", 비용과 방문 분위기",summary.isBlank()?"분야별로 확인할 방문 후기가 아직 부족해요.":summary,
                "분야별 검색",List.of(),List.of(),List.copyOf(sources),"검색 출처 수는 방문 후기 수나 방문자 수가 아닙니다.",today.toString(),suggestions.toString(),List.copyOf(aspects),List.copyOf(coverage));
        }catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(502,"후기 검색 응답을 처리하지 못했어요.");}finally{busy.set(false);}
    }
    AnalysisResult parseResponse(JsonNode payload,AnalysisRequest selection){
        var candidate=payload.path("candidates").path(0);var text=new StringBuilder();
        candidate.path("content").path("parts").forEach(p->{if(!p.path("thought").asBoolean(false))text.append(p.path("text").asText("")).append('\n');});
        var grounding=candidate.path("groundingMetadata");var sources=new ArrayList<Source>();var chunks=grounding.path("groundingChunks");
        for(int i=0;i<chunks.size();i++){var web=chunks.path(i).path("web");try{var uri=URI.create(web.path("uri").asText());if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)continue;sources.add(new Source(i+1,"익명 출처 "+(i+1),"웹 검색 출처",null,uri.toString()));}catch(IllegalArgumentException ignored){}}
        var validIds=new HashSet<Long>();sources.forEach(s->validIds.add(s.id()));var findings=new ArrayList<Finding>();var claims=new LinkedHashMap<String,LinkedHashSet<Long>>();
        grounding.path("groundingSupports").forEach(s->{var ids=new LinkedHashSet<Long>();s.path("groundingChunkIndices").forEach(i->{if(i.isIntegralNumber()){long id=i.asLong(-2)+1;if(validIds.contains(id))ids.add(id);}});
            var segment=s.path("segment").path("text").asText("").strip();
            if(!segment.isBlank()&&text.toString().contains(segment)&&!ids.isEmpty())claims.computeIfAbsent(segment,k->new LinkedHashSet<>()).addAll(ids);});
        claims.forEach((segment,ids)->findings.add(new Finding(segment,List.copyOf(ids))));
        if(sources.isEmpty()||findings.isEmpty())throw new ApiException(422,"출처가 연결된 방문 경험을 얻지 못했어요.");
        var used=new HashSet<Long>();findings.forEach(f->used.addAll(f.sourceIds()));sources.removeIf(s->!used.contains(s.id()));
        return new AnalysisResult("live",selection.destinationId(),selection.year(),"웹 검색 기반 방문 경험",String.join(" ",findings.stream().map(Finding::text).toList()),"출처 연결",List.of(),List.copyOf(findings),List.copyOf(sources),"",LocalDate.now(ZoneId.of("Asia/Seoul")).toString(),grounding.path("searchEntryPoint").path("renderedContent").asText(""));
    }
}
