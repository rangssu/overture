package mtf.com.overture.core.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TokenBlacklist {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklist(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklist(String token, long remainingSeconds) {
        if (remainingSeconds <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", Duration.ofSeconds(remainingSeconds));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
