package mtf.com.overture.user;

import jakarta.validation.Valid;
import mtf.com.overture.user.dto.UserResponse;
import mtf.com.overture.user.dto.UserUpdateRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/v1/users/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getMe(userId);
    }

    @PatchMapping("/api/v1/users/me")
    public UserResponse updateMe(@AuthenticationPrincipal Long userId, @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateMe(userId, request);
    }
}
