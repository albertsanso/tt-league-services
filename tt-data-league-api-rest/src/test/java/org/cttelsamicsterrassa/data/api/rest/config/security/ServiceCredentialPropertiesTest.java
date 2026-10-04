package org.cttelsamicsterrassa.data.api.rest.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCredentialPropertiesTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Test
    void bindsFromTheDocumentedEnvironmentVariables() {
        var environment = new SystemEnvironmentPropertySource("systemEnvironment", Map.of(
                "SECURITY_SERVICECREDENTIALS_0_NAME", "orchestrator",
                "SECURITY_SERVICECREDENTIALS_0_KEYSHA256", HASH_A,
                "SECURITY_SERVICECREDENTIALS_0_PERMISSIONS", "imports:write,matches:read"));

        var properties = new Binder(ConfigurationPropertySources.from(environment))
                .bind("security", ServiceCredentialProperties.class).get();

        var entry = properties.serviceCredentials().get(0);
        assertEquals("orchestrator", entry.name());
        assertEquals(HASH_A, entry.keySha256());
        assertEquals(Set.of("imports:write", "matches:read"), entry.permissions());
    }

    @Test
    void aMalformedEnvironmentEntryFailsBinding() {
        var environment = new SystemEnvironmentPropertySource("systemEnvironment", Map.of(
                "SECURITY_SERVICECREDENTIALS_0_NAME", "orchestrator",
                "SECURITY_SERVICECREDENTIALS_0_KEYSHA256", "not-a-hash",
                "SECURITY_SERVICECREDENTIALS_0_PERMISSIONS", "imports:write"));

        assertThrows(BindException.class, () -> new Binder(ConfigurationPropertySources.from(environment))
                .bind("security", ServiceCredentialProperties.class));
    }

    @Test
    void aMissingListConfiguresNoCredential() {
        assertTrue(new ServiceCredentialProperties(null).serviceCredentials().isEmpty());
    }

    @Test
    void aValidEntryNormalisesAndDecodesTheHash() {
        var entry = new ServiceCredentialProperties.Entry("orchestrator", "AB".repeat(32), Set.of("imports:write"));

        assertEquals("ab".repeat(32), entry.keySha256());
        byte[] expected = new byte[32];
        Arrays.fill(expected, (byte) 0xAB);
        assertArrayEquals(expected, entry.keyHash());
    }

    @Test
    void keyHashReturnsACopy() {
        var entry = new ServiceCredentialProperties.Entry("orchestrator", HASH_A, Set.of("imports:write"));

        entry.keyHash()[0] = 0;

        assertEquals((byte) 0xAA, entry.keyHash()[0]);
    }

    @Test
    void invalidNamesAreRejected() {
        for (String name : new String[]{null, "", "  ", "Upper", "has space", "under_score", "a".repeat(41)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ServiceCredentialProperties.Entry(name, HASH_A, Set.of("imports:write")),
                    "name: " + name);
        }
    }

    @Test
    void aHashThatIsNotSixtyFourHexCharactersIsRejectedWithoutEchoingIt() {
        for (String hash : new String[]{null, "", "a".repeat(63), "a".repeat(65), "g".repeat(64)}) {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> new ServiceCredentialProperties.Entry("orchestrator", hash, Set.of("imports:write")));
            assertTrue(exception.getMessage().contains("orchestrator"));
            assertTrue(exception.getMessage().contains("64 hexadecimal"));
            if (hash != null && !hash.isEmpty()) {
                assertFalse(exception.getMessage().contains(hash));
            }
        }
    }

    @Test
    void missingOrUnknownPermissionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceCredentialProperties.Entry("orchestrator", HASH_A, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceCredentialProperties.Entry("orchestrator", HASH_A, Set.of()));
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new ServiceCredentialProperties.Entry("orchestrator", HASH_A, Set.of("imports:wrte")));
        assertTrue(exception.getMessage().contains("unknown permission 'imports:wrte'"));
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceCredentialProperties.Entry("orchestrator", HASH_A, Set.of("ADMIN")));
    }

    @Test
    void duplicateNamesAndDuplicateHashesAreRejected() {
        var first = new ServiceCredentialProperties.Entry("orchestrator", HASH_A, Set.of("imports:write"));
        var sameName = new ServiceCredentialProperties.Entry("orchestrator", HASH_B, Set.of("matches:read"));
        var sameHash = new ServiceCredentialProperties.Entry("ingest", HASH_A.toUpperCase(), Set.of("matches:read"));

        var duplicateName = assertThrows(IllegalArgumentException.class,
                () -> new ServiceCredentialProperties(List.of(first, sameName)));
        assertTrue(duplicateName.getMessage().contains("duplicate name"));
        var duplicateHash = assertThrows(IllegalArgumentException.class,
                () -> new ServiceCredentialProperties(List.of(first, sameHash)));
        assertTrue(duplicateHash.getMessage().contains("duplicate key-sha256"));
        assertFalse(duplicateHash.getMessage().contains(HASH_A));
    }
}
