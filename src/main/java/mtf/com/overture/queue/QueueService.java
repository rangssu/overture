package mtf.com.overture.queue;

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
    private static final String JOINED_SUFFIX = ":joined";
    private static final String SEQUENCE_SUFFIX = ":seq";
    private static final String SEQUENCE_KEY_TTL_SECONDS = String.valueOf(Duration.ofDays(1).toSeconds());
    // 이탈(DELETE) 호출이 누락된 사용자를 위한 안전장치. 대기열 순번/깊이와 무관하게 오직 진입
    // 시각(joined 전용 ZSET) 기준으로만 정리하므로, 이미 입장 처리(rank < capacity)된 사용자를
    // 포함해 어떤 위치에 있든 5분간 자리를 지키고 있으면 함께 밀려날 수 있다 — 알려진 Phase 1
    // 한계이며, Phase 2 진입 토큰(TTL 5분)이 정식으로 해결한다.
    private static final Duration TTL = Duration.ofMinutes(5);

    // 순위(rank)는 오직 원자적으로 채번한 sequence만으로 결정한다. 이전에는 진입 시각(ms)에
    // 소수부 tiebreaker를 더해 score로 썼는데, tiebreaker가 sequence % 1000이라 이벤트 누적
    // 진입자가 1000명을 넘으면 순환(wraparound)해 서로 다른 시각에 온 두 사용자가 같은 score를
    // 받을 수 있었고, INCR 호출과 currentTimeMillis() 캡처가 원자적으로 묶이지 않아 스레드
    // 스케줄링에 따라 도착 순서가 뒤바뀔 수도 있었다(Phase 1 대기열의 핵심 보장인 FIFO가 깨짐).
    // 이 스크립트는 멤버 존재 여부 확인 → sequence 채번 → rank ZSET/joined ZSET 기록을 한 번에
    // 원자적으로 수행해 두 문제를 모두 없앤다. 이미 대기열에 있는 사용자의 재진입(no-op)은
    // ZSCORE로 먼저 걸러 sequence 카운터가 낭비되지 않게 한다.
    private static final RedisScript<String> ENTER_SCRIPT = new DefaultRedisScript<>("""
            local exists = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not exists then
                local seq = redis.call('INCR', KEYS[3])
                redis.call('EXPIRE', KEYS[3], ARGV[3])
                redis.call('ZADD', KEYS[1], seq, ARGV[1])
                redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
            end
            return '1'
            """, String.class);

    // joined ZSET(입장 시각 기준)에서 TTL을 넘긴 멤버를 찾아 rank용 ZSET과 joined ZSET 양쪽에서
    // 함께 제거한다. rank ZSET의 score는 이제 진입 시각과 무관한 sequence이므로, 정리 대상 판별은
    // 반드시 joined ZSET의 시각 기준 score로만 해야 한다.
    private static final RedisScript<Long> CLEANUP_SCRIPT = new DefaultRedisScript<>("""
            local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])
            for i, member in ipairs(expired) do
                redis.call('ZREM', KEYS[1], member)
                redis.call('ZREM', KEYS[2], member)
            end
            return #expired
            """, Long.class);

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
        String member = String.valueOf(userId);
        redisTemplate.execute(ENTER_SCRIPT,
                List.of(key(eventId), joinedKey(eventId), sequenceKey(eventId)),
                member, String.valueOf(System.currentTimeMillis()), SEQUENCE_KEY_TTL_SECONDS);
        return buildStatus(eventId, userId);
    }

    public QueueStatusResponse getStatus(Long eventId, Long userId) {
        assertEventVisible(eventId, userId);
        cleanupExpired(eventId);
        return buildStatus(eventId, userId);
    }

    public void leave(Long eventId, Long userId) {
        assertEventVisible(eventId, userId);
        String member = String.valueOf(userId);
        redisTemplate.opsForZSet().remove(key(eventId), member);
        redisTemplate.opsForZSet().remove(joinedKey(eventId), member);
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
        redisTemplate.execute(CLEANUP_SCRIPT, List.of(key(eventId), joinedKey(eventId)), String.valueOf(expireBefore));
    }

    // enter()만 PUBLISHED 여부까지 검증한다. 이벤트 자체가 없거나 caller에게 보이지 않으면
    // eventService.getEvent가 던지는 EventException(NOT_FOUND)을 그대로 전파해 나머지 API와
    // 동일한 에러코드를 유지하고, "존재하고 보이지만 아직 PUBLISHED가 아닌" 경우만 대기열
    // 고유의 QueueException으로 구분해 메시지가 오해를 사지 않게 한다.
    private void assertPublished(Long eventId, Long userId) {
        EventResponse event = eventService.getEvent(eventId, userId);
        if (!EventStatus.PUBLISHED.name().equals(event.status())) {
            throw new QueueException(QueueErrorCode.EVENT_NOT_PUBLISHED);
        }
    }

    // getStatus/leave는 PUBLISHED 여부까지는 요구하지 않지만, 존재하지 않거나 caller에게 보이지
    // 않는 이벤트에 대해서는 나머지 API와 동일하게 EVENT_001로 막아야 한다(이전에는 이 두 메서드가
    // 이 검증을 건너뛰고 Redis 상태만으로 응답했다).
    private void assertEventVisible(Long eventId, Long userId) {
        eventService.getEvent(eventId, userId);
    }

    private String joinedKey(Long eventId) {
        return key(eventId) + JOINED_SUFFIX;
    }

    private String sequenceKey(Long eventId) {
        return key(eventId) + SEQUENCE_SUFFIX;
    }

    private String key(Long eventId) {
        return KEY_PREFIX + eventId;
    }
}
