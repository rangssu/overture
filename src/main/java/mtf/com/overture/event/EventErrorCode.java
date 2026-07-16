package mtf.com.overture.event;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum EventErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT_001", "이벤트를 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "EVENT_002", "이 작업을 수행할 권한이 없습니다."),
    INVALID_SALE_PERIOD(HttpStatus.BAD_REQUEST, "EVENT_003", "판매 종료일은 판매 시작일 이후여야 합니다."),
    DUPLICATE_GRADE_NAME(HttpStatus.BAD_REQUEST, "EVENT_004", "이미 동일한 이름의 좌석 등급이 존재합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    EventErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
