package mtf.com.overture.user.oauth;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoUserInfoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returns_all_fields_when_full_consent_given() {
        Map<String, Object> attributes = kakaoAttributesWithProfile("111", "user@kakao.com", "닉네임", "http://img/1.png");

        KakaoUserInfo userInfo = new KakaoUserInfo(attributes, objectMapper);

        assertThat(userInfo.getProviderId()).isEqualTo("111");
        assertThat(userInfo.getEmail()).isEqualTo("user@kakao.com");
        assertThat(userInfo.getNickname()).isEqualTo("닉네임");
        assertThat(userInfo.getProfileImageUrl()).isEqualTo("http://img/1.png");
    }

    @Test
    void returns_null_nickname_and_image_when_profile_consent_declined() {
        Map<String, Object> account = new HashMap<>();
        account.put("email", "user@kakao.com");
        // "profile" key intentionally absent - user declined optional consent

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 222L);
        attributes.put("kakao_account", account);

        KakaoUserInfo userInfo = new KakaoUserInfo(attributes, objectMapper);

        assertThat(userInfo.getProviderId()).isEqualTo("222");
        assertThat(userInfo.getEmail()).isEqualTo("user@kakao.com");
        assertThat(userInfo.getNickname()).isNull();
        assertThat(userInfo.getProfileImageUrl()).isNull();
    }

    @Test
    void returns_null_email_and_profile_fields_when_kakao_account_missing() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 333L);
        // "kakao_account" key intentionally absent

        KakaoUserInfo userInfo = new KakaoUserInfo(attributes, objectMapper);

        assertThat(userInfo.getProviderId()).isEqualTo("333");
        assertThat(userInfo.getEmail()).isNull();
        assertThat(userInfo.getNickname()).isNull();
        assertThat(userInfo.getProfileImageUrl()).isNull();
    }

    @Test
    void throws_when_id_missing() {
        Map<String, Object> attributes = new HashMap<>();
        // "id" key intentionally absent - malformed/unexpected kakao response

        KakaoUserInfo userInfo = new KakaoUserInfo(attributes, objectMapper);

        assertThatThrownBy(userInfo::getProviderId)
                .isInstanceOf(IllegalStateException.class);
    }

    private Map<String, Object> kakaoAttributesWithProfile(String id, String email, String nickname, String imageUrl) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", nickname);
        profile.put("profile_image_url", imageUrl);

        Map<String, Object> account = new HashMap<>();
        account.put("email", email);
        account.put("profile", profile);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", Long.valueOf(id));
        attributes.put("kakao_account", account);
        return attributes;
    }
}
