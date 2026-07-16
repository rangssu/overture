package mtf.com.overture.core;

import mtf.com.overture.core.security.AuthException;
import mtf.com.overture.event.EventException;
import mtf.com.overture.queue.QueueException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("요청 값이 유효하지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("INVALID_REQUEST", message));
    }

    @ExceptionHandler(EventException.class)
    public ResponseEntity<ApiErrorResponse> handleEventException(EventException e) {
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(ApiErrorResponse.of(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }

    @ExceptionHandler(QueueException.class)
    public ResponseEntity<ApiErrorResponse> handleQueueException(QueueException e) {
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(ApiErrorResponse.of(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }
}
