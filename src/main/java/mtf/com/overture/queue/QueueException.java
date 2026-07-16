package mtf.com.overture.queue;

import lombok.Getter;

@Getter
public class QueueException extends RuntimeException {

    private final QueueErrorCode errorCode;

    public QueueException(QueueErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
