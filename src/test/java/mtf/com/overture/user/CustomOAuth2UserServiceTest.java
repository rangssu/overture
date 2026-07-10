package mtf.com.overture.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CustomOAuth2UserServiceTest {

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CustomOAuth2UserService service;

    private Map<String, Object> kakaoAttributes(String id, String email, String nickname) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", nickname);
        profile.put("profile_image_url", "http://example.com/" + id + ".png");

        Map<String, Object> account = new HashMap<>();
        account.put("email", email);
        account.put("profile", profile);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", Long.valueOf(id));
        attributes.put("kakao_account", account);
        return attributes;
    }

    @Test
    void resolveUser_creates_new_user_when_not_exists() {
        service = new CustomOAuth2UserService(userRepository, objectMapper);

        User user = service.resolveUser("kakao", kakaoAttributes("111", "new@kakao.com", "새유저"));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("new@kakao.com");
        assertThat(user.getNickname()).isEqualTo("새유저");
        assertThat(user.getOauthProvider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(user.getOauthProviderId()).isEqualTo("111");
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void resolveUser_returns_existing_user_when_already_registered() {
        service = new CustomOAuth2UserService(userRepository, objectMapper);
        User first = service.resolveUser("kakao", kakaoAttributes("222", "exist@kakao.com", "기존유저"));

        User second = service.resolveUser("kakao", kakaoAttributes("222", "exist@kakao.com", "기존유저"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void resolveUser_creates_user_when_email_consent_declined() {
        service = new CustomOAuth2UserService(userRepository, objectMapper);

        User user = service.resolveUser("kakao", kakaoAttributes("666", null, "이메일없는유저"));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getNickname()).isEqualTo("이메일없는유저");
    }

    @Test
    void resolveUser_rejects_login_when_nickname_consent_declined() {
        service = new CustomOAuth2UserService(userRepository, objectMapper);

        Map<String, Object> account = new HashMap<>();
        account.put("email", "new@kakao.com");
        // "profile" key intentionally absent - user declined nickname/image consent

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 333L);
        attributes.put("kakao_account", account);

        assertThatThrownBy(() -> service.resolveUser("kakao", attributes))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(userRepository.count()).isEqualTo(0);
    }

    @Test
    void resolveUser_rejects_login_when_kakao_account_missing_entirely() {
        service = new CustomOAuth2UserService(userRepository, objectMapper);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 444L);
        // "kakao_account" key intentionally absent - no account scope granted

        assertThatThrownBy(() -> service.resolveUser("kakao", attributes))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(userRepository.count()).isEqualTo(0);
    }

    @Test
    void resolveUser_rejects_unsupported_registration_id() {
        service = new CustomOAuth2UserService(userRepository, objectMapper);

        assertThatThrownBy(() -> service.resolveUser("naver", kakaoAttributes("555", "n@naver.com", "네이버유저")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
