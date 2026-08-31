package org.cttelsamicsterrassa.data.api.rest.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@SettingsOpenAPIv1Controller
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {
    private static final int MAX_BACKUP_BYTES = 1024 * 1024;

    private final ObjectMapper objectMapper;

    public SettingsController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    private ResponseEntity<Map<String, String>> error(HttpStatus status, Exception exception) {
        return error(status, exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message == null ? "Invalid request" : message));
    }
}
