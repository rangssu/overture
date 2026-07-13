package mtf.com.overture.user;

import mtf.com.overture.core.security.AuthException;
import mtf.com.overture.user.dto.UserResponse;
import mtf.com.overture.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("profile-" + System.nanoTime() + "@kakao.com")
                .nickname(null)
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("profile-" + System.nanoTime())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
    }

    @Test
    void getMe_returns_the_users_profile() {
        UserResponse response = userService.getMe(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.nickname()).isNull();
    }

    @Test
    void getMe_throws_when_user_is_withdrawn() {
        User withdrawn = userRepository.save(User.builder()
                .email("withdrawn-" + System.nanoTime() + "@kakao.com")
                .nickname("탈퇴예정")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("withdrawn-" + System.nanoTime())
                .role(Role.USER)
                .status(UserStatus.WITHDRAWN)
                .createdAt(LocalDateTime.now())
                .build());

        try {
            assertThatThrownBy(() -> userService.getMe(withdrawn.getId()))
                    .isInstanceOf(AuthException.class);
        } finally {
            userRepository.deleteById(withdrawn.getId());
        }
    }

    @Test
    void updateMe_sets_the_nickname_when_it_is_available() {
        UserResponse response = userService.updateMe(userId, new UserUpdateRequest("새닉네임", null));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(userRepository.findById(userId).orElseThrow().getNickname()).isEqualTo("새닉네임");
    }

    @Test
    void updateMe_sets_the_profile_image_url() {
        UserResponse response = userService.updateMe(userId, new UserUpdateRequest(null, "http://example.com/new.png"));

        assertThat(response.profileImageUrl()).isEqualTo("http://example.com/new.png");
    }

    @Test
    void updateMe_rejects_a_nickname_already_taken_by_another_user() {
        userRepository.save(User.builder()
                .email("owner-" + System.nanoTime() + "@kakao.com")
                .nickname("선점닉네임")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("owner-" + System.nanoTime())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        assertThatThrownBy(() -> userService.updateMe(userId, new UserUpdateRequest("선점닉네임", null)))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.NICKNAME_DUPLICATE));
    }

    @Test
    void updateMe_allows_keeping_your_own_current_nickname() {
        userService.updateMe(userId, new UserUpdateRequest("내닉네임", null));

        UserResponse response = userService.updateMe(userId, new UserUpdateRequest("내닉네임", null));

        assertThat(response.nickname()).isEqualTo("내닉네임");
    }

    @Test
    void updateMe_rejects_a_request_with_no_fields_set() {
        assertThatThrownBy(() -> userService.updateMe(userId, new UserUpdateRequest(null, null)))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.NO_FIELDS_TO_UPDATE));
    }
}
