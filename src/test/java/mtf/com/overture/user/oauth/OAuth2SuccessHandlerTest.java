package mtf.com.overture.user.oauth;
import mtf.com.overture.user.repository.RefreshTokenStore;
import mtf.com.overture.user.enums.Role;
import mtf.com.overture.user.repository.OAuthExchangeCodeStore;

import mtf.com.overture.core.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2SuccessHandlerTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";
    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 900, 604800);
    private RefreshTokenStore refreshTokenStore;
    private OAuthExchangeCodeStore exchangeCodeStore;
    private OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        refreshTokenStore = mock(RefreshTokenStore.class);
        exchangeCodeStore = mock(OAuthExchangeCodeStore.class);
        handler = new OAuth2SuccessHandler(jwtProvider, refreshTokenStore, exchangeCodeStore, "http://localhost:5173/oauth/callback");
    }

    @Test
    void redirects_with_a_one_time_exchange_code_instead_of_raw_tokens() throws Exception {
        // 토큰을 리다이렉트 URL에 직접 싣지 않는다 - 브라우저 히스토리·서버 access 로그·Referer 헤더
        // 유출을 막기 위해 1회용 교환 코드만 실어서 보낸다.
        when(exchangeCodeStore.issue(anyString(), anyString())).thenReturn("one-time-code-abc");
        CustomOAuth2User principal = new CustomOAuth2User(55L, Role.USER, Map.of("id", 55L));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(302);
        String location = response.getRedirectedUrl();
        assertThat(location).startsWith("http://localhost:5173/oauth/callback");
        assertThat(location).contains("code=one-time-code-abc");
        assertThat(location).doesNotContain("accessToken=");
        assertThat(location).doesNotContain("refreshToken=");
        verify(refreshTokenStore).store(eq(55L), anyString());
        verify(exchangeCodeStore).issue(anyString(), anyString());
    }
}
