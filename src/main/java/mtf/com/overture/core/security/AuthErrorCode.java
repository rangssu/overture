package mtf.com.overture.core.security;

import lombok.Getter;

@Getter
public enum AuthErrorCode {
    INVALID_ACCESS_TOKEN("AUTH_001", "만료되었거나 유효하지 않은 토큰입니다."),
    INVALID_REFRESH_TOKEN("AUTH_002", "리프레시 토큰이 유효하지 않습니다.");

    private final String code;
    private final String message;

    AuthErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
