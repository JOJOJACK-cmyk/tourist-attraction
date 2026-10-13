package com.travelprice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NaverLoginClientTest {
    @Test void nestedNaverIdIsRequiredAndOptionalProfileFieldsDoNotChangeIdentity(){
        assertThat(SocialLoginConfig.naverSubject(Map.of("resultcode","00","response",Map.of("id","stable-id","email","first@example.com","name","첫 이름")))).isEqualTo("stable-id");
        assertThat(SocialLoginConfig.naverSubject(Map.of("resultcode","00","response",Map.of("id","stable-id","email","other@example.com","name","다른 이름")))).isEqualTo("stable-id");
        assertThatThrownBy(()->SocialLoginConfig.naverSubject(Map.of("resultcode","024","response",Map.of("id","stable-id")))).isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(()->SocialLoginConfig.naverSubject(Map.of("resultcode","00","response",Map.of("email","only-email@example.com")))).isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(()->SocialLoginConfig.naverSubject(Map.of("resultcode","00","response",Map.of("id",123)))).isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(()->SocialLoginConfig.naverSubject(Map.of("resultcode","00","response","not-an-object"))).isInstanceOf(OAuth2AuthenticationException.class);
    }
    @Test void tokenExchangeIncludesNaverStateWithoutAddingItToGoogleRequests(){
        var registrations=new SocialLoginConfig.Registrations(new MockEnvironment().withProperty("NAVER_CLIENT_ID","naver-client").withProperty("NAVER_CLIENT_SECRET","naver-secret").withProperty("GOOGLE_CLIENT_ID","google-client").withProperty("GOOGLE_CLIENT_SECRET","google-secret"));
        var builder=RestClient.builder().messageConverters(converters->{converters.clear();converters.add(new FormHttpMessageConverter());converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());});
        var server=MockRestServiceServer.bindTo(builder).build();var client=new SocialLoginConfig().socialTokenResponseClient();client.setRestClient(builder.build());
        var grants=new ArrayList<OAuth2AuthorizationCodeGrantRequest>();
        for(String provider:new String[]{"naver","google"}){
            var registration=registrations.findByRegistrationId(provider);var redirect="http://localhost:8080/login/oauth2/code/"+provider;
            var authorization=OAuth2AuthorizationRequest.authorizationCode().authorizationUri(registration.getProviderDetails().getAuthorizationUri()).clientId(registration.getClientId()).redirectUri(redirect).state("verified-state").build();
            var response=OAuth2AuthorizationResponse.success("auth-code").redirectUri(redirect).state("verified-state").build();
            server.expect(requestTo(registration.getProviderDetails().getTokenUri())).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(provider.equals("naver")?containsString("state=verified-state"):not(containsString("state="))))
                .andExpect(content().string(containsString("code=auth-code")))
                .andRespond(withSuccess("{\"access_token\":\"mock-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",MediaType.APPLICATION_JSON));
            grants.add(new OAuth2AuthorizationCodeGrantRequest(registration,new OAuth2AuthorizationExchange(authorization,response)));
        }
        for(var grant:grants)assertThat(client.getTokenResponse(grant).getAccessToken().getTokenValue()).isEqualTo("mock-access-token");
        server.verify();
    }
    @Test void incompleteConfigurationDoesNotEnableProvidersOrExposeLegacyKakao(){
        var partial=new SocialLoginConfig.Registrations(new MockEnvironment().withProperty("NAVER_CLIENT_ID","id-only").withProperty("KAKAO_CLIENT_ID","legacy").withProperty("KAKAO_CLIENT_SECRET","legacy"));
        assertThat(partial.available("naver")).isFalse();assertThat(partial.available("kakao")).isFalse();assertThat(partial.enabled()).isFalse();
    }
}
