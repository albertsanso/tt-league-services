package org.cttelsamicsterrassa.data.api.rest.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingConflictException;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingNotFoundException;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingValidationException;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@SettingsOpenAPIv1Controller
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {
    private static final int MAX_BACKUP_BYTES = 1024 * 1024;
    private final SystemSettingsService service;
    private final ObjectMapper objectMapper;

    public SettingsController(SystemSettingsService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Operation(summary = "List system settings")
    public ResponseEntity<?> list(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String search) {
        try {
            SettingCategory parsed = category == null || category.isBlank()
                    ? null : SettingCategory.valueOf(category.toUpperCase());
            return ResponseEntity.ok(service.list(parsed, search).stream().map(SettingDto::from).toList());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown settings category");
        }
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update one system setting")
    public ResponseEntity<?> update(@PathVariable String key, @RequestBody SettingUpdateRequest request) {
        try {
            return ResponseEntity.ok(SettingDto.from(service.update(key, request.value(), request.version())));
        } catch (SettingConflictException e) {
            return error(HttpStatus.CONFLICT, e);
        } catch (SettingNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, e);
        } catch (SettingValidationException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    @PostMapping("/bulk")
    @Operation(summary = "Update multiple system settings")
    public ResponseEntity<?> bulk(@RequestBody BulkSettingsRequest request) {
        try {
            return ResponseEntity.ok(service.bulkUpdate(request.changes(), request.versions()).stream()
                    .map(SettingDto::from).toList());
        } catch (SettingConflictException e) {
            return error(HttpStatus.CONFLICT, e);
        } catch (SettingNotFoundException | SettingValidationException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    @PostMapping("/preview")
    @Operation(summary = "Validate pending settings without persisting")
    public ResponseEntity<?> preview(@RequestBody Map<String, Object> changes) {
        try {
            return ResponseEntity.ok(service.preview(changes).stream().map(SettingDto::from).toList());
        } catch (SettingNotFoundException | SettingValidationException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    @GetMapping(value = "/backup", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Download a versioned settings backup")
    public Map<String, Object> backup() {
        return Map.of("schemaVersion", 1, "settings", service.backup());
    }

    @PostMapping(value = "/restore", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Restore a versioned settings backup")
    public ResponseEntity<?> restore(@RequestBody byte[] payload) {
        if (payload.length > MAX_BACKUP_BYTES) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "Backup exceeds 1 MiB");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || root.get("schemaVersion") == null || root.get("schemaVersion").asInt() != 1
                    || root.get("settings") == null || !root.get("settings").isObject()) {
                return error(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported or malformed backup");
            }
            Map<String, Object> values = objectMapper.convertValue(root.get("settings"), LinkedHashMap.class);
            service.restore(values);
            return ResponseEntity.noContent().build();
        } catch (IOException | IllegalArgumentException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported or malformed backup");
        } catch (SettingValidationException | SettingNotFoundException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, Exception exception) {
        return error(status, exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message == null ? "Invalid request" : message));
    }
}
