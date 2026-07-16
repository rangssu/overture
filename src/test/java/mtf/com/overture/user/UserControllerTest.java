package mtf.com.overture.user;

import tools.jackson.databind.ObjectMapper;
import mtf.com.overture.core.security.JwtProvider;
import mtf.com.overture.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("me-" + System.nanoTime() + "@kakao.com")
                .nickname(null)
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("me-" + System.nanoTime())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
        userId = user.getId();
        accessToken = jwtProvider.createAccessToken(userId, "USER");
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
    }

    @Test
    void me_returns_the_authenticated_users_profile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.nickname").isEmpty());
    }

    @Test
    void me_without_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void updateMe_sets_the_nickname() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest("새닉네임", null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임"));
    }

    @Test
    void updateMe_rejects_an_invalid_nickname_format_with_400() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest("a", null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void updateMe_sets_the_profile_image_url() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest(null, "https://example.com/p.png");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/p.png"));
    }

    @Test
    void updateMe_rejects_an_invalid_profile_image_url_format_with_400() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest(null, "javascript:alert(1)");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void updateMe_rejects_a_duplicate_nickname_with_409() throws Exception {
        userRepository.save(User.builder()
                .email("taken-" + System.nanoTime() + "@kakao.com")
                .nickname("이미있는닉")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("taken-" + System.nanoTime())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
        UserUpdateRequest request = new UserUpdateRequest("이미있는닉", null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_001"));
    }

    @Test
    void updateMe_rejects_a_request_with_no_fields_with_400() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest(null, null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_002"));
    }
}
