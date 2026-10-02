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
import com.travelprice.api.SocialLoginController;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.client.oidc.userinfo.*;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean UserDetailsService userDetailsService(MemberRepository members){
        return loginId->{var member=members.findByLoginId(loginId).orElseThrow(()->new UsernameNotFoundException("회원 없음"));
            if(member.isSocial())throw new UsernameNotFoundException("소셜 로그인 계정");
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
    @Bean SecurityFilterChain security(HttpSecurity http,SecurityContextRepository contexts,CsrfTokenRepository tokens,
            SocialLoginConfig.Registrations registrations,
            OAuth2UserService<OAuth2UserRequest,OAuth2User> socialOAuthUserService,
            OAuth2UserService<OidcUserRequest,OidcUser> socialOidcUserService) throws Exception {
        http.securityContext(c->c.securityContextRepository(contexts))
            .csrf(c->c.csrfTokenRepository(tokens).ignoringRequestMatchers("/api/analysis"))
            .authorizeHttpRequests(a->a.requestMatchers("/admin/**","/api/reviews/**","/js/local-review.js").hasRole("ADMIN")
                .requestMatchers("/api/chat/**","/ws/chat").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/community/**").permitAll()
                .requestMatchers("/api/community/**").authenticated().anyRequest().permitAll())
            .requestCache(c->c.disable())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"error\":\"로그인이 필요해요.\"}");})
                .accessDeniedHandler((req,res,x)->{res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"error\":\"권한 또는 로그인 상태를 확인해주세요.\"}");}))
            .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->{res.setContentType("application/json");res.getWriter().write("{\"success\":true}");}));
        if(registrations.enabled())http.oauth2Login(o->o.clientRegistrationRepository(registrations)
            .loginPage("/community")
            .userInfoEndpoint(u->u.userService(socialOAuthUserService).oidcUserService(socialOidcUserService))
            .successHandler((req,res,auth)->res.sendRedirect(SocialLoginController.returnPath(req,false)))
            .failureHandler((req,res,x)->res.sendRedirect(SocialLoginController.returnPath(req,true))));
        return http.build();
    }
}
