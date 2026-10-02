package com.travelprice.service;
import com.travelprice.domain.Destination;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
public class VisitorSearchPlan {
    public record Aspect(String key,String label,String focus,List<String> queries){}
    private final String system;
    public VisitorSearchPlan(){try(var in=new ClassPathResource("prompts/visitor-search-system.txt").getInputStream()){system=new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("방문 경험 검색 프롬프트를 읽지 못했습니다.",e);}}
    public List<Aspect> aspects(Destination d,int year){
        var definitions=List.of(new String[]{"food_cost","식비","가격 후기"},new String[]{"lodging_cost","시장 주변 숙박비","숙소 요금 후기"},new String[]{"service","서비스·응대","응대 후기"},new String[]{"crowding","혼잡·대기","방문 대기 후기"});
        var aliases=new LinkedHashSet<String>();aliases.add(d.getName());if("sokcho".equals(d.getSlug()))aliases.add("속초시장");if(d.getAliases()!=null)Arrays.stream(d.getAliases().split(",")).map(String::strip).filter(s->!s.isBlank()).forEach(aliases::add);
        String names="("+String.join(" OR ",aliases.stream().map(s->"\""+s.replace("\"","")+"\"").toList())+")";
        return definitions.stream().map(a->{var queries=new ArrayList<String>();for(String domain:List.of("gall.dcinside.com","fmkorea.com","theqoo.net"))queries.add(names+" "+a[2]+" "+year+" site:"+domain);queries.add(names+" 다녀온 후기 "+year);return new Aspect(a[0],a[1],a[2],List.copyOf(queries));}).toList();
    }
    public String prompt(Destination d,Aspect aspect,LocalDate start,LocalDate end){return system+"\n대상: "+d.getName()+"\n별칭: "+d.getAliases()+"\n제외할 장소: "+d.getExcludedPlaces()+"\n작성 기간: "+start+" ~ "+end+"\n이번 조사 분야: "+aspect.label()+"\n요청 검색어(추가 별칭/긍정/부정 검색 허용):\n"+String.join("\n",aspect.queries());}
}
