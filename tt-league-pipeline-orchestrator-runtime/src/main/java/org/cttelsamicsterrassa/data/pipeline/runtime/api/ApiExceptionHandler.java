package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.List;
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

    private static ProblemDetail badRequest(String detail, String field) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Bad Request");
        if (field != null) {
            problem.setProperty("field", field);
        }
        return problem;
    }
}
