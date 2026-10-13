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
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class MemberService {
    private final MemberRepository members;private final PasswordEncoder encoder;
    public MemberService(MemberRepository members,PasswordEncoder encoder){this.members=members;this.encoder=encoder;}
    @Transactional public void signup(String loginId,String password){
        signup(loginId,password,null);
    }
    @Transactional public void signup(String loginId,String password,String nickname){
        if(password.getBytes(StandardCharsets.UTF_8).length>72)throw new ApiException(400,"비밀번호가 너무 길어요.");
        if(members.existsByLoginId(loginId))throw new ApiException(409,"이미 사용 중인 아이디예요.");
        var member=new Member(loginId,encoder.encode(password),Member.Role.MEMBER);
        if(nickname!=null&&!nickname.isBlank())applyNickname(member,nickname);
        members.saveAndFlush(member);
    }
    @Transactional public void updateNickname(String nickname,Authentication auth){
        var member=require(auth);applyNickname(member,nickname);
        try{members.saveAndFlush(member);}catch(DataIntegrityViolationException duplicate){throw new ApiException(409,"이미 사용 중인 닉네임이에요.");}
    }
    private void applyNickname(Member member,String value){
        var nickname=Normalizer.normalize(value.trim(),Normalizer.Form.NFKC);
        if(!nickname.matches("[가-힣A-Za-z0-9_]{2,16}"))throw new ApiException(400,"닉네임은 한글·영문·숫자·밑줄 2~16자로 입력해주세요.");
        var key=nickname.toLowerCase(Locale.ROOT);
        if(key.equals("익명")||(!member.isAdmin()&&Set.of("admin","administrator","관리자","운영자","여행온도").contains(key)))throw new ApiException(400,"이 닉네임은 사용할 수 없어요.");
        if(member.getId()==null?members.existsByNicknameKey(key):members.existsByNicknameKeyAndIdNot(key,member.getId()))throw new ApiException(409,"이미 사용 중인 닉네임이에요.");
        member.setNickname(nickname,key);
    }
    public Member current(Authentication auth){
        if(auth==null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)return null;
        return members.findByLoginId(auth.getName()).orElse(null);
    }
    public Member require(Authentication auth){var member=current(auth);if(member==null)throw new ApiException(401,"로그인이 필요해요.");return member;}
}
