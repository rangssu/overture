package mtf.com.overture.user;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum UserErrorCode {
    NICKNAME_DUPLICATE(HttpStatus.CONFLICT, "USER_001", "이미 사용 중인 닉네임입니다."),
    NO_FIELDS_TO_UPDATE(HttpStatus.BAD_REQUEST, "USER_002", "수정할 필드가 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    UserErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
