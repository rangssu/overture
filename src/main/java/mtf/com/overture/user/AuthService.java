package mtf.com.overture.user;

import mtf.com.overture.core.security.AuthErrorCode;
import mtf.com.overture.core.security.AuthException;
import mtf.com.overture.core.security.JwtProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    public AuthService(JwtProvider jwtProvider, StringRedisTemplate redisTemplate) {
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
    }

    public String refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + refreshToken))) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(refreshToken);
        String storedToken = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        if (!Objects.equals(storedToken, refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        return jwtProvider.createAccessToken(userId, "USER");
    }

    public void logout(Long userId) {
        String storedToken = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        if (storedToken != null && jwtProvider.validateToken(storedToken)) {
            long remainingSeconds = jwtProvider.getRemainingSeconds(storedToken);
            if (remainingSeconds > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + storedToken, "1", Duration.ofSeconds(remainingSeconds));
            }
        }
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
    }
}
