package mtf.com.overture.config;

import mtf.com.overture.core.security.JwtAuthenticationEntryPoint;
import mtf.com.overture.core.security.JwtAuthenticationFilter;
import mtf.com.overture.core.security.JwtProvider;
import mtf.com.overture.user.CustomOAuth2UserService;
import mtf.com.overture.user.OAuth2FailureHandler;
import mtf.com.overture.user.OAuth2SuccessHandler;
import mtf.com.overture.user.RefreshTokenStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final String oauth2RedirectUri;

    public SecurityConfig(JwtProvider jwtProvider,
                           JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                           CustomOAuth2UserService customOAuth2UserService,
                           RefreshTokenStore refreshTokenStore,
                           @Value("${app.oauth2.redirect-uri}") String oauth2RedirectUri) {
        this.jwtProvider = jwtProvider;
        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtProvider);
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.customOAuth2UserService = customOAuth2UserService;
        this.refreshTokenStore = refreshTokenStore;
        this.oauth2RedirectUri = oauth2RedirectUri;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/refresh", "/oauth2/**", "/login/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(new OAuth2SuccessHandler(jwtProvider, refreshTokenStore, oauth2RedirectUri))
                        .failureHandler(new OAuth2FailureHandler(oauth2RedirectUri))
                );

        return http.build();
    }
}
