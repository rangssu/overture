package mtf.com.overture.user;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mtf.com.overture.core.security.JwtProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;

public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final String redirectUri;

    public OAuth2SuccessHandler(JwtProvider jwtProvider, StringRedisTemplate redisTemplate, String redirectUri) {
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = principal.getUserId();

        String accessToken = jwtProvider.createAccessToken(userId, principal.getRole().name());
        String refreshToken = jwtProvider.createRefreshToken(userId);

        redisTemplate.opsForValue().set(REFRESH_KEY_PREFIX + userId, refreshToken, Duration.ofDays(7));

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        response.sendRedirect(targetUrl);
    }
}
