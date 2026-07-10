package mtf.com.overture.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, TokenBlacklist tokenBlacklist) {
        this.jwtProvider = jwtProvider;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            jwtProvider.parseIfValid(token)
                    .filter(jwtProvider::isAccessToken)
                    .filter(claims -> !tokenBlacklist.isBlacklisted(token))
                    .ifPresent(claims -> {
                        Long userId = jwtProvider.getUserId(claims);
                        String role = jwtProvider.getRole(claims);
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                        var authentication = new UsernamePasswordAuthenticationToken(userId, token, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }
}
