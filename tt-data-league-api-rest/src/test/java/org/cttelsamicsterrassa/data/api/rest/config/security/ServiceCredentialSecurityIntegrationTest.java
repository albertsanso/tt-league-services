package org.cttelsamicsterrassa.data.api.rest.config.security;

import jakarta.servlet.Filter;
import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.api.rest.importjob.ImportJobController;
import org.cttelsamicsterrassa.data.api.rest.match.MatchController;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.FindRoundProgressQuery;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.RoundProgressReadModel;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real {@link SecurityConfig} filter chain, the real {@link ImportJobController} authorization and the
 * real {@code security.service-credentials} binding (FEAT-00101).
 */
@SpringJUnitWebConfig(ServiceCredentialSecurityIntegrationTest.TestConfig.class)
class ServiceCredentialSecurityIntegrationTest {

    private static final String JOBS = "/api/v1/administration/import/jobs";
    private static final String ROUND_PROGRESS = "/api/v1/match/round-progress?source=FCTT&season=2026-2027";
    private static final String MATCH_PROBE = "/api/v1/match/probe";
    private static final String SETTINGS_PROBE = "/api/v1/administration/settings/probe";

    private static final String IMPORTER_KEY = UUID.randomUUID().toString();
    private static final String READER_KEY = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void serviceCredentials(DynamicPropertyRegistry registry) {
        registry.add("security.service-credentials[0].name", () -> "importer");
        registry.add("security.service-credentials[0].key-sha256",
                () -> ServiceCredentialAuthenticationFilterTest.sha256Hex(IMPORTER_KEY));
        registry.add("security.service-credentials[0].permissions", () -> "imports:write");
        registry.add("security.service-credentials[1].name", () -> "reader");
        registry.add("security.service-credentials[1].key-sha256",
                () -> ServiceCredentialAuthenticationFilterTest.sha256Hex(READER_KEY));
        registry.add("security.service-credentials[1].permissions", () -> "matches:read");
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void aServiceKeyWithImportsWriteReachesTheJobsApi() throws Exception {
        mockMvc.perform(get(JOBS).header("X-API-Key", IMPORTER_KEY)).andExpect(status().isOk());
    }

    @Test
    void aServiceKeyIsForbiddenOnAdminRoleEndpoints() throws Exception {
        mockMvc.perform(get(SETTINGS_PROBE).header("X-API-Key", IMPORTER_KEY)).andExpect(status().isForbidden());
    }

    @Test
    void aServiceKeyWithoutMatchesReadIsForbiddenOnMatchEndpoints() throws Exception {
        mockMvc.perform(get(MATCH_PROBE).header("X-API-Key", IMPORTER_KEY)).andExpect(status().isForbidden());
    }

    @Test
    void aServiceKeyWithMatchesReadReachesMatchEndpointsButNotTheJobsApi() throws Exception {
        mockMvc.perform(get(MATCH_PROBE).header("X-API-Key", READER_KEY)).andExpect(status().isOk());
        mockMvc.perform(get(JOBS).header("X-API-Key", READER_KEY)).andExpect(status().isForbidden());
    }

    @Test
    void aServiceKeyWithMatchesReadGetsRoundProgress() throws Exception {
        mockMvc.perform(get(ROUND_PROGRESS).header("X-API-Key", READER_KEY)).andExpect(status().isOk());
    }

    @Test
    void aServiceKeyWithoutMatchesReadIsForbiddenOnRoundProgress() throws Exception {
        mockMvc.perform(get(ROUND_PROGRESS).header("X-API-Key", IMPORTER_KEY)).andExpect(status().isForbidden());
    }

    @Test
    void anInvalidKeyIs401() throws Exception {
        mockMvc.perform(get(JOBS).header("X-API-Key", "not-a-configured-key")).andExpect(status().isUnauthorized());
    }

    @Test
    void anApiKeyTogetherWithABearerTokenIs400() throws Exception {
        mockMvc.perform(get(JOBS)
                        .header("X-API-Key", IMPORTER_KEY)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAnonymousRequestIsStillRejected() throws Exception {
        mockMvc.perform(get(JOBS)).andExpect(status().isForbidden());
    }

    @Test
    void anAdminJwtStillReachesTheJobsApiAndAdminEndpoints() throws Exception {
        mockMvc.perform(get(JOBS).header("Authorization", "Bearer admin-token")).andExpect(status().isOk());
        mockMvc.perform(get(SETTINGS_PROBE).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void aClubManagerJwtIsForbiddenOnTheJobsApi() throws Exception {
        mockMvc.perform(get(JOBS).header("Authorization", "Bearer manager-token"))
                .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {

        @Bean
        QueryBus queryBus() {
            QueryBus queryBus = mock(QueryBus.class);
            when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(List.of()));
            when(queryBus.push(any(FindRoundProgressQuery.class))).thenReturn(DomainQueryResponse.sucessResponse(
                    new RoundProgressReadModel(ImportSource.FCTT, Season.of(2026), null, false,
                            LocalDate.of(2026, 10, 4), 7, List.of())));
            return queryBus;
        }

        @Bean
        CommandBus commandBus() {
            return mock(CommandBus.class);
        }

        @Bean
        TokenBlacklistService tokenBlacklistService() {
            return mock(TokenBlacklistService.class);
        }

        @Bean
        JwtService jwtService() {
            JwtService jwtService = mock(JwtService.class);
            when(jwtService.extractUsername("admin-token")).thenReturn("admin");
            when(jwtService.extractUsername("manager-token")).thenReturn("manager");
            when(jwtService.validateToken(any(), any())).thenReturn(true);
            return jwtService;
        }

        @Bean
        UserDetailsService userDetailsService() {
            UserDetailsService userDetailsService = mock(UserDetailsService.class);
            when(userDetailsService.loadUserByUsername(eq("admin"))).thenReturn(user("admin", UserRole.ADMIN));
            when(userDetailsService.loadUserByUsername(eq("manager")))
                    .thenReturn(user("manager", UserRole.CLUB_MANAGER));
            return userDetailsService;
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter();
        }

        @Bean
        ImportJobController importJobController() {
            return new ImportJobController();
        }

        @Bean
        MatchRepository matchRepository() {
            return mock(MatchRepository.class);
        }

        @Bean
        MatchController matchController() {
            return new MatchController();
        }

        @Bean
        MatchProbe matchProbe() {
            return new MatchProbe();
        }

        @Bean
        SettingsProbe settingsProbe() {
            return new SettingsProbe();
        }

        private static UserDetails user(String username, UserRole role) {
            List<String> authorities = new ArrayList<>();
            authorities.add("ROLE_" + role.name());
            role.permissions().forEach(permission -> authorities.add(permission.value()));
            return User.builder().username(username).password("unused")
                    .authorities(authorities.toArray(String[]::new)).build();
        }
    }

    @RestController
    static class MatchProbe {
        @GetMapping(MATCH_PROBE)
        String probe() {
            return "ok";
        }
    }

    @RestController
    static class SettingsProbe {
        @GetMapping(SETTINGS_PROBE)
        String probe() {
            return "ok";
        }
    }
}
