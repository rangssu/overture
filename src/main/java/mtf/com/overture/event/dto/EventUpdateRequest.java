package mtf.com.overture.event.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventUpdateRequest(
        @Size(min = 2, max = 100)
        String title,
        @Size(min = 2, max = 100)
        String venue,
        @Size(max = 2000)
        String description,
        @Size(max = 255)
        String posterUrl,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt
) {
}
