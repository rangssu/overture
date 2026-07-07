package mtf.com.overture.user;

import tools.jackson.databind.ObjectMapper;
import mtf.com.overture.core.security.JwtProvider;
import mtf.com.overture.user.dto.RefreshRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

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

    private static final Long USER_ID = 100L;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        refreshToken = jwtProvider.createRefreshToken(USER_ID);
        redisTemplate.opsForValue().set("refresh:" + USER_ID, refreshToken, Duration.ofDays(7));
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("refresh:" + USER_ID);
        if (refreshToken != null) {
            redisTemplate.delete("blacklist:" + refreshToken);
        }
    }

    @Test
    void refresh_returns_new_access_token_for_valid_refresh_token() throws Exception {
        RefreshRequest request = new RefreshRequest(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refresh_returns_401_when_refresh_token_does_not_match_stored_value() throws Exception {
        // JWT의 iat/exp는 초 단위(NumericDate)라 같은 유저로 같은 초에 재발급하면 서명까지 동일해질 수 있으므로,
        // 다른 유저 ID로 토큰을 만들어 항상 저장값과 달라지도록 한다 (Redis에 refresh:{다른 userId} 키가 없어 불일치).
        String otherToken = jwtProvider.createRefreshToken(USER_ID + 1);
        // Redis에는 setUp에서 만든 refreshToken이 저장돼 있고, otherToken은 다른 값이라 불일치
        RefreshRequest request = new RefreshRequest(otherToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void logout_blacklists_refresh_token_and_returns_204() throws Exception {
        String accessToken = jwtProvider.createAccessToken(USER_ID, "USER");

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
}
