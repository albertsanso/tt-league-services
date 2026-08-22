package org.cttelsamicsterrassa.data.api.rest.config.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void tokensRemainValidForTheServiceInstanceThatIssuedThem() {
        JwtService jwtService = new JwtService();
        String token = jwtService.generateToken("alice");

        assertEquals("alice", jwtService.extractUsername(token));
    }

    @Test
    void tokensIssuedBeforeAServiceRestartRemainValidWithTheStableSecret() {
        String secret = "stable-test-signing-key-0123456789";
        JwtService previousInstance = new JwtService(secret);
        String token = previousInstance.generateToken("alice");
        JwtService restartedInstance = new JwtService(secret);

        assertEquals("alice", restartedInstance.extractUsername(token));
    }

    @Test
    void tokensContainRoleAndPermissionClaims() {
        JwtService jwtService = new JwtService("stable-test-signing-key-0123456789");
        String token = jwtService.generateToken("alice", java.util.List.of("PRACTITIONER"));

        assertEquals(java.util.List.of("PRACTITIONER"), jwtService.extractRoles(token));
        assertTrue(jwtService.extractPermissions(token).contains("clubs:read"));
    }
}
