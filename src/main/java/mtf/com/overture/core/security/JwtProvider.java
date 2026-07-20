package mtf.com.overture.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final String TYPE_CLAIM = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final JwtParser parser;
    private final long accessExpirationSeconds;
    private final long refreshExpirationSeconds;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-seconds}") long accessExpirationSeconds,
            @Value("${jwt.refresh-expiration-seconds}") long refreshExpirationSeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.accessExpirationSeconds = accessExpirationSeconds;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    public String createAccessToken(Long userId, String role) {
        return buildToken(userId, role, TYPE_ACCESS, accessExpirationSeconds);
    }

    public String createRefreshToken(Long userId) {
        return buildToken(userId, null, TYPE_REFRESH, refreshExpirationSeconds);
    }

    private String buildToken(Long userId, String role, String type, long expirationSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .claim(TYPE_CLAIM, type);

        if (role != null) {
            builder.claim(ROLE_CLAIM, role);
        }

        return builder.signWith(key).compact();
    }

    /**
     * 서명/만료 검증을 한 번만 수행하고 Claims를 반환한다. 같은 토큰에서 여러 값을 읽어야 할 때는
     * (예: JwtAuthenticationFilter, AuthService) 문자열 오버로드를 반복 호출하지 말고 이 메서드로
     * 한 번만 파싱한 뒤 Claims를 받는 오버로드를 쓴다.
     */
    public Optional<Claims> parseIfValid(String token) {
        try {
            return Optional.of(parser.parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean validateToken(String token) {
        return parseIfValid(token).isPresent();
    }

    public Long getUserId(String token) {
        return getUserId(parseClaims(token));
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getRole(String token) {
        return getRole(parseClaims(token));
    }

    public String getRole(Claims claims) {
        return claims.get(ROLE_CLAIM, String.class);
    }

    public boolean isAccessToken(String token) {
        return isAccessToken(parseClaims(token));
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(TYPE_CLAIM, String.class));
    }

    public boolean isRefreshToken(String token) {
        return isRefreshToken(parseClaims(token));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(TYPE_CLAIM, String.class));
    }

    public long getRemainingSeconds(String token) {
        return getRemainingSeconds(parseClaims(token));
    }

    public long getRemainingSeconds(Claims claims) {
        return Math.max(0, (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000);
    }

    private Claims parseClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }
}
