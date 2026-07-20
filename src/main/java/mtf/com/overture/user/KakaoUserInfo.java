package mtf.com.overture.user;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public class KakaoUserInfo implements OAuth2UserInfo {

    private final KakaoUserResponse response;

    public KakaoUserInfo(Map<String, Object> attributes, ObjectMapper objectMapper) {
        this.response = objectMapper.convertValue(attributes, KakaoUserResponse.class);
    }

    @Override
    public String getProviderId() {
        Long id = response.id();
        if (id == null) {
            throw new IllegalStateException("카카오 응답에 사용자 id가 없습니다.");
        }
        return String.valueOf(id);
    }

    @Override
    public String getEmail() {
        KakaoUserResponse.KakaoAccount account = account();
        return account != null ? account.email() : null;
    }

    @Override
    public String getNickname() {
        KakaoUserResponse.KakaoAccount.Profile profile = profile();
        return profile != null ? profile.nickname() : null;
    }

    @Override
    public String getProfileImageUrl() {
        KakaoUserResponse.KakaoAccount.Profile profile = profile();
        return profile != null ? profile.profileImageUrl() : null;
    }

    private KakaoUserResponse.KakaoAccount account() {
        return response.kakaoAccount();
    }

    private KakaoUserResponse.KakaoAccount.Profile profile() {
        KakaoUserResponse.KakaoAccount account = account();
        return account != null ? account.profile() : null;
    }
}
