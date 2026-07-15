package mtf.com.overture.event.dto;

import mtf.com.overture.event.Event;

import java.time.LocalDateTime;

public record EventResponse(Long id, String title, String venue, String description, String posterUrl,
                             LocalDateTime saleStartAt, LocalDateTime saleEndAt, String status,
                             Long createdBy, LocalDateTime createdAt) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getTitle(), event.getVenue(), event.getDescription(),
                event.getPosterUrl(), event.getSaleStartAt(), event.getSaleEndAt(), event.getStatus().name(),
                event.getCreatedBy(), event.getCreatedAt());
    }
}
