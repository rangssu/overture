package mtf.com.overture.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void resolveUser_falls_back_to_existing_user_when_concurrent_insert_races() {
        User winner = userRepository.save(User.builder()
                .email("race@kakao.com")
                .nickname("레이스유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("777")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // 두 요청이 동시에 find에서 empty를 본 뒤, 진 쪽의 save()가 unique 제약 위반으로 실패하는 상황을 재현한다.
        UserRepository racyRepository = mock(UserRepository.class);
        when(racyRepository.findByOauthProviderAndOauthProviderId(OauthProvider.KAKAO, "777"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(racyRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violated"));

        CustomOAuth2UserService racyService = new CustomOAuth2UserService(racyRepository, objectMapper);

        User resolved = racyService.resolveUser("kakao", kakaoAttributes("777", "race@kakao.com", "레이스유저"));

        assertThat(resolved.getId()).isEqualTo(winner.getId());
    }

    @Test
    void resolveUser_rejects_login_when_user_status_not_active() {
        userRepository.save(User.builder()
                .email("withdrawn@kakao.com")
                .nickname("탈퇴유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("888")
                .role(Role.USER)
                .status(UserStatus.WITHDRAWN)
                .createdAt(LocalDateTime.now())
                .build());

        service = new CustomOAuth2UserService(userRepository, objectMapper);

        assertThatThrownBy(() -> service.resolveUser("kakao", kakaoAttributes("888", "withdrawn@kakao.com", "탈퇴유저")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void resolveUser_rejects_unsupported_registration_id() {
        // IllegalArgumentException처럼 unchecked exception을 던지면 OAuth2LoginAuthenticationFilter가
        // AuthenticationException으로 인식하지 못해 OAuth2FailureHandler를 우회하고 500으로 노출된다.
        service = new CustomOAuth2UserService(userRepository, objectMapper);

        assertThatThrownBy(() -> service.resolveUser("naver", kakaoAttributes("555", "n@naver.com", "네이버유저")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
