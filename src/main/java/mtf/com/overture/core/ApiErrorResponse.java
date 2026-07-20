package mtf.com.overture.core;

import java.time.LocalDateTime;

public record ApiErrorResponse(boolean success, Object data, ErrorBody error, LocalDateTime timestamp) {

    public record ErrorBody(String code, String message) {
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, null, new ErrorBody(code, message), LocalDateTime.now());
    }
}
