package mtf.com.overture.queue;

import mtf.com.overture.core.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueueControllerTest extends QueueIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    private String userToken(long userId) {
        return jwtProvider.createAccessToken(userId, "USER");
    }

    @Test
    void enter_returns_position_one_for_the_first_entrant() throws Exception {
        Long eventId = publishedEvent();

        mockMvc.perform(post("/api/v1/queue/{eventId}/enter", eventId)
                        .header("Authorization", "Bearer " + userToken(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.totalWaiting").value(1))
                .andExpect(jsonPath("$.admitted").value(true));
    }

    @Test
    void enter_without_a_token_is_rejected() throws Exception {
        Long eventId = publishedEvent();

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
        Long eventId = publishedEvent();
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
        Long eventId = publishedEvent();

        mockMvc.perform(get("/api/v1/queue/{eventId}/status", eventId)
                        .header("Authorization", "Bearer " + userToken(2L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("QUEUE_001"));
    }

    @Test
    void leave_removes_the_entry_so_a_later_status_call_returns_404() throws Exception {
        Long eventId = publishedEvent();
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
