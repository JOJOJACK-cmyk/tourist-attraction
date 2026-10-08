package com.travelprice.service;

import com.travelprice.api.*;
import com.travelprice.api.VisitReviewModels.*;
import com.travelprice.domain.*;
import com.travelprice.domain.VisitReview.*;
import com.travelprice.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;

@Service @Transactional
public class VisitReviewService {
    private final VisitReviewRepository reviews;private final DestinationRepository destinations;
    private final MemberService members;private final CommunityPhotoStorage photos;
    public VisitReviewService(VisitReviewRepository reviews,DestinationRepository destinations,MemberService members,CommunityPhotoStorage photos){this.reviews=reviews;this.destinations=destinations;this.members=members;this.photos=photos;}
    @Transactional(readOnly=true) public ReviewPage list(String destination,Integer year,int page,Authentication auth){return page(spec(destination,year,Status.APPROVED,null),page,members.current(auth));}
    @Transactional(readOnly=true) public ReviewPage mine(String destination,int page,Authentication auth){var member=members.require(auth);return page(spec(destination,null,null,member.getId()),page,member);}
    @Transactional(readOnly=true) public ReviewPage queue(String destination,Status status,int page,Authentication auth){var admin=admin(auth);return page(spec(destination,null,status,null),page,admin);}
    private ReviewPage page(Specification<VisitReview> spec,int page,Member viewer){
        if(page<0||page>10000)throw new ApiException(400,"페이지를 확인해주세요.");
        var result=reviews.findAll(spec,PageRequest.of(page,12,Sort.by(Sort.Direction.DESC,"createdAt","id")));
        return new ReviewPage(result.getContent().stream().map(r->view(r,viewer)).toList(),page,result.getTotalPages(),result.getTotalElements());
    }
    @Transactional(readOnly=true) public View detail(Long id,Authentication auth){return view(visible(id,members.current(auth)),members.current(auth));}
    public View create(Input input,Authentication auth){var member=members.require(auth);var review=new VisitReview(member);apply(review,input,member);return view(reviews.saveAndFlush(review),member);}
    public View update(Long id,Input input,Authentication auth){
        var member=members.require(auth);var review=visible(id,member);owner(review,member);revision(review,input.version());
        var old=review.getImageKey();apply(review,input,member);reviews.flush();if(!Objects.equals(old,review.getImageKey()))photos.deleteUnused(old);return view(review,member);
    }
    public void delete(Long id,Long version,Authentication auth){
        var member=members.require(auth);var review=visible(id,member);if(!member.isAdmin())owner(review,member);revision(review,version);
        var image=review.getImageKey();reviews.delete(review);reviews.flush();photos.deleteUnused(image);
    }
    public View moderate(Long id,Decision input,Authentication auth){
        var member=admin(auth);var review=reviews.findById(id).orElseThrow(()->new ApiException(404,"후기가 없습니다."));revision(review,input.version());
        if(input.status()==Status.PENDING)throw new ApiException(400,"승인 또는 반려를 선택해주세요.");
        String note=trim(input.note());if(input.status()==Status.REJECTED&&note.length()<2)throw new ApiException(400,"반려 이유를 작성해주세요.");
        review.moderate(input.status(),note,member);reviews.flush();return view(review,member);
    }
    @Transactional(readOnly=true) public Summary summary(String destination,Integer year){
        destination(destination);var selected=reviews.findAll(spec(destination,year,Status.APPROVED,null),Sort.by(Sort.Direction.DESC,"createdAt","id"));
        var aspects=List.of(aspect(selected,"food_cost","식비",VisitReview::getFoodCost),aspect(selected,"lodging_cost","숙박비",VisitReview::getLodgingCost),aspect(selected,"service","서비스·응대",VisitReview::getService),aspect(selected,"crowding","혼잡·대기",VisitReview::getCrowding));
        long good=selected.stream().filter(r->!r.getGoodPoints().isBlank()||feelings(r).contains(Feeling.SATISFIED)).count();
        long bad=selected.stream().filter(r->!r.getBadPoints().isBlank()||feelings(r).contains(Feeling.UNSATISFIED)).count();
        return new Summary(destination,year,selected.size(),good,bad,aspects);
    }
    private Aspect aspect(List<VisitReview> selected,String key,String label,Function<VisitReview,Feeling> feeling){
        var rated=selected.stream().filter(r->feeling.apply(r)!=Feeling.NOT_USED).toList();
        // Explanatory text belongs to the whole visit; do not attribute it to one category without evidence.
        return new Aspect(key,label,rated.size(),rated.stream().filter(r->feeling.apply(r)==Feeling.SATISFIED).count(),rated.stream().filter(r->feeling.apply(r)==Feeling.UNSATISFIED).count(),rated.stream().filter(r->feeling.apply(r)==Feeling.NEUTRAL).count());
    }
    private List<Feeling> feelings(VisitReview r){return List.of(r.getFoodCost(),r.getLodgingCost(),r.getService(),r.getCrowding());}
    private Specification<VisitReview> spec(String destination,Integer year,Status status,Long author){
        if(destination!=null&&!destination.isBlank())destination(destination);
        if(year!=null&&(year<2000||year>LocalDate.now(ZoneId.of("Asia/Seoul")).getYear()))throw new ApiException(400,"후기 작성 연도를 확인해주세요.");
        return (root,query,cb)->{
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(destination!=null&&!destination.isBlank())predicates.add(cb.equal(root.get("destination").get("slug"),destination));
            if(status!=null)predicates.add(cb.equal(root.get("status"),status));
            if(author!=null)predicates.add(cb.equal(root.get("author").get("id"),author));
            if(year!=null){predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),LocalDate.of(year,1,1).atStartOfDay()));predicates.add(cb.lessThan(root.get("createdAt"),LocalDate.of(year+1,1,1).atStartOfDay()));}
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    private void apply(VisitReview review,Input input,Member member){
        if(input.visitedAt().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")))||input.visitedAt().isBefore(LocalDate.of(2000,1,1)))throw new ApiException(400,"실제로 방문한 날짜를 확인해주세요.");
        if(trim(input.title()).length()<2||Math.max(trim(input.goodPoints()).length(),trim(input.badPoints()).length())<5)throw new ApiException(400,"제목과 만족한 점 또는 아쉬운 점을 5자 이상 작성해주세요.");
        photos.checkOwner(input.imageKey(),member);
        review.update(destination(input.destinationId()),input.visitedAt(),trim(input.title()),trim(input.item()),input.spentWon(),input.waitingMinutes(),input.foodCost(),input.lodgingCost(),input.service(),input.crowding(),trim(input.goodPoints()),trim(input.badPoints()),input.imageKey());
    }
    private Destination destination(String slug){return destinations.findBySlug(slug==null?"":slug).orElseThrow(()->new ApiException(400,"등록된 관광지를 선택해주세요."));}
    private Member admin(Authentication auth){var member=members.require(auth);if(!member.isAdmin())throw new ApiException(403,"관리자 권한이 필요해요.");return member;}
    private VisitReview visible(Long id,Member viewer){var r=reviews.findById(id).orElseThrow(()->new ApiException(404,"후기가 없습니다."));if(r.getStatus()!=Status.APPROVED&&(viewer==null||(!viewer.isAdmin()&&!r.getAuthor().getId().equals(viewer.getId()))))throw new ApiException(404,"후기가 없습니다.");return r;}
    private void owner(VisitReview r,Member member){if(!r.getAuthor().getId().equals(member.getId()))throw new ApiException(403,"본인 후기만 수정할 수 있어요.");}
    private void revision(VisitReview r,Long version){if(version==null||!Objects.equals(r.getVersion(),version))throw new ApiException(409,"후기가 변경됐어요. 목록을 새로고침하고 다시 확인해주세요.");}
    private String trim(String value){return value==null?"":value.trim();}
    private View view(VisitReview r,Member viewer){boolean mine=viewer!=null&&r.getAuthor().getId().equals(viewer.getId());boolean privateView=mine||(viewer!=null&&viewer.isAdmin());return new View(r.getId(),r.getVersion(),r.getDestination().getSlug(),r.getDestination().getName(),r.getVisitedAt(),r.getTitle(),r.getItem(),r.getSpentWon(),r.getWaitingMinutes(),r.getFoodCost(),r.getLodgingCost(),r.getService(),r.getCrowding(),r.getGoodPoints(),r.getBadPoints(),r.getImageKey()==null?null:"/api/community/images/"+r.getImageKey(),"익명",mine,r.getStatus(),privateView?r.getReviewNote():null,r.getCreatedAt(),r.getUpdatedAt(),r.getReviewedAt());}
}
