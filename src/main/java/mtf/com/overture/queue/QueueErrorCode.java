package mtf.com.overture.queue;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum QueueErrorCode {
    NOT_IN_QUEUE(HttpStatus.NOT_FOUND, "QUEUE_001", "대기열에 참여하지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    QueueErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
