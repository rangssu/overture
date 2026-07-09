package mtf.com.overture.user;

import tools.jackson.databind.ObjectMapper;
import mtf.com.overture.core.security.JwtProvider;
import mtf.com.overture.user.dto.RefreshRequest;
import mtf.com.overture.user.dto.RefreshResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        userId = saveUser(Role.USER).getId();
        refreshToken = jwtProvider.createRefreshToken(userId);
        redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, Duration.ofDays(7));
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("refresh:" + userId);
        if (refreshToken != null) {
            redisTemplate.delete("blacklist:" + refreshToken);
        }
        userRepository.deleteById(userId);
    }

    private User saveUser(Role role) {
        return userRepository.save(User.builder()
                .email("user-" + System.nanoTime() + "@kakao.com")
                .nickname("테스트유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("provider-" + System.nanoTime())
                .role(role)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    void refresh_rotates_refresh_token_and_returns_new_access_token() throws Exception {
        RefreshRequest request = new RefreshRequest(refreshToken);

        String responseBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        RefreshResponse response = objectMapper.readValue(responseBody, RefreshResponse.class);
        assertThat(response.refreshToken()).isNotEqualTo(refreshToken);
        assertThat(redisTemplate.opsForValue().get("refresh:" + userId)).isEqualTo(response.refreshToken());
    }

    @Test
    void refresh_reusing_the_immediately_previous_token_within_grace_period_succeeds() throws Exception {
        // 동시 요청/재시도 레이스로 인해 방금 회전되어 폐기된 토큰이 곧바로 다시 들어오는 경우는
        // 탈취가 아니라 정상적인 경합 상황일 수 있으므로, grace 기간 내에는 거부하지 않고
        // 이미 회전된 최신 토큰을 그대로 돌려줘야 한다.
        String firstResponseBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String rotatedToken = objectMapper.readValue(firstResponseBody, RefreshResponse.class).refreshToken();

        String secondResponseBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String convergedToken = objectMapper.readValue(secondResponseBody, RefreshResponse.class).refreshToken();

        assertThat(convergedToken).isEqualTo(rotatedToken);
        assertThat(redisTemplate.opsForValue().get("refresh:" + userId)).isEqualTo(rotatedToken);
    }

    @Test
    void refresh_reusing_a_token_from_two_rotations_ago_revokes_current_session() throws Exception {
        String firstResponseBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondGenerationToken = objectMapper.readValue(firstResponseBody, RefreshResponse.class).refreshToken();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(secondGenerationToken))))
                .andExpect(status().isOk());

        // 원본 refreshToken은 이제 두 세대 전 토큰이라 grace 범위를 벗어남 - 탈취 의심으로 세션 강제 종료
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));

        assertThat(redisTemplate.opsForValue().get("refresh:" + userId)).isNull();
    }

    @Test
    void refresh_uses_users_current_role_instead_of_hardcoded_value() throws Exception {
        Long organizerId = saveUser(Role.ORGANIZER).getId();
        String organizerRefreshToken = jwtProvider.createRefreshToken(organizerId);
        redisTemplate.opsForValue().set("refresh:" + organizerId, organizerRefreshToken, Duration.ofDays(7));

        try {
            String responseBody = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new RefreshRequest(organizerRefreshToken))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            RefreshResponse response = objectMapper.readValue(responseBody, RefreshResponse.class);
            assertThat(jwtProvider.getRole(response.accessToken())).isEqualTo("ORGANIZER");
        } finally {
            redisTemplate.delete("refresh:" + organizerId);
            userRepository.deleteById(organizerId);
        }
    }

    @Test
    void refresh_returns_401_when_refresh_token_does_not_match_stored_value() throws Exception {
        // JWT의 iat/exp는 초 단위(NumericDate)라 같은 유저로 같은 초에 재발급하면 서명까지 동일해질 수 있으므로,
        // 존재하지 않는 유저 ID로 토큰을 만들어 항상 저장값과 달라지도록 한다 (Redis에 refresh:{다른 userId} 키가 없어 불일치).
        String otherToken = jwtProvider.createRefreshToken(userId + 999);
        RefreshRequest request = new RefreshRequest(otherToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void logout_blacklists_refresh_token_and_returns_204() throws Exception {
        String accessToken = jwtProvider.createAccessToken(userId, "USER");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // 로그아웃 후 같은 refreshToken으로 refresh 시도하면 거부되어야 한다
        RefreshRequest request = new RefreshRequest(refreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_without_token_returns_401_with_auth_001() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void refresh_returns_401_when_given_an_access_token_instead_of_a_refresh_token() throws Exception {
        String accessToken = jwtProvider.createAccessToken(userId, "USER");
        // Redis에 저장된 값을 access token 자체로 덮어써서 storedToken 불일치가 아니라
        // isRefreshToken() 검사 때문에 거부되는지를 격리해서 검증한다.
        redisTemplate.opsForValue().set("refresh:" + userId, accessToken, Duration.ofDays(7));
        RefreshRequest request = new RefreshRequest(accessToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }
}
