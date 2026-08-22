package org.cttelsamicsterrassa.data.api.rest.config.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void tokensRemainValidForTheServiceInstanceThatIssuedThem() {
        JwtService jwtService = new JwtService();
        String token = jwtService.generateToken("alice");

        assertEquals("alice", jwtService.extractUsername(token));
    }

    @Test
    void tokensIssuedBeforeAServiceRestartAreRejected() {
        JwtService previousInstance = new JwtService();
        String token = previousInstance.generateToken("alice");
        JwtService restartedInstance = new JwtService();

        assertThrows(JwtException.class, () -> restartedInstance.extractUsername(token));
    }
}
