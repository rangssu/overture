package mtf.com.overture.user;

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

class OAuth2SuccessHandlerTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";
    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 900, 604800);
    private RefreshTokenStore refreshTokenStore;
    private OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        refreshTokenStore = mock(RefreshTokenStore.class);
        handler = new OAuth2SuccessHandler(jwtProvider, refreshTokenStore, "http://localhost:5173/oauth/callback");
    }

    @Test
    void redirects_with_tokens_on_success() throws Exception {
        CustomOAuth2User principal = new CustomOAuth2User(55L, Role.USER, Map.of("id", 55L));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(302);
        String location = response.getRedirectedUrl();
        assertThat(location).startsWith("http://localhost:5173/oauth/callback");
        assertThat(location).contains("accessToken=");
        assertThat(location).contains("refreshToken=");
        verify(refreshTokenStore).store(eq(55L), anyString());
    }
}
