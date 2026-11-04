package org.cttelsamicsterrassa.data.pipeline.core.run;

public record RunError(String code, String message) {

    public RunError {
        Checks.nonBlankMax(code, "code", 64);
        Checks.nonBlank(message, "message");
    }
}
