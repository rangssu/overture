package mtf.com.overture.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SeatGradeCreateRequest(
        @NotBlank @Size(min = 1, max = 20)
        String name,
        @NotNull @Min(0)
        Integer price,
        @NotNull @Min(1) @Max(50000)
        Integer totalCount
) {
}
