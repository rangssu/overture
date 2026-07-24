package mtf.com.overture.user.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OAuthExchangeCodeStoreTest {

    @Autowired
    private OAuthExchangeCodeStore store;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("oauth_exchange:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void issue_returns_a_different_code_on_each_call() {
        String first = store.issue("a", "b");
        String second = store.issue("a", "b");

        assertThat(first).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void redeem_returns_the_token_pair_that_was_issued() {
        String code = store.issue("access-token", "refresh-token");

        Optional<OAuthExchangeCodeStore.TokenPair> result = store.redeem(code);

        assertThat(result).isPresent();
        assertThat(result.get().accessToken()).isEqualTo("access-token");
        assertThat(result.get().refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void redeem_consumes_the_code_so_it_cannot_be_used_twice() {
        String code = store.issue("access-token", "refresh-token");

        store.redeem(code);
        Optional<OAuthExchangeCodeStore.TokenPair> second = store.redeem(code);

        assertThat(second).isEmpty();
    }

    @Test
    void redeem_returns_empty_when_the_code_does_not_exist() {
        Optional<OAuthExchangeCodeStore.TokenPair> result = store.redeem("no-such-code");

        assertThat(result).isEmpty();
    }
}
