package mtf.com.overture.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByOauthProviderAndOauthProviderId_returns_saved_user() {
        User user = User.builder()
                .email("test@kakao.com")
                .nickname("테스터")
                .profileImageUrl("http://example.com/profile.png")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("12345")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(user);

        Optional<User> found = userRepository.findByOauthProviderAndOauthProviderId(OauthProvider.KAKAO, "12345");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@kakao.com");
    }

    @Test
    void findByOauthProviderAndOauthProviderId_returns_empty_when_not_found() {
        Optional<User> found = userRepository.findByOauthProviderAndOauthProviderId(OauthProvider.KAKAO, "no-such-id");

        assertThat(found).isEmpty();
    }

    @Test
    void findRoleById_returns_role_for_active_user() {
        User user = userRepository.save(User.builder()
                .email("active@kakao.com")
                .nickname("활성유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("active-1")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        Optional<Role> role = userRepository.findRoleById(user.getId());

        assertThat(role).contains(Role.USER);
    }

    @Test
    void findRoleById_returns_empty_for_withdrawn_user() {
        User user = userRepository.save(User.builder()
                .email("withdrawn@kakao.com")
                .nickname("탈퇴유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("withdrawn-1")
                .role(Role.USER)
                .status(UserStatus.WITHDRAWN)
                .createdAt(LocalDateTime.now())
                .build());

        Optional<Role> role = userRepository.findRoleById(user.getId());

        assertThat(role).isEmpty();
    }
}
