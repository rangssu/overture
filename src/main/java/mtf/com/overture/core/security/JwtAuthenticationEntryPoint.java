package mtf.com.overture.core.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mtf.com.overture.core.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
        // 클라이언트 응답은 원인과 무관하게 항상 동일한 AUTH_001로 통일한다(토큰 존재 여부·만료·
        // 블랙리스트 여부를 노출하면 공격자가 세션 상태를 추측하는 데 악용할 수 있음). 다만 운영 중
        // 원인 구분이 필요하니 실제 예외는 서버 로그에는 남긴다.
        log.debug("Authentication failed for {} {}: {}", request.getMethod(), request.getRequestURI(),
                authException.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // setContentType()만으로는 charset이 지정되지 않아 컨테이너가 ISO-8859-1로 폴백하고,
        // 한글 메시지가 getWriter() 인코딩 시점에 '?'로 깨진다 - 명시적으로 UTF-8을 지정해야 한다.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiErrorResponse body = ApiErrorResponse.of(
                AuthErrorCode.INVALID_ACCESS_TOKEN.getCode(),
                AuthErrorCode.INVALID_ACCESS_TOKEN.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
