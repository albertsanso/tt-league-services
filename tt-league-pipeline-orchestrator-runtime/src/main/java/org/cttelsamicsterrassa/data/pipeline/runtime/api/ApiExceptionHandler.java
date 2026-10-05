package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollPolicyException;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollScheduleException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.IllegalMatchDayTransitionException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayNotFoundException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.StaleMatchDayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Request errors as {@link ProblemDetail}; no stack traces or internal class names reach the response. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(InvalidRequestException e) {
        return badRequest(e.getMessage(), e.field());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
        ProblemDetail problem = badRequest("Invalid request: " + String.join("; ", errors),
                e.getBindingResult().getFieldErrors().isEmpty() ? null
                        : e.getBindingResult().getFieldErrors().get(0).getField());
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException e) {
        String field = null;
        String detail = "The request body is missing or is not valid JSON";
        if (e.getCause() instanceof InvalidFormatException format && !format.getPath().isEmpty()) {
            field = format.getPath().get(format.getPath().size() - 1).getFieldName();
            detail = "Invalid value for " + field;
        } else if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            field = mismatch.getPath().get(mismatch.getPath().size() - 1).getFieldName();
            detail = "Invalid value for " + field;
        }
        return badRequest(detail, field);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest("Invalid value for " + e.getName(), e.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParameter(MissingServletRequestParameterException e) {
        return badRequest("Missing parameter " + e.getParameterName(), e.getParameterName());
    }

    @ExceptionHandler(RunNotFoundException.class)
    ProblemDetail notFound(RunNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(MatchDayNotFoundException.class)
    ProblemDetail matchDayNotFound(MatchDayNotFoundException e) {
        return coded(HttpStatus.NOT_FOUND, "MATCH_DAY_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(IllegalMatchDayTransitionException.class)
    ProblemDetail illegalTransition(IllegalMatchDayTransitionException e) {
        return coded(HttpStatus.CONFLICT, "ILLEGAL_TRANSITION", e.getMessage());
    }

    @ExceptionHandler(StaleMatchDayException.class)
    ProblemDetail staleMatchDay(StaleMatchDayException e) {
        return coded(HttpStatus.CONFLICT, "STALE_MATCH_DAY",
                "The match day changed while it was being updated; reload it and try again");
    }

    @ExceptionHandler(PollScheduleNotFoundException.class)
    ProblemDetail pollScheduleNotFound(PollScheduleNotFoundException e) {
        return coded(HttpStatus.NOT_FOUND, "POLL_SCHEDULE_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(PollScheduleNotStoppedException.class)
    ProblemDetail pollScheduleNotStopped(PollScheduleNotStoppedException e) {
        return coded(HttpStatus.CONFLICT, "NOT_STOPPED", e.getMessage());
    }

    @ExceptionHandler(StalePollScheduleException.class)
    ProblemDetail stalePollSchedule(StalePollScheduleException e) {
        return coded(HttpStatus.CONFLICT, "STALE_SCHEDULE",
                "The poll schedule changed while it was being updated; reload it and try again");
    }

    @ExceptionHandler(StalePollPolicyException.class)
    ProblemDetail stalePollPolicy(StalePollPolicyException e) {
        return coded(HttpStatus.CONFLICT, "STALE_POLICY",
                "The polling policy of " + e.source() + " changed; reload it and try again");
    }

    private static ProblemDetail coded(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }

    private static ProblemDetail badRequest(String detail, String field) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Bad Request");
        if (field != null) {
            problem.setProperty("field", field);
        }
        return problem;
    }
}
