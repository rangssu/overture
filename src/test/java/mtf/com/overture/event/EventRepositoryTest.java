package mtf.com.overture.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    private Event buildEvent(EventStatus status) {
        return Event.builder()
                .title("콘서트")
                .venue("올림픽공원")
                .description("설명")
                .posterUrl("http://example.com/poster.png")
                .saleStartAt(LocalDateTime.now())
                .saleEndAt(LocalDateTime.now().plusDays(7))
                .status(status)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void save_persists_and_reloads_all_fields() {
        Event saved = eventRepository.save(buildEvent(EventStatus.DRAFT));

        Event found = eventRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getTitle()).isEqualTo("콘서트");
        assertThat(found.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(found.getCreatedBy()).isEqualTo(1L);
    }

    @Test
    void findByStatus_returns_only_matching_status() {
        eventRepository.save(buildEvent(EventStatus.DRAFT));
        eventRepository.save(buildEvent(EventStatus.PUBLISHED));
        eventRepository.save(buildEvent(EventStatus.PUBLISHED));

        Pageable pageable = PageRequest.of(0, 20);
        var page = eventRepository.findByStatus(EventStatus.PUBLISHED, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(event -> event.getStatus() == EventStatus.PUBLISHED);
    }

    @Test
    void update_changes_only_non_null_fields() {
        Event event = eventRepository.saveAndFlush(buildEvent(EventStatus.DRAFT));
        LocalDateTime originalSaleStartAt = event.getSaleStartAt();

        event.update("새 제목", null, null, null, null, null);

        assertThat(event.getTitle()).isEqualTo("새 제목");
        assertThat(event.getVenue()).isEqualTo("올림픽공원");
        assertThat(event.getSaleStartAt()).isEqualTo(originalSaleStartAt);
    }

    @Test
    void publish_transitions_status_to_published() {
        Event event = buildEvent(EventStatus.DRAFT);

        event.publish();

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void isOwnedBy_returns_true_only_for_the_creator() {
        Event event = buildEvent(EventStatus.DRAFT);

        assertThat(event.isOwnedBy(1L)).isTrue();
        assertThat(event.isOwnedBy(2L)).isFalse();
    }
}
