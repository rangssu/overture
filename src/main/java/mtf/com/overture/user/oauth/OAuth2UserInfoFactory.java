package mtf.com.overture.user.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {
    }

    public static OAuth2UserInfo create(String registrationId, Map<String, Object> attributes, ObjectMapper objectMapper) {
        if ("kakao".equalsIgnoreCase(registrationId)) {
            return new KakaoUserInfo(attributes, objectMapper);
        }
        // OAuth2LoginAuthenticationFilter는 AuthenticationException만 OAuth2FailureHandler로 보내므로,
        // unchecked exception을 던지면 리다이렉트 대신 500으로 노출된다.
        throw new OAuth2AuthenticationException(new OAuth2Error(
                "unsupported_provider", "지원하지 않는 OAuth2 provider입니다: " + registrationId, null));
    }
}
