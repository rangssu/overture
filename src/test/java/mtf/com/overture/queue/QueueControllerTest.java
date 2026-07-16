package mtf.com.overture.queue;

import mtf.com.overture.core.security.JwtProvider;
import mtf.com.overture.event.EventRepository;
import mtf.com.overture.event.EventService;
import mtf.com.overture.event.SeatGradeRepository;
import mtf.com.overture.event.SeatRepository;
import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.SeatGradeCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

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

    private String userToken(long userId) {
        return jwtProvider.createAccessToken(userId, "USER");
    }

    private Long publishedEventId() {
        EventResponse created = eventService.createEvent(organizerAuth(), 1L,
                new EventCreateRequest("콘서트", "올림픽공원", "설명", "http://example.com/p.png",
                        LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8)));
        eventService.addGrade(organizerAuth(), 1L, created.id(),
                new SeatGradeCreateRequest("VIP", 100000, 10));
        createdEventId = created.id();
        return created.id();
    }

    @Test
    void enter_returns_position_one_for_the_first_entrant() throws Exception {
        Long eventId = publishedEventId();

        mockMvc.perform(post("/api/v1/queue/{eventId}/enter", eventId)
                        .header("Authorization", "Bearer " + userToken(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.totalWaiting").value(1))
                .andExpect(jsonPath("$.admitted").value(true));
    }

    @Test
    void enter_without_a_token_is_rejected() throws Exception {
        Long eventId = publishedEventId();

        mockMvc.perform(post("/api/v1/queue/{eventId}/enter", eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enter_on_a_nonexistent_event_returns_404_with_event_error_code() throws Exception {
        mockMvc.perform(post("/api/v1/queue/{eventId}/enter", 999999L)
                        .header("Authorization", "Bearer " + userToken(2L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_001"));
    }

    @Test
    void status_reflects_the_entry_made_via_enter() throws Exception {
        Long eventId = publishedEventId();
        mockMvc.perform(post("/api/v1/queue/{eventId}/enter", eventId)
                .header("Authorization", "Bearer " + userToken(2L)));

        mockMvc.perform(get("/api/v1/queue/{eventId}/status", eventId)
                        .header("Authorization", "Bearer " + userToken(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.admitted").value(true));
    }

    @Test
    void status_without_entering_first_returns_404_with_queue_error_code() throws Exception {
        Long eventId = publishedEventId();

        mockMvc.perform(get("/api/v1/queue/{eventId}/status", eventId)
                        .header("Authorization", "Bearer " + userToken(2L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("QUEUE_001"));
    }

    @Test
    void leave_removes_the_entry_so_a_later_status_call_returns_404() throws Exception {
        Long eventId = publishedEventId();
        mockMvc.perform(post("/api/v1/queue/{eventId}/enter", eventId)
                .header("Authorization", "Bearer " + userToken(2L)));

        mockMvc.perform(delete("/api/v1/queue/{eventId}", eventId)
                        .header("Authorization", "Bearer " + userToken(2L)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/queue/{eventId}/status", eventId)
                        .header("Authorization", "Bearer " + userToken(2L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("QUEUE_001"));
    }
}
