package mtf.com.overture.queue;

import mtf.com.overture.event.EventRepository;
import mtf.com.overture.event.EventService;
import mtf.com.overture.event.SeatGradeRepository;
import mtf.com.overture.event.SeatRepository;
import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.SeatGradeCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

abstract class QueueIntegrationTestSupport {

    @Autowired
    protected EventService eventService;

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected SeatGradeRepository seatGradeRepository;

    @Autowired
    protected SeatRepository seatRepository;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    protected Long createdEventId;

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

    protected Authentication organizerAuth() {
        return new TestingAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));
    }

    protected Long publishedEvent() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L,
                new EventCreateRequest("콘서트", "올림픽공원", "설명", "http://example.com/p.png",
                        LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8)));
        eventService.addGrade(organizerAuth(), 1L, created.id(),
                new SeatGradeCreateRequest("VIP", 100000, 10));
        createdEventId = created.id();
        return created.id();
    }
}
