package mtf.com.overture.user;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenStoreTest {

    @Test
    void store_clears_the_previous_grace_key_before_setting_the_new_current_token() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RefreshTokenStore store = new RefreshTokenStore(redisTemplate, 604800);

        store.store(1L, "new-login-token");

        // 새 로그인은 이전 세션의 grace(prev) 상태를 남기지 않아야, 이미 폐기된 옛 토큰이
        // grace window를 통해 이 새 토큰을 가로채는 레이스가 발생하지 않는다.
        verify(redisTemplate).delete("refresh:1:prev");
        verify(valueOperations).set(eq("refresh:1"), eq("new-login-token"), any(Duration.class));
    }

    @Test
    void store_uses_the_ttl_derived_from_the_configured_refresh_expiration_seconds() {
        // JwtProvider가 refresh JWT 서명에 쓰는 만료시간(jwt.refresh-expiration-seconds)과 같은 값을
        // 공유해야 하드코딩된 값이 설정과 따로 놀아 불일치가 생기는 걸 막을 수 있다.
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RefreshTokenStore store = new RefreshTokenStore(redisTemplate, 1200);

        store.store(1L, "token");

        verify(valueOperations).set(eq("refresh:1"), eq("token"), eq(Duration.ofSeconds(1200)));
    }
}
