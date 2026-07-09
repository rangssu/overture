package mtf.com.overture.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final String TYPE_CLAIM = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long accessExpirationSeconds;
    private final long refreshExpirationSeconds;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-seconds}") long accessExpirationSeconds,
            @Value("${jwt.refresh-expiration-seconds}") long refreshExpirationSeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getRole(String token) {
        return parseClaims(token).get(ROLE_CLAIM, String.class);
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
    }

    public long getRemainingSeconds(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return Math.max(0, (expiration.getTime() - System.currentTimeMillis()) / 1000);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
