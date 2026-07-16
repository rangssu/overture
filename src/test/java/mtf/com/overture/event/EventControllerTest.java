package mtf.com.overture.event;

import mtf.com.overture.core.security.JwtProvider;
import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventUpdateRequest;
import mtf.com.overture.event.dto.SeatGradeCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String organizerToken;
    private String userToken;
    private String adminToken;
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

    private String organizerToken() {
        if (organizerToken == null) {
            organizerToken = jwtProvider.createAccessToken(1L, "ORGANIZER");
        }
        return organizerToken;
    }

    private String userToken() {
        if (userToken == null) {
            userToken = jwtProvider.createAccessToken(2L, "USER");
        }
        return userToken;
    }

    private String adminToken() {
        if (adminToken == null) {
            adminToken = jwtProvider.createAccessToken(9L, "ADMIN");
        }
        return adminToken;
    }

    private EventCreateRequest validRequest() {
        return new EventCreateRequest("콘서트", "올림픽공원", "설명", "http://example.com/p.png",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8));
    }

    @Test
    void create_registers_a_draft_event_for_an_organizer() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + organizerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andDo(result -> {
                    var body = objectMapper.readTree(result.getResponse().getContentAsString());
                    createdEventId = body.get("id").asLong();
                });
    }

    @Test
    void create_rejects_a_plain_user_with_403() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EVENT_002"));
    }

    @Test
    void create_without_a_token_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void detail_is_accessible_without_authentication_for_a_published_event() throws Exception {
        Event event = eventRepository.saveAndFlush(Event.builder()
                .title("콘서트").venue("올림픽공원")
                .saleStartAt(LocalDateTime.now()).saleEndAt(LocalDateTime.now().plusDays(7))
                .status(EventStatus.PUBLISHED).createdBy(1L).createdAt(LocalDateTime.now())
                .build());
        createdEventId = event.getId();

        mockMvc.perform(get("/api/v1/events/" + event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()));
    }

    @Test
    void detail_returns_404_for_a_draft_event_viewed_by_a_non_owner() throws Exception {
        Event event = eventRepository.saveAndFlush(Event.builder()
                .title("콘서트").venue("올림픽공원")
                .saleStartAt(LocalDateTime.now()).saleEndAt(LocalDateTime.now().plusDays(7))
                .status(EventStatus.DRAFT).createdBy(1L).createdAt(LocalDateTime.now())
                .build());
        createdEventId = event.getId();

        mockMvc.perform(get("/api/v1/events/" + event.getId())
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_001"));
    }

    @Test
    void update_changes_the_event_for_its_owner() throws Exception {
        Event event = eventRepository.saveAndFlush(Event.builder()
                .title("콘서트").venue("올림픽공원")
                .saleStartAt(LocalDateTime.now()).saleEndAt(LocalDateTime.now().plusDays(7))
                .status(EventStatus.DRAFT).createdBy(1L).createdAt(LocalDateTime.now())
                .build());
        createdEventId = event.getId();
        EventUpdateRequest update = new EventUpdateRequest("새 제목", null, null, null, null, null);

        mockMvc.perform(patch("/api/v1/events/" + event.getId())
                        .header("Authorization", "Bearer " + organizerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 제목"));
    }

    @Test
    void addGrade_creates_seats_and_publishes_the_event() throws Exception {
        Event event = eventRepository.saveAndFlush(Event.builder()
                .title("콘서트").venue("올림픽공원")
                .saleStartAt(LocalDateTime.now()).saleEndAt(LocalDateTime.now().plusDays(7))
                .status(EventStatus.DRAFT).createdBy(1L).createdAt(LocalDateTime.now())
                .build());
        createdEventId = event.getId();
        SeatGradeCreateRequest request = new SeatGradeCreateRequest("VIP", 150000, 3);

        mockMvc.perform(post("/api/v1/events/" + event.getId() + "/grades")
                        .header("Authorization", "Bearer " + organizerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3));

        mockMvc.perform(get("/api/v1/events/" + event.getId() + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.VIP.length()").value(3));
    }

    @Test
    void list_only_returns_published_events_without_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk());
    }

    @Test
    void detail_shows_a_draft_event_to_an_admin_who_is_not_the_owner() throws Exception {
        Event event = eventRepository.saveAndFlush(Event.builder()
                .title("콘서트").venue("올림픽공원")
                .saleStartAt(LocalDateTime.now()).saleEndAt(LocalDateTime.now().plusDays(7))
                .status(EventStatus.DRAFT).createdBy(1L).createdAt(LocalDateTime.now())
                .build());
        createdEventId = event.getId();

        mockMvc.perform(get("/api/v1/events/" + event.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()));
    }

}
