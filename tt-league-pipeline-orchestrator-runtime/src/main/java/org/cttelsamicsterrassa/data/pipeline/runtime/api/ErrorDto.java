package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;

public record ErrorDto(String code, String message) {

    static ErrorDto from(RunError error) {
        return error == null ? null : new ErrorDto(error.code(), error.message());
    }
}
