package org.cttelsamicsterrassa.data.core.repository.jpa.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Import(FederatedPlayerRepositoryJpaTest.TestBeans.class)
@Transactional
class FederatedPlayerRepositoryJpaTest {

    private static final UUID RFETM_PLAYER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FCTT_PLAYER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Autowired
    private FederatedPlayerRepository federatedPlayerRepository;

    @Test
    void searchesByNameOrLicenseIdAndPreservesSourceScope() {
        federatedPlayerRepository.saveFederatedPlayer(FederatedPlayer.createExisting(
                RFETM_PLAYER_ID, ImportSource.RFETM, "Anna Smith", "RF-123", null));
        federatedPlayerRepository.saveFederatedPlayer(FederatedPlayer.createExisting(
                FCTT_PLAYER_ID, ImportSource.FCTT, "Anna Smith", "CT-123", null));

        List<FederatedPlayer> byLicense = federatedPlayerRepository.findAllFederatedPlayersByFragmentsInName(
                List.of("rf-123"));
        List<FederatedPlayer> byNameAndLicense =
                federatedPlayerRepository.findAllFederatedPlayersBySourceAndFragmentsInName(
                        ImportSource.RFETM, List.of("anna", "123"));
        List<FederatedPlayer> byScopedLicense =
                federatedPlayerRepository.findAllFederatedPlayersBySourceAndFragmentsInName(
                        ImportSource.RFETM, List.of("rf-123"));
        List<FederatedPlayer> outsideSource =
                federatedPlayerRepository.findAllFederatedPlayersBySourceAndFragmentsInName(
                        ImportSource.FCTT, List.of("rf-123"));

        assertEquals(List.of(RFETM_PLAYER_ID), byLicense.stream().map(FederatedPlayer::getId).toList());
        assertEquals("RF-123", byLicense.getFirst().getLicenseId());
        assertEquals(List.of(RFETM_PLAYER_ID), byNameAndLicense.stream().map(FederatedPlayer::getId).toList());
        assertEquals(List.of(RFETM_PLAYER_ID), byScopedLicense.stream().map(FederatedPlayer::getId).toList());
        assertEquals(List.of(), outsideSource);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        ImportRunRegistry importRunRegistry() {
            return mock(ImportRunRegistry.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
