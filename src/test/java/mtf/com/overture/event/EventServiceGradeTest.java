package mtf.com.overture.event;

import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.SeatGradeCreateRequest;
import mtf.com.overture.event.dto.SeatGradeResponse;
import mtf.com.overture.event.dto.SeatGradeUpdateRequest;
import mtf.com.overture.event.dto.SeatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EventServiceGradeTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long createdEventId;

    @AfterEach
    void tearDown() {
        if (createdEventId != null) {
            List<SeatGrade> grades = seatGradeRepository.findByEventId(createdEventId);
            List<Long> gradeIds = grades.stream().map(SeatGrade::getId).toList();
            if (!gradeIds.isEmpty()) {
                seatRepository.deleteAll(seatRepository.findByGradeIdIn(gradeIds));
            }
            seatGradeRepository.deleteAll(grades);
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

    private Authentication adminAuth() {
        return new TestingAuthenticationToken(9L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Long createDraftEvent() {
        EventCreateRequest request = new EventCreateRequest("콘서트", "올림픽공원", "설명", null,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8));
        EventResponse response = eventService.createEvent(organizerAuth(), 1L, request);
        createdEventId = response.id();
        return response.id();
    }

    @Test
    void addGrade_creates_the_grade_and_the_matching_number_of_seats() {
        Long eventId = createDraftEvent();

        SeatGradeResponse response = eventService.addGrade(organizerAuth(), 1L, eventId,
                new SeatGradeCreateRequest("VIP", 150000, 5));

        assertThat(response.totalCount()).isEqualTo(5);
        assertThat(response.remainCount()).isEqualTo(5);
        List<Seat> seats = seatRepository.findByGradeId(response.id());
        assertThat(seats).hasSize(5);
        assertThat(seats).allMatch(seat -> seat.getRow().equals(1) && seat.getStatus() == SeatStatus.AVAILABLE);
        assertThat(seats.stream().map(Seat::getCol).sorted().toList()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void addGrade_transitions_the_event_from_draft_to_published() {
        Long eventId = createDraftEvent();

        eventService.addGrade(organizerAuth(), 1L, eventId, new SeatGradeCreateRequest("VIP", 150000, 5));

        Event event = eventRepository.findById(eventId).orElseThrow();
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void addGrade_hides_a_draft_event_from_a_non_owner_non_admin_caller_as_not_found() {
        // event is still DRAFT (no grade added yet), so assertVisible runs before the owner/admin
        // check and yields the same 404 a GET on this event would.
        Long eventId = createDraftEvent();
        Authentication otherOrganizer = new TestingAuthenticationToken(
                3L, null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));

        assertThatThrownBy(() -> eventService.addGrade(otherOrganizer, 3L, eventId,
                new SeatGradeCreateRequest("VIP", 150000, 5)))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.NOT_FOUND));
    }

    @Test
    void addGrade_allows_an_admin_who_is_not_the_owner() {
        Long eventId = createDraftEvent();

        SeatGradeResponse response = eventService.addGrade(adminAuth(), 9L, eventId,
                new SeatGradeCreateRequest("VIP", 150000, 5));

        assertThat(response.totalCount()).isEqualTo(5);
    }

    @Test
    void listGrades_returns_all_grades_for_the_event() {
        Long eventId = createDraftEvent();
        eventService.addGrade(organizerAuth(), 1L, eventId, new SeatGradeCreateRequest("VIP", 150000, 5));
        eventService.addGrade(organizerAuth(), 1L, eventId, new SeatGradeCreateRequest("R", 100000, 10));

        List<SeatGradeResponse> grades = eventService.listGrades(eventId, null);

        assertThat(grades).hasSize(2);
    }

    @Test
    void listGrades_hides_grades_of_a_draft_event_from_a_non_owner() {
        Long eventId = createDraftEvent();

        assertThatThrownBy(() -> eventService.listGrades(eventId, 2L))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.NOT_FOUND));
    }

    @Test
    void addGrade_evicts_the_cached_grade_list() {
        Long eventId = createDraftEvent();
        eventService.addGrade(organizerAuth(), 1L, eventId, new SeatGradeCreateRequest("VIP", 150000, 5));
        eventService.listGrades(eventId, 1L);

        eventService.addGrade(organizerAuth(), 1L, eventId, new SeatGradeCreateRequest("R", 100000, 10));
        List<SeatGradeResponse> grades = eventService.listGrades(eventId, 1L);

        assertThat(grades).hasSize(2);
        Event event = eventRepository.findById(eventId).orElseThrow();
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void updateGrade_changes_name_and_price_but_not_seat_count() {
        Long eventId = createDraftEvent();
        SeatGradeResponse grade = eventService.addGrade(organizerAuth(), 1L, eventId,
                new SeatGradeCreateRequest("VIP", 150000, 5));

        SeatGradeResponse updated = eventService.updateGrade(organizerAuth(), 1L, eventId, grade.id(),
                new SeatGradeUpdateRequest("VVIP", 200000));

        assertThat(updated.name()).isEqualTo("VVIP");
        assertThat(updated.price()).isEqualTo(200000);
        assertThat(updated.totalCount()).isEqualTo(5);
        assertThat(seatRepository.findByGradeId(grade.id())).hasSize(5);
    }

    @Test
    void updateGrade_rejects_a_caller_who_is_not_the_owner() {
        Long eventId = createDraftEvent();
        SeatGradeResponse grade = eventService.addGrade(organizerAuth(), 1L, eventId,
                new SeatGradeCreateRequest("VIP", 150000, 5));
        Authentication otherOrganizer = new TestingAuthenticationToken(
                3L, null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));

        assertThatThrownBy(() -> eventService.updateGrade(otherOrganizer, 3L, eventId, grade.id(),
                new SeatGradeUpdateRequest("VVIP", 200000)))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.FORBIDDEN));
    }

    @Test
    void updateGrade_throws_not_found_for_a_grade_belonging_to_a_different_event() {
        Long eventId = createDraftEvent();
        SeatGradeResponse grade = eventService.addGrade(organizerAuth(), 1L, eventId,
                new SeatGradeCreateRequest("VIP", 150000, 5));
        Long otherEventId = createDraftEventForCleanupOnly();

        assertThatThrownBy(() -> eventService.updateGrade(organizerAuth(), 1L, otherEventId, grade.id(),
                new SeatGradeUpdateRequest("VVIP", 200000)))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.NOT_FOUND));

        eventRepository.deleteById(otherEventId);
    }

    private Long createDraftEventForCleanupOnly() {
        EventCreateRequest request = new EventCreateRequest("다른 콘서트", "다른 장소", null, null,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8));
        return eventService.createEvent(organizerAuth(), 1L, request).id();
    }

    @Test
    void getSeats_groups_seats_by_grade_name() {
        Long eventId = createDraftEvent();
        eventService.addGrade(organizerAuth(), 1L, eventId, new SeatGradeCreateRequest("VIP", 150000, 2));
        eventService.addGrade(organizerAuth(), 1L, eventId, new SeatGradeCreateRequest("R", 100000, 3));

        Map<String, List<SeatResponse>> seats = eventService.getSeats(eventId, null);

        assertThat(seats.get("VIP")).hasSize(2);
        assertThat(seats.get("R")).hasSize(3);
    }

    @Test
    void getSeats_hides_seats_of_a_draft_event_from_a_non_owner() {
        Long eventId = createDraftEvent();

        assertThatThrownBy(() -> eventService.getSeats(eventId, 2L))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.NOT_FOUND));
    }
}
