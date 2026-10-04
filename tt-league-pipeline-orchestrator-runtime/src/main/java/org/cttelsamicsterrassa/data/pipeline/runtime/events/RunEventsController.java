package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/pipeline/events")
@Tag(name = "Events")
@SecurityRequirement(name = "bearer")
class RunEventsController {

    private final RunEventBroadcaster broadcaster;

    RunEventsController(RunEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /** No replay and no {@code Last-Event-ID} support: clients refetch {@code GET /api/pipeline/runs} on reconnect. */
    @GetMapping
    @Operation(summary = "Server-Sent Events: run, step and pending-trigger changes",
            description = "Events: ready, run, step, pending-trigger; comment lines keep the connection alive.")
    @ApiResponse(responseCode = "503", description = "Too many subscribers")
    SseEmitter events() {
        return broadcaster.subscribe();
    }

    @ExceptionHandler(RunEventBroadcaster.TooManySubscribersException.class)
    ResponseEntity<ProblemDetail> tooManySubscribers(RunEventBroadcaster.TooManySubscribersException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setTitle("Service Unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
