package mtf.com.overture.queue;

import mtf.com.overture.event.EventErrorCode;
import mtf.com.overture.event.EventException;
import mtf.com.overture.event.EventService;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.queue.dto.QueueStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class QueueService {

    private static final String KEY_PREFIX = "queue:";
    // 이탈(DELETE) 호출이 누락된 사용자를 위한 안전장치. 대기열 순번/깊이와 무관하게 오직 진입
    // 시각(score) 기준으로만 정리하므로, 이미 입장 처리(rank < capacity)된 사용자를 포함해 어떤
    // 위치에 있든 5분간 자리를 지키고 있으면 함께 밀려날 수 있다 — 알려진 Phase 1 한계이며,
    // Phase 2 진입 토큰(TTL 5분)이 정식으로 해결한다.
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final EventService eventService;
    private final long capacity;

    public QueueService(StringRedisTemplate redisTemplate, EventService eventService,
                         @Value("${queue.capacity}") long capacity) {
        this.redisTemplate = redisTemplate;
        this.eventService = eventService;
        this.capacity = capacity;
    }

    public QueueStatusResponse enter(Long eventId, Long userId) {
        assertPublished(eventId, userId);
        cleanupExpired(eventId);
        redisTemplate.opsForZSet().addIfAbsent(key(eventId), String.valueOf(userId), System.currentTimeMillis());
        return buildStatus(eventId, userId);
    }

    public QueueStatusResponse getStatus(Long eventId, Long userId) {
        cleanupExpired(eventId);
        return buildStatus(eventId, userId);
    }

    public void leave(Long eventId, Long userId) {
        redisTemplate.opsForZSet().remove(key(eventId), String.valueOf(userId));
    }

    QueueStatusResponse buildStatus(Long eventId, Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(key(eventId), String.valueOf(userId));
        if (rank == null) {
            throw new QueueException(QueueErrorCode.NOT_IN_QUEUE);
        }
        Long total = redisTemplate.opsForZSet().zCard(key(eventId));
        boolean admitted = rank < capacity;
        return new QueueStatusResponse(rank + 1, total == null ? 0 : total, admitted);
    }

    void cleanupExpired(Long eventId) {
        long expireBefore = System.currentTimeMillis() - TTL.toMillis();
        redisTemplate.opsForZSet().removeRangeByScore(key(eventId), Double.NEGATIVE_INFINITY, expireBefore);
    }

    private void assertPublished(Long eventId, Long userId) {
        EventResponse event = eventService.getEvent(eventId, userId);
        if (!"PUBLISHED".equals(event.status())) {
            throw new EventException(EventErrorCode.NOT_FOUND);
        }
    }

    private String key(Long eventId) {
        return KEY_PREFIX + eventId;
    }
}
