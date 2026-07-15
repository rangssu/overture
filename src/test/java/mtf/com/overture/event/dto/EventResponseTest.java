package mtf.com.overture.event.dto;

import mtf.com.overture.event.Event;
import mtf.com.overture.event.EventStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EventResponseTest {

    @Test
    void from_maps_all_fields_from_the_event_entity() {
        LocalDateTime saleStartAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime saleEndAt = LocalDateTime.of(2026, 8, 7, 23, 59);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 15, 9, 0);
        Event event = Event.builder()
                .title("콘서트")
                .venue("올림픽공원")
                .description("설명")
                .posterUrl("http://example.com/poster.png")
                .saleStartAt(saleStartAt)
                .saleEndAt(saleEndAt)
                .status(EventStatus.DRAFT)
                .createdBy(1L)
                .createdAt(createdAt)
                .build();

        EventResponse response = EventResponse.from(event);

        assertThat(response.id()).isNull();
        assertThat(response.title()).isEqualTo("콘서트");
        assertThat(response.venue()).isEqualTo("올림픽공원");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.createdBy()).isEqualTo(1L);
        assertThat(response.saleStartAt()).isEqualTo(saleStartAt);
        assertThat(response.saleEndAt()).isEqualTo(saleEndAt);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
