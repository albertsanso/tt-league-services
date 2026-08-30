package org.cttelsamicsterrassa.data.api.rest.importjob;

import io.swagger.v3.oas.annotations.Operation;
import org.cttelsamicsterrassa.data.core.domain.shared.port.ImportSourcesPort;
import org.cttelsamicsterrassa.data.core.domain.shared.port.ImportJobsPort;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportJobRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@ImportJobOpenAPIv1Controller
@PreAuthorize("hasRole('ADMIN')")
public class ImportJobController {

    private final ImportSourcesPort importSourcesPort;
    private final ImportJobsPort importJobsPort;

    @Autowired
    public ImportJobController(ImportSourcesPort importSourcesPort, ImportJobsPort importJobsPort) {
        this.importSourcesPort = importSourcesPort;
        this.importJobsPort = importJobsPort;
    }

    public ImportJobController(ImportSourcesPort importSourcesPort) {
        this(importSourcesPort, new InMemoryImportJobsService());
    }

    @GetMapping("/sources")
    @Operation(summary = "List supported import sources")
    public ResponseEntity<?> listSources() {
        List<ImportSourceDto> sources = importSourcesPort.listSupportedSources()
                .stream()
                .map(ImportSourceDto::from)
                .toList();
        return ResponseEntity.ok(sources);
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody ImportJobRequest request) {
        return ResponseEntity.ok(importJobsPort.preview(request));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<?> validate(@PathVariable UUID id) { return ResponseEntity.ok(importJobsPort.validate(id)); }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> start(@PathVariable UUID id) { return ResponseEntity.ok(importJobsPort.start(id)); }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID id) { return ResponseEntity.ok(importJobsPort.cancel(id)); }

    @PostMapping("/{id}/rollback")
    public ResponseEntity<?> rollback(@PathVariable UUID id) { return ResponseEntity.ok(importJobsPort.rollback(id)); }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        return importJobsPort.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<?> history(@RequestParam(defaultValue = "") String query,
                                     @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(importJobsPort.history(query, limit));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return error(org.springframework.http.HttpStatus.BAD_REQUEST, "The import request is invalid");
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return error(org.springframework.http.HttpStatus.NOT_FOUND, "Import job not found");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return error(org.springframework.http.HttpStatus.CONFLICT, "Import job is not in a valid state");
    }

    private ResponseEntity<Map<String, String>> error(org.springframework.http.HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message == null ? "Invalid request" : message));
    }
}
