package mtf.com.overture.event;

import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.EventUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long createdEventId;

    @AfterEach
    void tearDown() {
        if (createdEventId != null) {
            eventRepository.deleteById(createdEventId);
            createdEventId = null;
        }
        Set<String> keys = redisTemplate.keys("event:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private Authentication organizerAuth() {
        return new TestingAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));
    }

    private Authentication userAuth() {
        return new TestingAuthenticationToken(2L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private Authentication adminAuth() {
        return new TestingAuthenticationToken(9L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private EventCreateRequest validRequest() {
        return new EventCreateRequest("콘서트", "올림픽공원", "설명", "http://example.com/p.png",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8));
    }

    @Test
    void createEvent_creates_a_draft_event_owned_by_the_caller() {
        EventResponse response = eventService.createEvent(organizerAuth(), 1L, validRequest());
        createdEventId = response.id();

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.createdBy()).isEqualTo(1L);
    }

    @Test
    void createEvent_allows_an_admin_caller() {
        EventResponse response = eventService.createEvent(adminAuth(), 9L, validRequest());
        createdEventId = response.id();

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.createdBy()).isEqualTo(9L);
    }

    @Test
    void createEvent_rejects_a_caller_without_organizer_or_admin_role() {
        assertThatThrownBy(() -> eventService.createEvent(userAuth(), 2L, validRequest()))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.FORBIDDEN));
    }

    @Test
    void createEvent_rejects_a_sale_end_at_before_sale_start_at() {
        EventCreateRequest invalid = new EventCreateRequest("콘서트", "올림픽공원", null, null,
                LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> eventService.createEvent(organizerAuth(), 1L, invalid))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.INVALID_SALE_PERIOD));
    }

    @Test
    void getEvent_returns_a_published_event_to_anyone() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L, validRequest());
        createdEventId = created.id();
        Event event = eventRepository.findById(created.id()).orElseThrow();
        event.publish();
        eventRepository.saveAndFlush(event);

        EventResponse response = eventService.getEvent(created.id(), null);

        assertThat(response.id()).isEqualTo(created.id());
    }

    @Test
    void getEvent_hides_a_draft_event_from_a_non_owner_as_not_found() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L, validRequest());
        createdEventId = created.id();

        assertThatThrownBy(() -> eventService.getEvent(created.id(), 2L))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.NOT_FOUND));
    }

    @Test
    void getEvent_shows_a_draft_event_to_its_owner() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L, validRequest());
        createdEventId = created.id();

        EventResponse response = eventService.getEvent(created.id(), 1L);

        assertThat(response.id()).isEqualTo(created.id());
    }

    @Test
    void getEvent_throws_not_found_for_a_nonexistent_event() {
        assertThatThrownBy(() -> eventService.getEvent(999999L, null))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.NOT_FOUND));
    }

    @Test
    void getEvent_hides_a_cached_draft_event_from_a_non_owner() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L, validRequest());
        createdEventId = created.id();

        // Cache-miss path: owner reads the event, populating the cache via EventCache.putEvent.
        eventService.getEvent(created.id(), 1L);

        // Cache-hit path: a different, non-owner viewer must still be denied even though
        // the response now comes from the cache, proving assertVisible re-runs and fails closed.
        assertThatThrownBy(() -> eventService.getEvent(created.id(), 2L))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.NOT_FOUND));
    }

    @Test
    void listEvents_returns_only_published_events() {
        EventResponse draft = eventService.createEvent(organizerAuth(), 1L, validRequest());
        createdEventId = draft.id();

        var page = eventService.listEvents(PageRequest.of(0, 20));

        assertThat(page.getContent()).noneMatch(e -> e.id().equals(draft.id()));

        Event event = eventRepository.findById(draft.id()).orElseThrow();
        event.publish();
        eventRepository.saveAndFlush(event);

        var pageAfterPublish = eventService.listEvents(PageRequest.of(0, 20));

        assertThat(pageAfterPublish.getContent()).anyMatch(e -> e.id().equals(draft.id()));
    }

    @Test
    void updateEvent_changes_only_the_provided_fields() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L, validRequest());
        createdEventId = created.id();
        EventUpdateRequest update = new EventUpdateRequest("새 제목", null, null, null, null, null);

        EventResponse response = eventService.updateEvent(organizerAuth(), 1L, created.id(), update);

        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.venue()).isEqualTo("올림픽공원");
    }

    @Test
    void updateEvent_rejects_a_caller_who_is_not_the_owner_or_admin() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L, validRequest());
        createdEventId = created.id();
        EventUpdateRequest update = new EventUpdateRequest("새 제목", null, null, null, null, null);
        Authentication otherOrganizer = new org.springframework.security.authentication.TestingAuthenticationToken(
                3L, null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));

        assertThatThrownBy(() -> eventService.updateEvent(otherOrganizer, 3L, created.id(), update))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.FORBIDDEN));
    }

    @Test
    void updateEvent_evicts_the_cached_entry() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L, validRequest());
        createdEventId = created.id();
        eventService.getEvent(created.id(), 1L);
        EventUpdateRequest update = new EventUpdateRequest("새 제목", null, null, null, null, null);

        eventService.updateEvent(organizerAuth(), 1L, created.id(), update);
        EventResponse refetched = eventService.getEvent(created.id(), 1L);

        assertThat(refetched.title()).isEqualTo("새 제목");
    }
}
