package mtf.com.overture.queue;

import mtf.com.overture.event.EventErrorCode;
import mtf.com.overture.event.EventException;
import mtf.com.overture.event.EventService;
import mtf.com.overture.event.EventStatus;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.queue.dto.QueueStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class QueueService {

    private static final String KEY_PREFIX = "queue:";
    private static final String SEQUENCE_SUFFIX = ":seq";
    // 이탈(DELETE) 호출이 누락된 사용자를 위한 안전장치. 대기열 순번/깊이와 무관하게 오직 진입
    // 시각(score) 기준으로만 정리하므로, 이미 입장 처리(rank < capacity)된 사용자를 포함해 어떤
    // 위치에 있든 5분간 자리를 지키고 있으면 함께 밀려날 수 있다 — 알려진 Phase 1 한계이며,
    // Phase 2 진입 토큰(TTL 5분)이 정식으로 해결한다.
    private static final Duration TTL = Duration.ofMinutes(5);

    // ZRANK와 ZCARD를 한 번의 원자적 호출로 묶어, 두 호출 사이에 다른 사용자가 이탈/정리되며
    // position과 totalWaiting이 논리적으로 모순되는(예: "6명 중 6번째") 상태가 노출되는 것을 막는다.
    private static final RedisScript<String> STATUS_SCRIPT = new DefaultRedisScript<>("""
            local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
            if rank == false then
                rank = -1
            end
            local total = redis.call('ZCARD', KEYS[1])
            return rank .. ':' .. total
            """, String.class);

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
        redisTemplate.opsForZSet().addIfAbsent(key(eventId), String.valueOf(userId), entryScore(eventId));
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
        String result = redisTemplate.execute(STATUS_SCRIPT, List.of(key(eventId)), String.valueOf(userId));
        String[] parts = result.split(":");
        long rank = Long.parseLong(parts[0]);
        long total = Long.parseLong(parts[1]);
        if (rank == -1) {
            throw new QueueException(QueueErrorCode.NOT_IN_QUEUE);
        }
        boolean admitted = rank < capacity;
        return new QueueStatusResponse(rank + 1, total, admitted);
    }

    void cleanupExpired(Long eventId) {
        long expireBefore = System.currentTimeMillis() - TTL.toMillis();
        redisTemplate.opsForZSet().removeRangeByScore(key(eventId), Double.NEGATIVE_INFINITY, expireBefore);
    }

    private void assertPublished(Long eventId, Long userId) {
        EventResponse event = eventService.getEvent(eventId, userId);
        if (!EventStatus.PUBLISHED.name().equals(event.status())) {
            throw new EventException(EventErrorCode.NOT_FOUND);
        }
    }

    private double entryScore(Long eventId) {
        Long sequence = redisTemplate.opsForValue().increment(sequenceKey(eventId));
        long tiebreaker = (sequence == null ? 0L : sequence) % 1000;
        return System.currentTimeMillis() + (tiebreaker / 1000.0);
    }

    private String sequenceKey(Long eventId) {
        return key(eventId) + SEQUENCE_SUFFIX;
    }

    private String key(Long eventId) {
        return KEY_PREFIX + eventId;
    }
}
