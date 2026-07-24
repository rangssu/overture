package mtf.com.overture.event;

import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.SeatGradeResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class EventCache {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String EVENT_PREFIX = "event:";
    private static final String GRADES_SUFFIX = ":grades";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public EventCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<EventResponse> getEvent(Long eventId) {
        String value = redisTemplate.opsForValue().get(eventKey(eventId));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(value, EventResponse.class));
    }

    public void putEvent(Long eventId, EventResponse response) {
        redisTemplate.opsForValue().set(eventKey(eventId), objectMapper.writeValueAsString(response), TTL);
    }

    public void evictEvent(Long eventId) {
        redisTemplate.delete(eventKey(eventId));
    }

    public Optional<List<SeatGradeResponse>> getGrades(Long eventId) {
        String value = redisTemplate.opsForValue().get(gradesKey(eventId));
        if (value == null) {
            return Optional.empty();
        }
        SeatGradeResponse[] array = objectMapper.readValue(value, SeatGradeResponse[].class);
        return Optional.of(List.of(array));
    }

    public void putGrades(Long eventId, List<SeatGradeResponse> grades) {
        redisTemplate.opsForValue().set(gradesKey(eventId), objectMapper.writeValueAsString(grades), TTL);
    }

    public void evictGrades(Long eventId) {
        redisTemplate.delete(gradesKey(eventId));
    }

    private String eventKey(Long eventId) {
        return EVENT_PREFIX + eventId;
    }

    private String gradesKey(Long eventId) {
        return EVENT_PREFIX + eventId + GRADES_SUFFIX;
    }
}
