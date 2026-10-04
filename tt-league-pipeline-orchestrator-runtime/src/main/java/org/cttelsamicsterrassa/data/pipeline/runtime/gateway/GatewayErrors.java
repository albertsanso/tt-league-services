package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException.Kind;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Maps HTTP client failures to {@link GatewayException}. Messages hold the method, path, status and a short
 * {@code detail}/{@code message} from the body; never a key or a header value.
 */
final class GatewayErrors {

    private static final int MAX_DETAIL = 300;
    private static final ObjectMapper JSON = new ObjectMapper();

    private GatewayErrors() {
    }

    static GatewayException translate(String method, String path, RuntimeException failure) {
        if (failure instanceof RestClientResponseException response) {
            return fromStatus(method, path, response.getStatusCode().value(), response.getResponseBodyAsString());
        }
        if (failure instanceof ResourceAccessException) {
            return new GatewayException(Kind.UNAVAILABLE, null,
                    method + " " + path + " failed: " + failure.getClass().getSimpleName(), failure);
        }
        return protocol(method, path, "unreadable response", failure);
    }

    static GatewayException fromStatus(String method, String path, int status, String body) {
        Kind kind;
        if (status >= 500) {
            kind = Kind.UNAVAILABLE;
        } else if (status == 404) {
            kind = Kind.NOT_FOUND;
        } else if (status == 409) {
            kind = Kind.CONFLICT;
        } else if (status >= 400) {
            kind = Kind.REJECTED;
        } else {
            kind = Kind.PROTOCOL;
        }
        StringBuilder message = new StringBuilder(method).append(' ').append(path).append(" -> HTTP ").append(status);
        String detail = detail(body);
        if (detail != null) {
            message.append(": ").append(detail);
        }
        if (status == 401 || status == 403) {
            message.append(" (check the configured API key)");
        }
        return new GatewayException(kind, status, message.toString());
    }

    static GatewayException protocol(String method, String path, String reason, Throwable cause) {
        return new GatewayException(Kind.PROTOCOL, null, method + " " + path + ": " + reason, cause);
    }

    private static String detail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode field = root == null ? null : root.get("detail");
            if (field == null || field.isNull()) {
                field = root == null ? null : root.get("message");
            }
            if (field == null || field.isNull()) {
                return null;
            }
            String text = field.isTextual() ? field.asText() : field.toString();
            return text.length() <= MAX_DETAIL ? text : text.substring(0, MAX_DETAIL);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }
}
