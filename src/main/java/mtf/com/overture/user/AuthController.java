package mtf.com.overture.user;

import jakarta.validation.Valid;
import mtf.com.overture.user.dto.ExchangeRequest;
import mtf.com.overture.user.dto.RefreshRequest;
import mtf.com.overture.user.dto.RefreshResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/api/v1/auth/exchange")
    public RefreshResponse exchange(@Valid @RequestBody ExchangeRequest request) {
        return authService.exchange(request.code());
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId, Authentication authentication) {
        authService.logout(userId, (String) authentication.getCredentials());
        return ResponseEntity.noContent().build();
    }
}
