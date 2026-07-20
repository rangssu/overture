package mtf.com.overture.user.dto;
import mtf.com.overture.user.entity.User;


import java.time.LocalDateTime;

public record UserResponse(Long id, String email, String nickname, String profileImageUrl,
                            String role, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(),
                user.getProfileImageUrl(), user.getRole().name(), user.getCreatedAt());
    }
}
