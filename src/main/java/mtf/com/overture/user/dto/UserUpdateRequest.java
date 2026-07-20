package mtf.com.overture.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]{2,10}$", message = "닉네임은 한글/영문/숫자 2~10자여야 합니다.")
        String nickname,
        @Size(max = 255, message = "프로필 이미지 URL은 255자를 넘을 수 없습니다.")
        @Pattern(regexp = "^https?://.+", message = "프로필 이미지 URL은 http(s)로 시작해야 합니다.")
        String profileImageUrl
) {
}
