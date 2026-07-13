package mtf.com.overture.user;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OAuthExchangeCodeStore {

    private static final String KEY_PREFIX = "oauth_exchange:";
    private static final Duration TTL = Duration.ofSeconds(30);
    // JWT compact 직렬화는 세그먼트가 [A-Za-z0-9_-]이고 '.'으로만 구분되므로,
    // ':'는 토큰 값 안에 절대 나타나지 않아 구분자로 안전하다.
    private static final String DELIMITER = ":";

    // GET+DEL을 원자적으로 묶어 1회용 코드를 보장한다. Redis 6.2+의 GETDEL 대신 Lua 스크립트를
    // 쓰는 이유는 GETDEL이 없는 구버전 Redis(예: 로컬 개발 환경)와도 호환되어야 하기 때문이다 -
    // EVAL은 Redis 2.6부터 지원되어 훨씬 폭넓게 호환된다.
    private static final RedisScript<String> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then
                redis.call('DEL', KEYS[1])
            end
            return value
            """, String.class);

    private final StringRedisTemplate redisTemplate;

    public OAuthExchangeCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issue(String accessToken, String refreshToken) {
        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(code), accessToken + DELIMITER + refreshToken, TTL);
        return code;
    }

    /**
     * 코드를 조회와 동시에 삭제한다(1회용). 리다이렉트 URL·서버 로그에 코드가 남더라도
     * 이미 교환됐거나 30초가 지나면 무효화되어 재사용할 수 없다.
     */
    public Optional<TokenPair> redeem(String code) {
        String value = redisTemplate.execute(GET_AND_DELETE_SCRIPT, List.of(key(code)));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(DELIMITER, 2);
        return Optional.of(new TokenPair(parts[0], parts[1]));
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
