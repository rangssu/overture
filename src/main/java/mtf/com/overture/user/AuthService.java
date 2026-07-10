package mtf.com.overture.user;

import io.jsonwebtoken.Claims;
import mtf.com.overture.core.security.AuthErrorCode;
import mtf.com.overture.core.security.AuthException;
import mtf.com.overture.core.security.JwtProvider;
import mtf.com.overture.core.security.TokenBlacklist;
import mtf.com.overture.user.dto.RefreshResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final JwtProvider jwtProvider;
    private final TokenBlacklist tokenBlacklist;
    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;

    public AuthService(JwtProvider jwtProvider, TokenBlacklist tokenBlacklist, UserRepository userRepository,
                        RefreshTokenStore refreshTokenStore) {
        this.jwtProvider = jwtProvider;
        this.tokenBlacklist = tokenBlacklist;
        this.userRepository = userRepository;
        this.refreshTokenStore = refreshTokenStore;
    }

    public RefreshResponse refresh(String refreshToken) {
        Claims claims = jwtProvider.parseIfValid(refreshToken)
                .filter(jwtProvider::isRefreshToken)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (tokenBlacklist.isBlacklisted(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(claims);

        // role 조회는 Redis 토큰 회전보다 먼저 해야 한다 - 회전 뒤에 실패하면 클라이언트는 방금
        // 폐기된 옛 토큰만 들고 있고 새로 발급된 토큰은 받지 못해 세션이 영구히 막힌다.
        Role role = userRepository.findRoleById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        String newRefreshTokenCandidate = jwtProvider.createRefreshToken(userId);
        String effectiveRefreshToken = refreshTokenStore.rotateIfValid(userId, refreshToken, newRefreshTokenCandidate);
        if (effectiveRefreshToken == null) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String newAccessToken = jwtProvider.createAccessToken(userId, role.name());

        return new RefreshResponse(newAccessToken, effectiveRefreshToken);
    }

    public void logout(Long userId, String accessToken) {
        blacklistIfValid(accessToken);
        blacklistIfValid(refreshTokenStore.get(userId));
        refreshTokenStore.revoke(userId);
    }

    private void blacklistIfValid(String token) {
        if (token == null) {
            return;
        }
        jwtProvider.parseIfValid(token)
                .ifPresent(claims -> tokenBlacklist.blacklist(token, jwtProvider.getRemainingSeconds(claims)));
    }
}
