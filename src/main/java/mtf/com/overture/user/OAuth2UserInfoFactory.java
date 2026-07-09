package mtf.com.overture.user;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {
    }

    public static OAuth2UserInfo create(String registrationId, Map<String, Object> attributes, ObjectMapper objectMapper) {
        if ("kakao".equalsIgnoreCase(registrationId)) {
            return new KakaoUserInfo(attributes, objectMapper);
        }
        throw new IllegalArgumentException("지원하지 않는 OAuth2 provider입니다: " + registrationId);
    }
}
