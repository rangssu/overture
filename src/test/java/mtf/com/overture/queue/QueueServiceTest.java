package mtf.com.overture.queue;

import mtf.com.overture.event.EventException;
import mtf.com.overture.event.EventRepository;
import mtf.com.overture.event.EventService;
import mtf.com.overture.event.SeatGradeRepository;
import mtf.com.overture.event.SeatRepository;
import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.SeatGradeCreateRequest;
import mtf.com.overture.queue.dto.QueueStatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class QueueServiceTest {

    @Autowired
    private QueueService queueService;

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

    @Value("${queue.capacity}")
    private long capacity;

    private Long createdEventId;

    @AfterEach
    void tearDown() {
        if (createdEventId != null) {
            var grades = seatGradeRepository.findByEventId(createdEventId);
            var gradeIds = grades.stream().map(g -> g.getId()).toList();
            if (!gradeIds.isEmpty()) {
                seatRepository.deleteAll(seatRepository.findByGradeIdIn(gradeIds));
            }
            seatGradeRepository.deleteAll(grades);
            eventRepository.deleteById(createdEventId);
            createdEventId = null;
        }
        Set<String> eventKeys = redisTemplate.keys("event:*");
        if (eventKeys != null && !eventKeys.isEmpty()) {
            redisTemplate.delete(eventKeys);
        }
        Set<String> queueKeys = redisTemplate.keys("queue:*");
        if (queueKeys != null && !queueKeys.isEmpty()) {
            redisTemplate.delete(queueKeys);
        }
    }

    private Authentication organizerAuth() {
        return new TestingAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));
    }

    private Long publishedEvent() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L,
                new EventCreateRequest("콘서트", "올림픽공원", "설명", "http://example.com/p.png",
                        LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8)));
        eventService.addGrade(organizerAuth(), 1L, created.id(),
                new SeatGradeCreateRequest("VIP", 100000, 10));
        createdEventId = created.id();
        return created.id();
    }

    private Long draftEvent() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L,
                new EventCreateRequest("콘서트", "올림픽공원", "설명", "http://example.com/p.png",
                        LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8)));
        createdEventId = created.id();
        return created.id();
    }

    @Test
    void enter_adds_the_user_to_the_queue_and_returns_position_one_when_first() {
        Long eventId = publishedEvent();

        QueueStatusResponse response = queueService.enter(eventId, 2L);

        assertThat(response.position()).isEqualTo(1);
        assertThat(response.totalWaiting()).isEqualTo(1);
        assertThat(response.admitted()).isTrue();
    }

    @Test
    void enter_throws_when_the_event_does_not_exist() {
        assertThatThrownBy(() -> queueService.enter(999999L, 2L))
                .isInstanceOf(EventException.class);
    }

    @Test
    void enter_throws_when_the_event_is_still_draft() {
        Long eventId = draftEvent();

        assertThatThrownBy(() -> queueService.enter(eventId, 1L))
                .isInstanceOf(EventException.class);
    }

    @Test
    void enter_is_idempotent_when_the_same_user_enters_twice() {
        Long eventId = publishedEvent();
        queueService.enter(eventId, 2L);

        QueueStatusResponse second = queueService.enter(eventId, 2L);

        assertThat(second.position()).isEqualTo(1);
        assertThat(second.totalWaiting()).isEqualTo(1);
    }

    @Test
    void enter_ranks_users_by_arrival_order() {
        Long eventId = publishedEvent();
        queueService.enter(eventId, 2L);

        QueueStatusResponse second = queueService.enter(eventId, 3L);

        assertThat(second.position()).isEqualTo(2);
        assertThat(second.totalWaiting()).isEqualTo(2);
    }

    @Test
    void enter_breaks_same_millisecond_ties_by_arrival_order_not_userId_string() {
        Long eventId = publishedEvent();
        queueService.enter(eventId, 9L);
        queueService.enter(eventId, 10L);

        QueueStatusResponse firstUser = queueService.getStatus(eventId, 9L);
        QueueStatusResponse secondUser = queueService.getStatus(eventId, 10L);

        assertThat(firstUser.position()).isEqualTo(1);
        assertThat(secondUser.position()).isEqualTo(2);
    }

    @Test
    void enter_marks_admitted_false_once_rank_reaches_capacity() {
        Long eventId = publishedEvent();
        long baseScore = System.currentTimeMillis() - 60_000;
        for (int i = 0; i < capacity; i++) {
            redisTemplate.opsForZSet().add("queue:" + eventId, "dummy-" + i, baseScore + i);
        }

        QueueStatusResponse response = queueService.enter(eventId, 2L);

        assertThat(response.position()).isEqualTo(capacity + 1);
        assertThat(response.admitted()).isFalse();
    }

    @Test
    void enter_marks_admitted_true_when_rank_is_just_below_capacity() {
        Long eventId = publishedEvent();
        long baseScore = System.currentTimeMillis() - 60_000;
        for (int i = 0; i < capacity - 1; i++) {
            redisTemplate.opsForZSet().add("queue:" + eventId, "dummy-" + i, baseScore + i);
        }

        QueueStatusResponse response = queueService.enter(eventId, 2L);

        assertThat(response.position()).isEqualTo(capacity);
        assertThat(response.admitted()).isTrue();
    }

    @Test
    void enter_preserves_original_score_and_position_on_re_entry() {
        Long eventId = publishedEvent();

        QueueStatusResponse userAFirst = queueService.enter(eventId, 2L);
        QueueStatusResponse userBFirst = queueService.enter(eventId, 3L);
        QueueStatusResponse userASecond = queueService.enter(eventId, 2L);

        assertThat(userAFirst.position()).isEqualTo(1);
        assertThat(userBFirst.position()).isEqualTo(2);
        assertThat(userASecond.position()).isEqualTo(1);
        assertThat(userASecond.totalWaiting()).isEqualTo(2);

        QueueStatusResponse userBAfter = queueService.enter(eventId, 3L);
        assertThat(userBAfter.position()).isEqualTo(2);
        assertThat(userBAfter.totalWaiting()).isEqualTo(2);
    }

    @Test
    void getStatus_returns_the_same_position_as_enter_without_re_adding() {
        Long eventId = publishedEvent();
        queueService.enter(eventId, 2L);
        queueService.enter(eventId, 3L);

        QueueStatusResponse status = queueService.getStatus(eventId, 3L);

        assertThat(status.position()).isEqualTo(2);
        assertThat(status.totalWaiting()).isEqualTo(2);
        assertThat(status.admitted()).isTrue();
    }

    @Test
    void getStatus_throws_when_the_user_never_entered_the_queue() {
        Long eventId = publishedEvent();
        queueService.enter(eventId, 2L);

        assertThatThrownBy(() -> queueService.getStatus(eventId, 999L))
                .isInstanceOf(QueueException.class);
    }

    @Test
    void leave_removes_the_user_so_status_lookup_then_throws() {
        Long eventId = publishedEvent();
        queueService.enter(eventId, 2L);

        queueService.leave(eventId, 2L);

        assertThatThrownBy(() -> queueService.getStatus(eventId, 2L))
                .isInstanceOf(QueueException.class);
    }

    @Test
    void leave_does_not_throw_when_the_user_was_never_in_the_queue() {
        Long eventId = publishedEvent();

        queueService.leave(eventId, 2L);
    }

    @Test
    void enter_lazily_removes_entries_older_than_the_ttl() {
        Long eventId = publishedEvent();
        String key = "queue:" + eventId;
        long staleScore = System.currentTimeMillis() - Duration.ofMinutes(5).toMillis() - 1000;
        redisTemplate.opsForZSet().add(key, "999", staleScore);

        queueService.enter(eventId, 2L);

        assertThat(redisTemplate.opsForZSet().rank(key, "999")).isNull();
        assertThat(redisTemplate.opsForZSet().rank(key, "2")).isNotNull();
    }

    @Test
    void getStatus_lazily_removes_entries_older_than_the_ttl() {
        Long eventId = publishedEvent();
        String key = "queue:" + eventId;
        long staleScore = System.currentTimeMillis() - Duration.ofMinutes(5).toMillis() - 1000;
        redisTemplate.opsForZSet().add(key, "999", staleScore);

        queueService.enter(eventId, 2L);

        long anotherStaleScore = System.currentTimeMillis() - Duration.ofMinutes(5).toMillis() - 1000;
        redisTemplate.opsForZSet().add(key, "888", anotherStaleScore);

        QueueStatusResponse status = queueService.getStatus(eventId, 2L);

        assertThat(redisTemplate.opsForZSet().rank(key, "999")).isNull();
        assertThat(redisTemplate.opsForZSet().rank(key, "888")).isNull();
        assertThat(status.position()).isEqualTo(1);
        assertThat(status.totalWaiting()).isEqualTo(1);
        assertThat(status.admitted()).isTrue();
    }
}
