package mtf.com.overture.core.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mtf.com.overture.core.ApiErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
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
