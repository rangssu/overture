package mtf.com.overture.user.repository;
import mtf.com.overture.user.entity.User;
import mtf.com.overture.user.enums.UserStatus;
import mtf.com.overture.user.enums.OauthProvider;
import mtf.com.overture.user.enums.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void nickname_can_be_null_for_a_user_pending_onboarding() {
        User user = userRepository.save(User.builder()
                .email("pending@kakao.com")
                .nickname(null)
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("pending-1")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        Optional<User> found = userRepository.findById(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isNull();
    }

    @Test
    void multiple_users_can_have_a_null_nickname_at_the_same_time() {
        userRepository.saveAndFlush(User.builder()
                .email("pending1@kakao.com")
                .nickname(null)
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("pending-a")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        User second = userRepository.saveAndFlush(User.builder()
                .email("pending2@kakao.com")
                .nickname(null)
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("pending-b")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        assertThat(second.getId()).isNotNull();
    }

    @Test
    void saving_a_duplicate_nickname_violates_the_unique_constraint() {
        userRepository.saveAndFlush(User.builder()
                .email("first@kakao.com")
                .nickname("중복닉네임")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("dup-1")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        User second = User.builder()
                .email("second@kakao.com")
                .nickname("중복닉네임")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("dup-2")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByNicknameAndIdNot_returns_true_only_for_another_user_with_that_nickname() {
        User owner = userRepository.saveAndFlush(User.builder()
                .email("owner@kakao.com")
                .nickname("점유자")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("owner-1")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
        User other = userRepository.saveAndFlush(User.builder()
                .email("other@kakao.com")
                .nickname(null)
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("other-1")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        assertThat(userRepository.existsByNicknameAndIdNot("점유자", other.getId())).isTrue();
        assertThat(userRepository.existsByNicknameAndIdNot("점유자", owner.getId())).isFalse();
    }
}
