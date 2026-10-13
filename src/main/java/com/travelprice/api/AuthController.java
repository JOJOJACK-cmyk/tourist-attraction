package com.travelprice.api;
import com.travelprice.service.MemberService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final MemberService members;private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;private final SessionAuthenticationStrategy sessions;
    public AuthController(MemberService members,AuthenticationManager manager,SecurityContextRepository contexts,SessionAuthenticationStrategy sessions){this.members=members;this.manager=manager;this.contexts=contexts;this.sessions=sessions;}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("headerName",token.getHeaderName(),"token",token.getToken());}
    @GetMapping("/me") public Map<String,Object> me(Authentication auth){
        var member=members.current(auth);return member==null?Map.of("authenticated",false):Map.of("authenticated",true,"admin",member.isAdmin(),"loginId",member.isSocial()?switch(member.getSocialProvider()){case "naver"->"네이버 계정";case "google"->"구글 계정";case "kakao"->"카카오 계정";default->"소셜 계정";}:member.getLoginId(),"authProvider",member.isSocial()?member.getSocialProvider():"local","nickname",member.getNickname()==null?"":member.getNickname(),"displayName",member.getDisplayName());
    }
    @PostMapping("/signup") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String,Boolean> signup(@Valid @RequestBody SignupCredentials input){members.signup(input.loginId(),input.password(),input.nickname());return Map.of("success",true);}
    @PatchMapping("/profile") public Map<String,Object> nickname(@Valid @RequestBody NicknameInput input,Authentication auth){members.updateNickname(input.nickname(),auth);return me(auth);}
    @PostMapping("/login") public Map<String,Boolean> login(@Valid @RequestBody Credentials input,HttpServletRequest request,HttpServletResponse response){
        try {
            var auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.loginId(),input.password()));
            sessions.onAuthentication(auth,request,response);
            var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);
            return Map.of("success",true);
        }catch(AuthenticationException e){throw new ApiException(401,"아이디 또는 비밀번호가 맞지 않아요.");}
    }
    public record Credentials(@NotBlank @Pattern(regexp="[a-z0-9_]{3,30}") String loginId,@NotBlank @Size(min=8,max=64) String password){}
    public record SignupCredentials(@NotBlank @Pattern(regexp="[a-z0-9_]{3,30}") String loginId,@NotBlank @Size(min=8,max=64) String password,@Size(max=64) String nickname){}
    public record NicknameInput(@NotBlank @Size(max=64) String nickname){}
}
