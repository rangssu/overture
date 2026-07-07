package mtf.com.overture.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CustomOAuth2UserServiceTest {

    @Autowired
    private UserRepository userRepository;

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
        service = new CustomOAuth2UserService(userRepository);

        User user = service.resolveUser(kakaoAttributes("111", "new@kakao.com", "새유저"));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("new@kakao.com");
        assertThat(user.getNickname()).isEqualTo("새유저");
        assertThat(user.getOauthProvider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(user.getOauthProviderId()).isEqualTo("111");
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void resolveUser_returns_existing_user_when_already_registered() {
        service = new CustomOAuth2UserService(userRepository);
        User first = service.resolveUser(kakaoAttributes("222", "exist@kakao.com", "기존유저"));

        User second = service.resolveUser(kakaoAttributes("222", "exist@kakao.com", "기존유저"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userRepository.count()).isEqualTo(1);
    }
}
