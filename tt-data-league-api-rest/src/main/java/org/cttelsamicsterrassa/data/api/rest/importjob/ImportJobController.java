package org.cttelsamicsterrassa.data.api.rest.importjob;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.importjob.FindImportJobHistoryQuery;
import org.cttelsamicsterrassa.data.core.application.importjob.FindImportJobQuery;
import org.cttelsamicsterrassa.data.core.application.importjob.SubmitImportJobCommand;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobAcceptedDto;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobRejectionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.cttelsamicsterrassa.data.api.rest.ControllerConfig.API_BASE_PATH_V1;

/**
 * Machine-friendly import API (FEAT-00100): submit an upload ZIP as an import job and follow it by its id.
 */
@RestController
@RequestMapping(API_BASE_PATH_V1 + "/administration/import/jobs")
@Tag(name = "Import Jobs API", description = "Submit upload ZIPs as import jobs and follow them to completion")
@PreAuthorize("hasRole('ADMIN')")
public class ImportJobController {

    @Autowired
    private QueryBus queryBus;
    @Autowired
    private CommandBus commandBus;

    @Operation(summary = "Submit an upload ZIP as an import job",
            description = "Validates the ZIP synchronously, then stores it and imports every ACTAS season of its "
                    + "manifest asynchronously. Returns 202 with a new job, or 200 with the existing job when the "
                    + "manifest contentSha256 matches an active, SUCCEEDED or PARTIAL job of the same source.")
    @ApiResponse(responseCode = "202", description = "Job created")
    @ApiResponse(responseCode = "200", description = "Existing job for the same content")
    @ApiResponse(responseCode = "400", description = "Invalid file, ZIP, manifest or runId")
    @ApiResponse(responseCode = "409", description = "The upload shrinks published actas")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submit(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "runId", required = false) String runId,
                                    @RequestParam(value = "allowPublishedShrink", defaultValue = "false")
                                    boolean allowPublishedShrink,
                                    Authentication authentication) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "ZIP file is required"));
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unable to read uploaded ZIP file"));
        }
        DomainCommandResponse response = commandBus.push(new SubmitImportJobCommand(file.getOriginalFilename(),
                content, Optional.ofNullable(runId), allowPublishedShrink, authentication.getName()));
        if (response.isSuccess()) {
            ImportJobAcceptedDto accepted = (ImportJobAcceptedDto) response.getResponse();
            return ResponseEntity.status(accepted.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(accepted);
        }
        ImportJobRejectionDto rejection = (ImportJobRejectionDto) response.getResponse();
        HttpStatus status = rejection.reason() == ImportJobRejectionDto.Reason.SHRINK
                ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("message", rejection.message()));
    }

    @Operation(summary = "Read an import job with the outcome of each season")
    @ApiResponse(responseCode = "404", description = "Unknown import job")
    @GetMapping(value = "/{importJobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> find(@PathVariable("importJobId") UUID importJobId) {
        DomainQueryResponse<?> response = queryBus.push(new FindImportJobQuery(importJobId));
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Import job not found: " + importJobId));
        }
        return ResponseEntity.ok(response.getResponse());
    }

    @Operation(summary = "List import jobs, most recent first",
            description = "from and to are inclusive UTC dates on the job creation time; limit defaults to 50 "
                    + "and must be between 1 and 200.")
    @ApiResponse(responseCode = "400", description = "Unknown source, from after to, or limit out of range")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(@RequestParam(value = "source", required = false) String source,
                                  @RequestParam(value = "from", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(value = "to", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(value = "limit", required = false) Integer limit) {
        FindImportJobHistoryQuery query;
        try {
            query = new FindImportJobHistoryQuery(Optional.ofNullable(source), Optional.ofNullable(from),
                    Optional.ofNullable(to), Optional.ofNullable(limit));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
        return ResponseEntity.ok(queryBus.push(query).getResponse());
    }
}
