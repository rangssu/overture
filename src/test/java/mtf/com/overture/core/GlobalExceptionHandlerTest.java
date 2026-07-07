package mtf.com.overture.core;

import mtf.com.overture.core.security.AuthErrorCode;
import mtf.com.overture.core.security.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAuthException_returns_401_with_error_body() {
        AuthException exception = new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("AUTH_002");
    }
}
