package mtf.com.overture.event.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// totalCount 필드가 없다 - 이미 생성된 Seat row와의 정합성 때문에 구조적으로 변경 불가능하게 막는다.
public record SeatGradeUpdateRequest(
        @Size(min = 1, max = 20)
        String name,
        @Min(0)
        Integer price
) {
}
