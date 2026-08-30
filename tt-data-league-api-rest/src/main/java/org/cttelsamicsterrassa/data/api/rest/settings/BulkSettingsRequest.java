package org.cttelsamicsterrassa.data.api.rest.settings;

import java.util.Map;

public record BulkSettingsRequest(Map<String, Object> changes, Map<String, Long> versions) {
}
