package mtf.com.overture.user.service;
import mtf.com.overture.user.entity.User;
import mtf.com.overture.user.repository.UserRepository;
import mtf.com.overture.user.enums.UserStatus;
import mtf.com.overture.user.enums.OauthProvider;
import mtf.com.overture.user.enums.Role;
import mtf.com.overture.user.repository.OAuthExchangeCodeStore;

import mtf.com.overture.core.security.AuthException;
import mtf.com.overture.core.security.JwtProvider;
import mtf.com.overture.user.dto.RefreshResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthExchangeCodeStore exchangeCodeStore;

    private Long userId;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("race-" + System.nanoTime() + "@kakao.com")
                .nickname("레이스테스트")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("race-" + System.nanoTime())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
        userId = user.getId();
        refreshToken = jwtProvider.createRefreshToken(userId);
        redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, Duration.ofDays(7));
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("refresh:" + userId);
        redisTemplate.delete("refresh:" + userId + ":prev");
        userRepository.deleteById(userId);
    }

    @Test
    void concurrent_refresh_calls_with_the_same_token_all_converge_to_one_rotated_token() throws Exception {
        // grace-window 정책: 동시 경합 상황에서 진 요청들은 거부되는 게 아니라,
        // 이긴 요청이 만든 새 토큰으로 함께 수렴해야 한다 (탈취가 아닌 정상 경합이므로).
        int attempts = 10;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);

        List<Future<RefreshResponse>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return authService.refresh(refreshToken);
            }));
        }

        ready.await();
        start.countDown();

        List<RefreshResponse> results = new ArrayList<>();
        for (Future<RefreshResponse> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(results).hasSize(attempts);
        assertThat(results.stream().map(RefreshResponse::refreshToken).distinct()).hasSize(1);
        assertThat(redisTemplate.opsForValue().get("refresh:" + userId))
                .isEqualTo(results.get(0).refreshToken());
    }

    @Test
    void refresh_reusing_a_stale_token_beyond_the_grace_generation_revokes_the_session() {
        RefreshResponse firstRotation = authService.refresh(refreshToken);

        authService.refresh(firstRotation.refreshToken());

        // 원본 refreshToken은 이제 두 세대 전 토큰이라 grace 범위를 벗어남
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(AuthException.class);
        assertThat(redisTemplate.opsForValue().get("refresh:" + userId)).isNull();
    }

    @Test
    void refresh_does_not_rotate_the_token_when_the_user_no_longer_exists() {
        // role 조회를 Redis 회전보다 먼저 해야 한다 - 회전 뒤에 role 조회가 실패하면
        // 클라이언트는 방금 폐기된 옛 토큰만 들고 있고 새로 발급된 토큰은 못 받아 영구 락아웃된다.
        // 공유 tearDown()의 이중 삭제를 피하기 위해 별도 유저로 진행한다.
        User ghostUser = userRepository.save(User.builder()
                .email("ghost-" + System.nanoTime() + "@kakao.com")
                .nickname("삭제될유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId("ghost-" + System.nanoTime())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
        Long ghostUserId = ghostUser.getId();
        String ghostRefreshToken = jwtProvider.createRefreshToken(ghostUserId);
        redisTemplate.opsForValue().set("refresh:" + ghostUserId, ghostRefreshToken, Duration.ofDays(7));

        try {
            userRepository.deleteById(ghostUserId);

            assertThatThrownBy(() -> authService.refresh(ghostRefreshToken))
                    .isInstanceOf(AuthException.class);

            // 회전이 일어나지 않았으므로 원본 토큰이 여전히 Redis에 그대로 남아있어야 한다.
            assertThat(redisTemplate.opsForValue().get("refresh:" + ghostUserId)).isEqualTo(ghostRefreshToken);
        } finally {
            redisTemplate.delete("refresh:" + ghostUserId);
        }
    }

    @Test
    void exchange_returns_the_token_pair_for_a_valid_code_and_consumes_it() {
        String code = exchangeCodeStore.issue("issued-access-token", "issued-refresh-token");

        RefreshResponse response = authService.exchange(code);

        assertThat(response.accessToken()).isEqualTo("issued-access-token");
        assertThat(response.refreshToken()).isEqualTo("issued-refresh-token");
        // 1회용이므로 같은 코드로 다시 교환을 시도하면 거부되어야 한다.
        assertThatThrownBy(() -> authService.exchange(code))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void exchange_rejects_an_unknown_or_expired_code() {
        assertThatThrownBy(() -> authService.exchange("no-such-code"))
                .isInstanceOf(AuthException.class);
    }
}
