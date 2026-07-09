package mtf.com.overture.user;

import mtf.com.overture.core.security.AuthErrorCode;
import mtf.com.overture.core.security.AuthException;
import mtf.com.overture.core.security.JwtProvider;
import mtf.com.overture.user.dto.RefreshResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthService {

    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;

    public AuthService(JwtProvider jwtProvider, StringRedisTemplate redisTemplate, UserRepository userRepository,
                        RefreshTokenStore refreshTokenStore) {
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.refreshTokenStore = refreshTokenStore;
    }

    public RefreshResponse refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + refreshToken))) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(refreshToken);
        String newRefreshTokenCandidate = jwtProvider.createRefreshToken(userId);

        String effectiveRefreshToken = refreshTokenStore.rotateIfValid(userId, refreshToken, newRefreshTokenCandidate);
        if (effectiveRefreshToken == null) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Role role = userRepository.findRoleById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        String newAccessToken = jwtProvider.createAccessToken(userId, role.name());

        return new RefreshResponse(newAccessToken, effectiveRefreshToken);
    }

    public void logout(Long userId) {
        String storedToken = refreshTokenStore.get(userId);
        if (storedToken != null && jwtProvider.validateToken(storedToken)) {
            long remainingSeconds = jwtProvider.getRemainingSeconds(storedToken);
            if (remainingSeconds > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + storedToken, "1", Duration.ofSeconds(remainingSeconds));
            }
        }
        refreshTokenStore.revoke(userId);
    }
}
