package mtf.com.overture.user;

import jakarta.validation.Valid;
import mtf.com.overture.user.dto.RefreshRequest;
import mtf.com.overture.user.dto.RefreshResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/v1/auth/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        String accessToken = authService.refresh(request.refreshToken());
        return new RefreshResponse(accessToken);
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
