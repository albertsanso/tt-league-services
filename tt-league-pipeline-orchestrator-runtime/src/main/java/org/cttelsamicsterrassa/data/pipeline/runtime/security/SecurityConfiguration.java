package org.cttelsamicsterrassa.data.pipeline.runtime.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless bearer-token security. Tokens are platform JWTs; viewing needs authentication and triggering a run needs
 * the {@code matches:write} authority. Tokens are never logged or echoed.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    public static final String TRIGGER_AUTHORITY = "matches:write";

    @Bean
    JwtDecoder platformJwtDecoder(PipelineOrchestratorProperties.Security security) {
        return PlatformJwtDecoderFactory.create(security.jwtSecret());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(PipelineOrchestratorProperties.Security security) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!security.corsAllowedOrigins().isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(security.corsAllowedOrigins());
            cors.setAllowedMethods(List.of("GET", "POST"));
            cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Last-Event-ID"));
            cors.setAllowCredentials(false);
            cors.setMaxAge(Duration.ofHours(1));
            source.registerCorsConfiguration("/api/**", cors);
        }
        return source;
    }

    @Bean
    SecurityFilterChain pipelineSecurityFilterChain(
            HttpSecurity http, JwtDecoder decoder,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors, ObjectMapper json)
            throws Exception {
        ProblemResponses problems = new ProblemResponses(json);
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens in the Authorization header only: no cookies, so no CSRF surface.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(configurer -> configurer.configurationSource(cors))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        // Async and error dispatches continue a request that was already authorized.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pipeline/runs").hasAuthority(TRIGGER_AUTHORITY)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(
                                new PlatformJwtAuthenticationConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }
}
