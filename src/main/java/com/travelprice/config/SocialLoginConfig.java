package com.travelprice.config;

import com.travelprice.service.SocialMemberService;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.client.oidc.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import java.util.*;

@Configuration
public class SocialLoginConfig {
    public static final class Registrations implements ClientRegistrationRepository,Iterable<ClientRegistration>{
        private final Map<String,ClientRegistration> clients=new LinkedHashMap<>();
        public Registrations(Environment env){
            var kakaoId=env.getProperty("KAKAO_CLIENT_ID","").trim();var kakaoSecret=env.getProperty("KAKAO_CLIENT_SECRET","").trim();
            if(!kakaoId.isEmpty() && !kakaoSecret.isEmpty())clients.put("kakao",ClientRegistration.withRegistrationId("kakao")
                .clientId(kakaoId).clientSecret(kakaoSecret).clientName("카카오")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize").tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me").userNameAttributeName("id").build());
            var googleId=env.getProperty("GOOGLE_CLIENT_ID","").trim();var googleSecret=env.getProperty("GOOGLE_CLIENT_SECRET","").trim();
            if(!googleId.isEmpty() && !googleSecret.isEmpty())clients.put("google",CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(googleId).clientSecret(googleSecret).scope("openid").build());
        }
        @Override public ClientRegistration findByRegistrationId(String id){return clients.get(id);}
        @Override public Iterator<ClientRegistration> iterator(){return clients.values().iterator();}
        public boolean available(String id){return clients.containsKey(id);}
        public boolean enabled(){return !clients.isEmpty();}
    }
    @Bean Registrations socialRegistrations(Environment env){return new Registrations(env);}
    @Bean OAuth2UserService<OAuth2UserRequest,OAuth2User> socialOAuthUserService(SocialMemberService members){
        var delegate=new DefaultOAuth2UserService();
        return request->{
            var remote=delegate.loadUser(request);var provider=request.getClientRegistration().getRegistrationId();
            if(!"kakao".equals(provider))throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_provider"));
            var member=members.resolve(provider,remote.getName());
            // Only the local identity and role are retained in the Kakao session principal.
            return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_"+member.getRole().name())),Map.of("localLoginId",member.getLoginId()),"localLoginId");
        };
    }
    @Bean OAuth2UserService<OidcUserRequest,OidcUser> socialOidcUserService(SocialMemberService members){
        var delegate=new OidcUserService();
        return request->{
            var remote=delegate.loadUser(request);var member=members.resolve(request.getClientRegistration().getRegistrationId(),remote.getSubject());
            var loginId=member.getLoginId();
            return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_"+member.getRole().name())),remote.getIdToken()){
                @Override public String getName(){return loginId;}
            };
        };
    }
}
