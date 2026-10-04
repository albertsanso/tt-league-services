package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.springframework.stereotype.Component;

/**
 * JSON form of a {@link RunScope}: {"scopes":[{...}]} with the ingest scope keys, nulls omitted. Reading is strict,
 * so unknown keys fail instead of being silently dropped.
 */
@Component
class RunScopeJson {

    private static final String ROOT_KEY = "scopes";
    private static final Set<String> FILTER_KEYS =
            Set.of("category", "group", "phase", "territory", "gender", "matchDays");

    private final ObjectMapper mapper;

    RunScopeJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String write(RunScope scope) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode scopes = root.putArray(ROOT_KEY);
        for (ScopeFilter filter : scope.filters()) {
            ObjectNode node = scopes.addObject();
            put(node, "category", filter.category());
            put(node, "group", filter.group());
            put(node, "phase", filter.phase());
            put(node, "territory", filter.territory());
            put(node, "gender", filter.gender());
            if (!filter.matchDays().isEmpty()) {
                ArrayNode days = node.putArray("matchDays");
                filter.matchDays().forEach(days::add);
            }
        }
        return root.toString();
    }

    RunScope read(String json) {
        JsonNode root = parse(json);
        if (!root.isObject() || root.size() != 1 || !root.has(ROOT_KEY) || !root.get(ROOT_KEY).isArray()) {
            throw new IllegalArgumentException("Run scope JSON must be an object with a single 'scopes' array");
        }
        List<ScopeFilter> filters = new ArrayList<>();
        for (JsonNode node : root.get(ROOT_KEY)) {
            filters.add(readFilter(node));
        }
        return new RunScope(filters);
    }

    String writeIssues(List<String> issues) {
        try {
            return mapper.writeValueAsString(issues);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize import issues", e);
        }
    }

    List<String> readIssues(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Import issues JSON is not an array of strings", e);
        }
    }

    private ScopeFilter readFilter(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Each run scope entry must be an object");
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            String key = it.next().getKey();
            if (!FILTER_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown run scope key: " + key);
            }
        }
        List<Integer> matchDays = new ArrayList<>();
        JsonNode days = node.get("matchDays");
        if (days != null) {
            if (!days.isArray()) {
                throw new IllegalArgumentException("matchDays must be an array");
            }
            for (JsonNode day : days) {
                if (!day.isInt()) {
                    throw new IllegalArgumentException("matchDays must contain integers only");
                }
                matchDays.add(day.intValue());
            }
        }
        return new ScopeFilter(
                text(node, "category"), text(node, "group"), text(node, "phase"), text(node, "territory"),
                text(node, "gender"), matchDays);
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Run scope JSON is malformed", e);
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return value.textValue();
    }

    private static void put(ObjectNode node, String key, String value) {
        if (value != null) {
            node.put(key, value);
        }
    }
}
