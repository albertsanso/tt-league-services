package org.cttelsamicsterrassa.data.api.rest.importresource;

import io.swagger.v3.oas.annotations.Operation;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.importresource.find.FindPendingImportsInfoQuery;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@ImportResourceAPIv1Controller
public class ImportResourceController {

    @Autowired
    private QueryBus queryBus;
    @Autowired
    private ResourceUploadService resourceUploadService;


    @Operation(summary = "Upload a ZIP import resource")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadZipFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "ZIP file is required"));
        }

        try {
            resourceUploadService.uploadAndTriggerAsyncLoad(file.getOriginalFilename(), file.getBytes());
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("message", "ZIP file accepted for processing"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unable to read uploaded ZIP file"));
        }
    }

    @Operation(summary = "List sources with pending imports")
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listSourcesWithPendingImports() {
        return ResponseEntity.ok(queryBus.push(new FindPendingImportsInfoQuery()));
    }
}
