package org.cttelsamicsterrassa.data.api.rest.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset"
                        ).permitAll()
                        // Swagger UI and OpenAPI docs
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs"
                        ).permitAll()
                        // Actuator
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers("/api/v1/user/me").authenticated()
                        .requestMatchers("/api/v1/user/**").hasRole(RbacCatalog.ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/club/**")
                                .hasAuthority(RbacCatalog.CLUBS_READ)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/club/**")
                                .hasRole(RbacCatalog.ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/player/**")
                                .hasAuthority(RbacCatalog.PLAYERS_READ)
                        .requestMatchers(HttpMethod.GET, "/api/v1/match/**")
                                .hasAuthority(RbacCatalog.MATCHES_READ)
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
