package mtf.com.overture.event;

import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.SeatGradeCreateRequest;
import mtf.com.overture.event.dto.SeatGradeResponse;
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
    void addGrade_rejects_a_caller_who_is_not_the_owner() {
        Long eventId = createDraftEvent();
        Authentication otherOrganizer = new TestingAuthenticationToken(
                3L, null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));

        assertThatThrownBy(() -> eventService.addGrade(otherOrganizer, 3L, eventId,
                new SeatGradeCreateRequest("VIP", 150000, 5)))
                .isInstanceOf(EventException.class)
                .satisfies(e -> assertThat(((EventException) e).getErrorCode()).isEqualTo(EventErrorCode.FORBIDDEN));
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
}
