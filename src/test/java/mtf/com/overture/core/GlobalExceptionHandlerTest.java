package mtf.com.overture.core;

import mtf.com.overture.core.security.AuthErrorCode;
import mtf.com.overture.core.security.AuthException;
import mtf.com.overture.user.controller.AuthController;
import mtf.com.overture.user.exception.UserErrorCode;
import mtf.com.overture.user.exception.UserException;
import mtf.com.overture.user.dto.RefreshRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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

    @Test
    void handleValidationException_returns_400_with_field_error_message() throws NoSuchMethodException {
        var bindingResult = new BeanPropertyBindingResult(new RefreshRequest(""), "refreshRequest");
        bindingResult.addError(new FieldError("refreshRequest", "refreshToken", "공백일 수 없습니다"));
        var methodParameter = new MethodParameter(
                AuthController.class.getMethod("refresh", RefreshRequest.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().error().message()).contains("refreshToken", "공백일 수 없습니다");
    }

    @Test
    void handleUserException_returns_the_http_status_and_code_from_the_error_code() {
        UserException exception = new UserException(UserErrorCode.NICKNAME_DUPLICATE);

        ResponseEntity<ApiErrorResponse> response = handler.handleUserException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("USER_001");
    }

    @Test
    void handleUserException_maps_no_fields_to_update_to_400() {
        UserException exception = new UserException(UserErrorCode.NO_FIELDS_TO_UPDATE);

        ResponseEntity<ApiErrorResponse> response = handler.handleUserException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("USER_002");
    }
}
