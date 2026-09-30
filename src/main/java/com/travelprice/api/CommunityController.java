package com.travelprice.api;
import com.travelprice.api.CommunityModels.*;
import com.travelprice.domain.CommunityPost.*;
import com.travelprice.service.*;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private final CommunityService community;private final CommunityPhotoStorage photos;private final MemberService members;
    public CommunityController(CommunityService community,CommunityPhotoStorage photos,MemberService members){this.community=community;this.photos=photos;this.members=members;}
    @GetMapping("/posts") public PostPage list(@RequestParam(required=false)String destination,@RequestParam(required=false)Kind kind,@RequestParam(required=false)Category category,@RequestParam(required=false)String q,@RequestParam(defaultValue="false")boolean includeHidden,@RequestParam(defaultValue="0")int page,Authentication auth){return community.list(destination,kind,category,q,includeHidden,page,auth);}
    @GetMapping("/posts/{id}") public PostView detail(@PathVariable Long id,Authentication auth){return community.detail(id,auth);}
    @PostMapping("/posts") @ResponseStatus(HttpStatus.CREATED) public PostView create(@Valid @RequestBody PostInput input,Authentication auth){return community.create(input,auth);}
    @PutMapping("/posts/{id}") public PostView update(@PathVariable Long id,@Valid @RequestBody PostInput input,Authentication auth){return community.update(id,input,auth);}
    @DeleteMapping("/posts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id,Authentication auth){community.delete(id,auth);}
    @PatchMapping("/posts/{id}/visibility") @ResponseStatus(HttpStatus.NO_CONTENT) public void visibility(@PathVariable Long id,@RequestBody VisibilityInput input,Authentication auth){community.visibility(id,input.hidden(),auth);}
    @GetMapping("/posts/{id}/comments") public List<CommentView> comments(@PathVariable Long id,Authentication auth){return community.comments(id,auth);}
    @PostMapping("/posts/{id}/comments") @ResponseStatus(HttpStatus.CREATED) public CommentView comment(@PathVariable Long id,@Valid @RequestBody CommentInput input,Authentication auth){return community.addComment(id,input,auth);}
    @DeleteMapping("/posts/{postId}/comments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteComment(@PathVariable Long postId,@PathVariable Long id,Authentication auth){community.deleteComment(postId,id,auth);}
    @PostMapping("/images") @ResponseStatus(HttpStatus.CREATED) public Map<String,String> upload(@RequestParam("file")MultipartFile file,Authentication auth){var key=photos.upload(file,members.require(auth));return Map.of("imageKey",key,"url","/api/community/images/"+key);}
    @GetMapping("/images/{key}") public ResponseEntity<Resource> photo(@PathVariable String key,Authentication auth){var photo=photos.read(key,members.current(auth));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(photo.contentType())).body(photo.resource());}
}
