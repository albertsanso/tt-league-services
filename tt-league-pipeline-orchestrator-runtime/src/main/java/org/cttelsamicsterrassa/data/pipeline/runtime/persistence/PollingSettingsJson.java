package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.springframework.stereotype.Component;

/**
 * JSON form of {@link PollingSettings}: ISO-8601 durations and integers under the setting names. Reading is strict:
 * a missing, unknown or malformed value fails instead of silently taking a default.
 */
@Component
class PollingSettingsJson {

    private static final Set<String> KEYS = Set.of("matchDay", "matchDayStartOffset", "dayAfter", "daysTwoToSeven",
            "open", "overdue", "overdueStopAfterDays", "fullRefresh", "noChangeThreshold", "recentMatchDays");

    private final ObjectMapper mapper;

    PollingSettingsJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String write(PollingSettings settings) {
        ObjectNode node = mapper.createObjectNode();
        node.put("matchDay", settings.matchDay().toString());
        node.put("matchDayStartOffset", settings.matchDayStartOffset().toString());
        node.put("dayAfter", settings.dayAfter().toString());
        node.put("daysTwoToSeven", settings.daysTwoToSeven().toString());
        node.put("open", settings.open().toString());
        node.put("overdue", settings.overdue().toString());
        node.put("overdueStopAfterDays", settings.overdueStopAfterDays());
        node.put("fullRefresh", settings.fullRefresh().toString());
        node.put("noChangeThreshold", settings.noChangeThreshold());
        node.put("recentMatchDays", settings.recentMatchDays());
        return node.toString();
    }

    PollingSettings read(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Polling settings JSON is malformed", e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Polling settings JSON must be an object");
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext();) {
            String key = it.next().getKey();
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown polling setting: " + key);
            }
        }
        return new PollingSettings(duration(root, "matchDay"), duration(root, "matchDayStartOffset"),
                duration(root, "dayAfter"), duration(root, "daysTwoToSeven"), duration(root, "open"),
                duration(root, "overdue"), integer(root, "overdueStopAfterDays"), duration(root, "fullRefresh"),
                integer(root, "noChangeThreshold"), integer(root, "recentMatchDays"));
    }

    private static Duration duration(JsonNode root, String key) {
        JsonNode value = root.get(key);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(key + " must be an ISO-8601 duration string");
        }
        try {
            return Duration.parse(value.textValue());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(key + " is not an ISO-8601 duration", e);
        }
    }

    private static int integer(JsonNode root, String key) {
        JsonNode value = root.get(key);
        if (value == null || !value.isInt()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return value.intValue();
    }
}
