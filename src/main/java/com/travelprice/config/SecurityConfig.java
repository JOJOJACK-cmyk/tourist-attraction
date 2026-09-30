package com.travelprice.config;
import com.travelprice.repository.MemberRepository;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean UserDetailsService userDetailsService(MemberRepository members){
        return loginId->{var member=members.findByLoginId(loginId).orElseThrow(()->new UsernameNotFoundException("회원 없음"));
            return User.withUsername(member.getLoginId()).password(member.getPasswordHash()).roles(member.getRole().name()).build();};
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users,PasswordEncoder encoder){
        var provider=new DaoAuthenticationProvider(users);provider.setPasswordEncoder(encoder);return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository(){return new HttpSessionSecurityContextRepository();}
    @Bean CsrfTokenRepository csrfTokenRepository(){return new HttpSessionCsrfTokenRepository();}
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository tokens){
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(tokens)));
    }
    @Bean SecurityFilterChain security(HttpSecurity http,SecurityContextRepository contexts,CsrfTokenRepository tokens) throws Exception {
        http.securityContext(c->c.securityContextRepository(contexts))
            .csrf(c->c.csrfTokenRepository(tokens).ignoringRequestMatchers("/api/analysis","/api/reviews/extract"))
            .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/api/community/**").permitAll()
                .requestMatchers("/api/community/**").authenticated().anyRequest().permitAll())
            .requestCache(c->c.disable())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"error\":\"로그인이 필요해요.\"}");})
                .accessDeniedHandler((req,res,x)->{res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"error\":\"권한 또는 로그인 상태를 확인해주세요.\"}");}))
            .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->{res.setContentType("application/json");res.getWriter().write("{\"success\":true}");}));
        return http.build();
    }
}
