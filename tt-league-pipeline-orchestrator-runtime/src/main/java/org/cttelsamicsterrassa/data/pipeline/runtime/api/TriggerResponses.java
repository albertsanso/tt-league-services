package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Maps the outcomes of {@code TriggerRun} to the HTTP contract shared by {@code POST /api/pipeline/runs} and the
 * match-day refresh: 201 with the run's {@code Location} when a run was created, 202 when a trigger was queued, 409
 * when a source was rejected and 422 when no scope was available.
 */
final class TriggerResponses {

    private TriggerResponses() {
    }

    static ResponseEntity<Object> of(List<Outcome> outcomes, RunDtoMapper mapper) {
        TriggerResponse response =
                new TriggerResponse(outcomes.stream().map(outcome -> result(outcome, mapper)).toList());
        Outcome.Created firstCreated = outcomes.stream().filter(Outcome.Created.class::isInstance)
                .map(Outcome.Created.class::cast).findFirst().orElse(null);
        if (firstCreated != null) {
            URI location = URI.create("/api/pipeline/runs/" + firstCreated.run().id());
            return ResponseEntity.created(location).body(response);
        }
        if (outcomes.stream().anyMatch(Outcome.Queued.class::isInstance)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        HttpStatus status = outcomes.stream().anyMatch(Outcome.Rejected.class::isInstance)
                ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(status, response));
    }

    private static TriggerResultDto result(Outcome outcome, RunDtoMapper mapper) {
        return switch (outcome) {
            case Outcome.Created created -> new TriggerResultDto(created.source().name(), "CREATED", null, null,
                    mapper.summary(created.run(), List.of()), null);
            case Outcome.Queued queued -> new TriggerResultDto(queued.source().name(), "QUEUED", null,
                    "Source " + queued.source() + " has an active run; the trigger is queued behind it", null,
                    queued.activeRunId());
            case Outcome.Rejected rejected -> new TriggerResultDto(rejected.source().name(), "REJECTED",
                    rejected.code(), rejected.message(), null, rejected.activeRunId());
            case Outcome.Unavailable unavailable -> new TriggerResultDto(unavailable.source().name(),
                    "UNAVAILABLE", unavailable.code(), unavailable.message(), null, null);
        };
    }

    private static ProblemDetail problem(HttpStatus status, TriggerResponse response) {
        List<String> messages = new ArrayList<>();
        String code = null;
        for (TriggerResultDto result : response.results()) {
            if (result.message() != null) {
                messages.add(result.message());
            }
            if (code == null) {
                code = result.code();
            }
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, String.join("; ", messages));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("results", response.results());
        return problem;
    }
}
