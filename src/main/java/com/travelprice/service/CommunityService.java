package com.travelprice.service;
import com.travelprice.api.*;
import com.travelprice.api.CommunityModels.*;
import com.travelprice.domain.*;
import com.travelprice.domain.CommunityPost.*;
import com.travelprice.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class CommunityService {
    private final CommunityPostRepository posts;private final CommunityCommentRepository comments;private final DestinationRepository destinations;
    private final MemberService members;private final CommunityPhotoStorage photos;
    public CommunityService(CommunityPostRepository posts,CommunityCommentRepository comments,DestinationRepository destinations,MemberService members,CommunityPhotoStorage photos){this.posts=posts;this.comments=comments;this.destinations=destinations;this.members=members;this.photos=photos;}
    @Transactional(readOnly=true) public PostPage list(String destination,Kind kind,Category category,String q,boolean includeHidden,int page,Authentication auth){
        var viewer=members.current(auth);if(includeHidden && (viewer==null || !viewer.isAdmin()))throw new ApiException(403,"관리자만 숨긴 글을 볼 수 있어요.");
        if(page<0 || page>10000 || (q!=null && q.length()>80))throw new ApiException(400,"검색 조건을 확인해주세요.");
        Specification<CommunityPost> spec=(root,query,cb)->{
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(!includeHidden)predicates.add(cb.isFalse(root.get("hidden")));
            if(destination!=null && !destination.isBlank())predicates.add(cb.equal(root.get("destination").get("slug"),destination));
            if(kind==Kind.GENERAL)predicates.add(root.get("kind").in(Kind.GENERAL,Kind.REVIEW,Kind.REPORT));
            else if(kind!=null)predicates.add(cb.equal(root.get("kind"),kind));
            if(category!=null)predicates.add(cb.equal(root.get("category"),category));
            if(q!=null && !q.isBlank()){
                var word="%"+q.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")),word,'\\'),cb.like(cb.lower(root.get("body")),word,'\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result=posts.findAll(spec,PageRequest.of(page,12,Sort.by(Sort.Direction.DESC,"createdAt","id")));
        return new PostPage(result.getContent().stream().map(p->view(p,viewer,true)).toList(),page,result.getTotalPages(),result.getTotalElements());
    }
    @Transactional(readOnly=true) public PostView detail(Long id,Authentication auth){var viewer=members.current(auth);return view(visible(id,viewer),viewer,false);}
    public PostView create(PostInput input,Authentication auth){
        var author=members.require(auth);var destination=validate(input,author);
        return view(posts.saveAndFlush(new CommunityPost(author,destination,input.kind().boardKind(),input.category(),input.title().trim(),input.body().trim(),input.visitedAt(),input.imageKey())),author,false);
    }
    public PostView update(Long id,PostInput input,Authentication auth){
        var author=members.require(auth);var post=visible(id,author);requireOwner(post,author);var oldImage=post.getImageKey();
        post.update(validate(input,author),input.kind().boardKind(),input.category(),input.title().trim(),input.body().trim(),input.visitedAt(),input.imageKey());
        posts.flush();if(!Objects.equals(oldImage,input.imageKey()))photos.deleteUnused(oldImage);
        return view(post,author,false);
    }
    public void delete(Long id,Authentication auth){var author=members.require(auth);var post=visible(id,author);if(!author.isAdmin())requireOwner(post,author);var image=post.getImageKey();comments.deleteByPostId(id);posts.delete(post);posts.flush();photos.deleteUnused(image);}
    public void visibility(Long id,boolean hidden,Authentication auth){var admin=members.require(auth);if(!admin.isAdmin())throw new ApiException(403,"관리자 권한이 필요해요.");posts.findById(id).orElseThrow(()->new ApiException(404,"글이 없습니다.")).setHidden(hidden);}
    @Transactional(readOnly=true) public List<CommentView> comments(Long id,Authentication auth){
        var viewer=members.current(auth);visible(id,viewer);var list=new ArrayList<>(comments.findTop100ByPostIdOrderByCreatedAtDesc(id));Collections.reverse(list);
        return list.stream().map(c->comment(c,viewer)).toList();
    }
    public CommentView addComment(Long id,CommentInput input,Authentication auth){
        var author=members.require(auth);var post=visible(id,author);if(post.isHidden())throw new ApiException(403,"숨긴 글에는 댓글을 작성할 수 없어요.");
        return comment(comments.saveAndFlush(new CommunityComment(post,author,input.body().trim())),author);
    }
    public void deleteComment(Long postId,Long id,Authentication auth){
        var author=members.require(auth);visible(postId,author);var comment=comments.findById(id).orElseThrow(()->new ApiException(404,"댓글이 없습니다."));
        if(!comment.getPost().getId().equals(postId))throw new ApiException(404,"댓글이 없습니다.");
        if(!author.isAdmin() && !comment.getAuthor().getId().equals(author.getId()))throw new ApiException(403,"본인 댓글만 삭제할 수 있어요.");comments.delete(comment);
    }
    private Destination validate(PostInput input,Member author){
        if(input.title().trim().length()<2 || input.body().trim().length()<5)throw new ApiException(400,"제목과 내용을 조금 더 작성해주세요.");
        if(input.visitedAt()!=null && input.visitedAt().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))))throw new ApiException(400,"방문일을 확인해주세요.");
        photos.checkOwner(input.imageKey(),author);
        if(input.destinationId()==null || input.destinationId().isBlank())return null;
        return destinations.findBySlug(input.destinationId().trim()).orElseThrow(()->new ApiException(400,"등록된 관광지 태그를 선택해주세요."));
    }
    private CommunityPost visible(Long id,Member viewer){
        var post=posts.findById(id).orElseThrow(()->new ApiException(404,"글이 없습니다."));
        if(post.isHidden() && (viewer==null || (!viewer.isAdmin() && !post.getAuthor().getId().equals(viewer.getId()))))throw new ApiException(404,"글이 없습니다.");return post;
    }
    private void requireOwner(CommunityPost post,Member member){if(!post.getAuthor().getId().equals(member.getId()))throw new ApiException(403,"본인 글만 수정·삭제할 수 있어요.");}
    private PostView view(CommunityPost p,Member viewer,boolean preview){
        boolean mine=viewer!=null && p.getAuthor().getId().equals(viewer.getId());var body=p.getBody();if(preview && body.length()>140)body=body.substring(0,140)+"…";
        return new PostView(p.getId(),p.getDestination()==null?null:p.getDestination().getSlug(),p.getDestination()==null?null:p.getDestination().getName(),p.getKind().boardKind(),p.getCategory(),p.getTitle(),body,p.getVisitedAt(),p.getImageKey()==null?null:"/api/community/images/"+p.getImageKey(),"익명",mine,p.isHidden(),viewer!=null&&viewer.isAdmin(),p.getCreatedAt(),p.getUpdatedAt());
    }
    private CommentView comment(CommunityComment c,Member viewer){return new CommentView(c.getId(),c.getBody(),"익명",viewer!=null && (viewer.isAdmin() || c.getAuthor().getId().equals(viewer.getId())),c.getCreatedAt());}
}
