package mtf.com.overture.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, 900, 604800);
    }

    @Test
    void createAccessToken_contains_userId_and_role() {
        String token = jwtProvider.createAccessToken(42L, "USER");

        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.getUserId(token)).isEqualTo(42L);
        assertThat(jwtProvider.getRole(token)).isEqualTo("USER");
    }

    @Test
    void createRefreshToken_contains_userId() {
        String token = jwtProvider.createRefreshToken(7L);

        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.getUserId(token)).isEqualTo(7L);
    }

    @Test
    void validateToken_returns_false_for_expired_token() {
        JwtProvider shortLived = new JwtProvider(SECRET, -1, -1);
        String expiredToken = shortLived.createAccessToken(1L, "USER");

        assertThat(jwtProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateToken_returns_false_for_tampered_token() {
        String token = jwtProvider.createAccessToken(1L, "USER");

        assertThat(jwtProvider.validateToken(token + "tampered")).isFalse();
    }

    @Test
    void createAccessToken_is_not_a_refresh_token() {
        String token = jwtProvider.createAccessToken(1L, "USER");

        assertThat(jwtProvider.isAccessToken(token)).isTrue();
        assertThat(jwtProvider.isRefreshToken(token)).isFalse();
    }

    @Test
    void createRefreshToken_is_not_an_access_token() {
        String token = jwtProvider.createRefreshToken(1L);

        assertThat(jwtProvider.isRefreshToken(token)).isTrue();
        assertThat(jwtProvider.isAccessToken(token)).isFalse();
    }

    @Test
    void createRefreshToken_returns_distinct_tokens_for_same_user_even_within_the_same_second() {
        // iat/exp가 초 단위(NumericDate)라 jti 같은 고유 클레임이 없으면
        // 같은 유저에게 같은 초에 재발급된 토큰이 완전히 동일해질 수 있다 (rotation 무력화 위험).
        String first = jwtProvider.createRefreshToken(1L);
        String second = jwtProvider.createRefreshToken(1L);

        assertThat(first).isNotEqualTo(second);
    }
}
