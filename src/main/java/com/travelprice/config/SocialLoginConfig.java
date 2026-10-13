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
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.util.LinkedMultiValueMap;
import java.util.*;

@Configuration
public class SocialLoginConfig {
    public static final class Registrations implements ClientRegistrationRepository,Iterable<ClientRegistration>{
        private final Map<String,ClientRegistration> clients=new LinkedHashMap<>();
        public Registrations(Environment env){
            var naverId=env.getProperty("NAVER_CLIENT_ID","").trim();var naverSecret=env.getProperty("NAVER_CLIENT_SECRET","").trim();
            if(!naverId.isEmpty() && !naverSecret.isEmpty())clients.put("naver",ClientRegistration.withRegistrationId("naver")
                .clientId(naverId).clientSecret(naverSecret).clientName("네이버")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize").tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me").userNameAttributeName("response").build());
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
    @Bean RestClientAuthorizationCodeTokenResponseClient socialTokenResponseClient(){
        var client=new RestClientAuthorizationCodeTokenResponseClient();
        client.addParametersConverter(grant->{var parameters=new LinkedMultiValueMap<String,String>();
            if("naver".equals(grant.getClientRegistration().getRegistrationId()))parameters.add("state",grant.getAuthorizationExchange().getAuthorizationRequest().getState());
            return parameters;
        });return client;
    }
    public static String naverSubject(Map<String,Object> attributes){
        if(!"00".equals(attributes.get("resultcode"))||!(attributes.get("response") instanceof Map<?,?> response)||!(response.get("id") instanceof String id)||id.isBlank()||id.length()>255)
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_naver_identity"));
        return id;
    }
    @Bean OAuth2UserService<OAuth2UserRequest,OAuth2User> socialOAuthUserService(SocialMemberService members){
        var delegate=new DefaultOAuth2UserService();
        return request->{
            var remote=delegate.loadUser(request);var provider=request.getClientRegistration().getRegistrationId();
            if(!"naver".equals(provider))throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_provider"));
            var member=members.resolve(provider,naverSubject(remote.getAttributes()));
            // Only the local identity and role are retained in the Naver session principal.
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
