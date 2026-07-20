package mtf.com.overture.user;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final String DEFAULT_ERROR_CODE = "oauth_failed";

    private final String redirectUri;

    public OAuth2FailureHandler(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException, ServletException {
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", errorCode(exception))
                .encode()
                .build().toUriString();

        response.sendRedirect(targetUrl);
    }

    private String errorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception
                && oauth2Exception.getError().getErrorCode() != null) {
            return oauth2Exception.getError().getErrorCode();
        }
        return DEFAULT_ERROR_CODE;
    }
}
