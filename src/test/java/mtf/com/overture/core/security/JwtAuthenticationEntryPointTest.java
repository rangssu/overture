package mtf.com.overture.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses a real embedded Tomcat (RANDOM_PORT) and a real socket HTTP client rather than
 * MockMvc, because MockMvc's MockHttpServletResponse defaults its character encoding to
 * UTF-8 and does not reproduce Tomcat's actual ISO-8859-1 fallback when a response
 * writer is obtained without an explicit charset - the exact condition this test guards
 * against.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtAuthenticationEntryPointTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void an_unauthenticated_request_gets_a_401_with_the_korean_message_intact() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type")).get().asString().containsIgnoringCase("UTF-8");
        assertThat(response.body()).contains(AuthErrorCode.INVALID_ACCESS_TOKEN.getMessage());
    }
}
