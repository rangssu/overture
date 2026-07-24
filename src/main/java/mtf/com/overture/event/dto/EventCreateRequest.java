package mtf.com.overture.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventCreateRequest(
        @NotBlank @Size(min = 2, max = 100)
        String title,
        @NotBlank @Size(min = 2, max = 100)
        String venue,
        @Size(max = 2000)
        String description,
        @Size(max = 255)
        String posterUrl,
        @NotNull
        LocalDateTime saleStartAt,
        @NotNull
        LocalDateTime saleEndAt
) {
}
