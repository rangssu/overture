package mtf.com.overture.event;

import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.SeatGradeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventCacheTest {

    @Autowired
    private EventCache eventCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("event:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private EventResponse buildEventResponse(Long id) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 9, 0);
        return new EventResponse(id, "콘서트", "올림픽공원", "설명", "http://example.com/p.png",
                now, now.plusDays(7), "PUBLISHED", 1L, now);
    }

    @Test
    void getEvent_returns_empty_when_nothing_is_cached() {
        Optional<EventResponse> result = eventCache.getEvent(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void putEvent_then_getEvent_returns_the_same_data() {
        EventResponse response = buildEventResponse(1L);

        eventCache.putEvent(1L, response);
        Optional<EventResponse> result = eventCache.getEvent(1L);

        assertThat(result).contains(response);
    }

    @Test
    void evictEvent_removes_the_cached_entry() {
        eventCache.putEvent(1L, buildEventResponse(1L));

        eventCache.evictEvent(1L);

        assertThat(eventCache.getEvent(1L)).isEmpty();
    }

    @Test
    void putGrades_then_getGrades_returns_the_same_list() {
        List<SeatGradeResponse> grades = List.of(
                new SeatGradeResponse(1L, 1L, "VIP", 150000, 50, 50),
                new SeatGradeResponse(2L, 1L, "R", 100000, 100, 100)
        );

        eventCache.putGrades(1L, grades);
        Optional<List<SeatGradeResponse>> result = eventCache.getGrades(1L);

        assertThat(result).contains(grades);
    }

    @Test
    void evictGrades_removes_the_cached_entry() {
        eventCache.putGrades(1L, List.of(new SeatGradeResponse(1L, 1L, "VIP", 150000, 50, 50)));

        eventCache.evictGrades(1L);

        assertThat(eventCache.getGrades(1L)).isEmpty();
    }
}
