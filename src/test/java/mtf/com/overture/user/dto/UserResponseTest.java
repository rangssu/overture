package mtf.com.overture.user.dto;
import mtf.com.overture.user.entity.User;
import mtf.com.overture.user.enums.UserStatus;
import mtf.com.overture.user.enums.OauthProvider;
import mtf.com.overture.user.enums.Role;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTest {

    @Test
    void from_maps_all_fields_from_the_user_entity() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 13, 10, 0);
        User user = User.builder()
                .email("test@kakao.com")
                .nickname("테스터")
                .profileImageUrl("http://example.com/profile.png")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("1")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(createdAt)
                .build();

        UserResponse response = UserResponse.from(user);

        assertThat(response.id()).isNull(); // 아직 영속화 전이라 id는 없다
        assertThat(response.email()).isEqualTo("test@kakao.com");
        assertThat(response.nickname()).isEqualTo("테스터");
        assertThat(response.profileImageUrl()).isEqualTo("http://example.com/profile.png");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
