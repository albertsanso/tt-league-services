package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(RunEventsController.class)
@Import({SecurityConfiguration.class, RunEventsControllerTest.Settings.class})
class RunEventsControllerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @TestConfiguration
    static class Settings {

        @Bean
        PipelineOrchestratorProperties.Security security() {
            return new PipelineOrchestratorProperties.Security(SECRET, List.of());
        }
    }

    @Autowired
    MockMvc mvc;
    @MockitoBean
    RunEventBroadcaster broadcaster;

    private static String token() throws Exception {
        com.nimbusds.jwt.SignedJWT jwt = new com.nimbusds.jwt.SignedJWT(
                new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("alice")
                        .expirationTime(java.util.Date.from(Instant.now().plusSeconds(600))).build());
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/pipeline/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void streamsThroughAnAsyncEmitter() throws Exception {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(1).toMillis());
        when(broadcaster.subscribe()).thenReturn(emitter);

        var started = mvc.perform(get("/api/pipeline/events").header("Authorization", "Bearer " + token()))
                .andExpect(request().asyncStarted()).andReturn();
        emitter.complete();

        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());
    }

    @Test
    void answers503WhenTheSubscriberCapIsReached() throws Exception {
        when(broadcaster.subscribe()).thenThrow(new RunEventBroadcaster.TooManySubscribersException(1));

        mvc.perform(get("/api/pipeline/events").header("Authorization", "Bearer " + token()))
                .andExpect(status().isServiceUnavailable());
    }
}
