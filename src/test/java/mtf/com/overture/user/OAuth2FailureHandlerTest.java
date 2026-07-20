package mtf.com.overture.user;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2FailureHandlerTest {

    private final OAuth2FailureHandler handler = new OAuth2FailureHandler("http://localhost:5173/oauth/callback");

    @Test
    void redirects_with_the_specific_oauth2_error_code_when_present() throws Exception {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("kakao_required_profile_missing", "카카오 계정에서 이메일/닉네임 필수 동의 항목을 확인할 수 없습니다.", null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:5173/oauth/callback")
                .contains("error=kakao_required_profile_missing");
    }

    @Test
    void redirects_with_generic_error_code_for_non_oauth2_authentication_exceptions() throws Exception {
        BadCredentialsException exception = new BadCredentialsException("bad credentials");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:5173/oauth/callback")
                .contains("error=oauth_failed");
    }

    @Test
    void encodes_error_code_so_it_cannot_inject_extra_query_parameters() throws Exception {
        // /login/oauth2/code/kakao 는 permitAll 이라 error 파라미터는 카카오가 아니라
        // 누구든 직접 조작해서 보낼 수 있다 - '&'/'=' 가 그대로 URL에 반영되면 안 된다.
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("evil&redirect_uri=https://phishing.example.com", "설명", null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:5173/oauth/callback")
                .doesNotContain("redirect_uri=https://phishing.example.com")
                .contains("error=evil%26redirect_uri%3Dhttps://phishing.example.com");
    }
}
