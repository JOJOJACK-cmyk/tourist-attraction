package com.travelprice.service;
import com.travelprice.api.ApiException;
import com.travelprice.domain.Member;
import com.travelprice.repository.MemberRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Service
public class MemberService {
    private final MemberRepository members;private final PasswordEncoder encoder;
    public MemberService(MemberRepository members,PasswordEncoder encoder){this.members=members;this.encoder=encoder;}
    @Transactional public void signup(String loginId,String password){
        if(password.getBytes(StandardCharsets.UTF_8).length>72)throw new ApiException(400,"비밀번호가 너무 길어요.");
        if(members.existsByLoginId(loginId))throw new ApiException(409,"이미 사용 중인 아이디예요.");
        members.saveAndFlush(new Member(loginId,encoder.encode(password),Member.Role.MEMBER));
    }
    public Member current(Authentication auth){
        if(auth==null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)return null;
        return members.findByLoginId(auth.getName()).orElse(null);
    }
    public Member require(Authentication auth){var member=current(auth);if(member==null)throw new ApiException(401,"로그인이 필요해요.");return member;}
}
