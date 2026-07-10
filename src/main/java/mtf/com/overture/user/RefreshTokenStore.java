package mtf.com.overture.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";
    private static final String PREVIOUS_KEY_SUFFIX = ":prev";
    private static final Duration GRACE_PERIOD = Duration.ofSeconds(10);

    // KEYS[1]=현재 토큰 키, KEYS[2]=직전(회전 폐기) 토큰 키
    // ARGV[1]=제출된 토큰, ARGV[2]=새로 발급할 토큰 후보, ARGV[3]=현재 토큰 TTL(초), ARGV[4]=grace TTL(초)
    //
    // GET+비교+SET을 원자적으로 묶어 rotation 중 TOCTOU 레이스를 막고, 동시 경합으로 방금 폐기된
    // 토큰이 곧바로 재사용되는 경우(탈취가 아닌 정상 경합)는 grace 기간 동안 거부 없이 최신 토큰으로
    // 수렴시킨다. 그 범위를 벗어난 재사용만 탈취 의심으로 보고 세션 전체를 무효화한다.
    private static final RedisScript<String> ROTATE_WITH_GRACE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                redis.call('SET', KEYS[2], current, 'EX', ARGV[4])
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return '1:' .. ARGV[2]
            end
            local previous = redis.call('GET', KEYS[2])
            if current and previous == ARGV[1] then
                return '2:' .. current
            end
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            return '0:'
            """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RefreshTokenStore(StringRedisTemplate redisTemplate,
                              @Value("${jwt.refresh-expiration-seconds}") long refreshExpirationSeconds) {
        this.redisTemplate = redisTemplate;
        // JwtProvider가 refresh JWT에 서명할 때 쓰는 만료시간과 같은 값을 공유해야, 둘 중 하나만
        // 바뀌었을 때 "JWT는 유효한데 Redis는 이미 지웠다/그 반대" 같은 불일치가 생기지 않는다.
        this.ttl = Duration.ofSeconds(refreshExpirationSeconds);
    }

    public void store(Long userId, String refreshToken) {
        // 새 로그인은 새 세션의 시작이므로, 이전 세션이 남긴 grace(prev) 상태를 함께 정리해야
        // 이미 무효화됐어야 할 이전 토큰이 grace window를 통해 이 새 토큰을 받아가는 것을 막는다.
        redisTemplate.delete(previousKey(userId));
        redisTemplate.opsForValue().set(key(userId), refreshToken, ttl);
    }

    public String get(Long userId) {
        return redisTemplate.opsForValue().get(key(userId));
    }

    /**
     * presentedToken이 현재 저장된 토큰과 일치하면 newTokenCandidate로 원자적으로 회전한 뒤
     * newTokenCandidate를 반환한다. grace 기간 내의 직전(회전 폐기) 토큰과 일치하면 회전하지
     * 않고 이미 활성화된 현재 토큰을 그대로 반환한다. 둘 다 아니면 세션을 무효화하고 null을 반환한다.
     */
    public String rotateIfValid(Long userId, String presentedToken, String newTokenCandidate) {
        String result = redisTemplate.execute(ROTATE_WITH_GRACE_SCRIPT,
                List.of(key(userId), previousKey(userId)),
                presentedToken, newTokenCandidate,
                String.valueOf(ttl.toSeconds()), String.valueOf(GRACE_PERIOD.toSeconds()));

        if (result == null || result.charAt(0) == '0') {
            revoke(userId);
            return null;
        }
        return result.substring(2);
    }

    public void revoke(Long userId) {
        redisTemplate.delete(key(userId));
        redisTemplate.delete(previousKey(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String previousKey(Long userId) {
        return key(userId) + PREVIOUS_KEY_SUFFIX;
    }
}
