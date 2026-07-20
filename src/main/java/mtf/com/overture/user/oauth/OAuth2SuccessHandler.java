package mtf.com.overture.user.oauth;
import mtf.com.overture.user.repository.RefreshTokenStore;
import mtf.com.overture.user.repository.OAuthExchangeCodeStore;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mtf.com.overture.core.security.JwtProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final OAuthExchangeCodeStore exchangeCodeStore;
    private final String redirectUri;

    public OAuth2SuccessHandler(JwtProvider jwtProvider, RefreshTokenStore refreshTokenStore,
                                 OAuthExchangeCodeStore exchangeCodeStore, String redirectUri) {
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.exchangeCodeStore = exchangeCodeStore;
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = principal.getUserId();

        String accessToken = jwtProvider.createAccessToken(userId, principal.getRole().name());
        String refreshToken = jwtProvider.createRefreshToken(userId);

        refreshTokenStore.store(userId, refreshToken);

        // 토큰을 리다이렉트 URL에 직접 싣지 않는다 - 브라우저 히스토리·서버 access 로그·Referer 헤더로
        // 유출될 수 있기 때문. 대신 1회용 짧은 TTL 코드만 실어서, 클라이언트가 POST /api/v1/auth/exchange로
        // 즉시 교환하게 한다.
        String code = exchangeCodeStore.issue(accessToken, refreshToken);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .build().toUriString();

        response.sendRedirect(targetUrl);
    }
}
