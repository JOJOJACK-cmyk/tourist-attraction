package com.travelprice.config;
import com.travelprice.domain.Member;
import com.travelprice.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class AdminAccountInitializer implements ApplicationRunner {
    private final MemberRepository members;private final PasswordEncoder encoder;private final String loginId,password;
    public AdminAccountInitializer(MemberRepository members,PasswordEncoder encoder,@Value("${APP_ADMIN_LOGIN:}")String loginId,@Value("${APP_ADMIN_PASSWORD:}")String password){this.members=members;this.encoder=encoder;this.loginId=loginId;this.password=password;}
    @Override public void run(ApplicationArguments args){
        if(loginId.isBlank() && password.isBlank())return;
        if(!loginId.matches("[a-z0-9_]{3,30}") || password.length()<8 || password.length()>64 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalStateException("관리자 아이디/비밀번호 환경변수 형식을 확인해주세요.");
        var existing=members.findByLoginId(loginId);
        if(existing.isPresent()){if(!existing.get().isAdmin())throw new IllegalStateException("관리자 생성에 기존 일반회원 아이디를 사용할 수 없습니다.");return;}
        members.save(new Member(loginId,encoder.encode(password),Member.Role.ADMIN));
    }
}
