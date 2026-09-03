package org.cttelsamicsterrassa.data.core.domain.resource.model;

public final class ResourceKeys {

    private static final String DATA_IMPORT_KEY_TEMPLATE = "data-import > %s > %s";

    private ResourceKeys() {}

    public static String dataImportKey(String source, String assetType) {
        return DATA_IMPORT_KEY_TEMPLATE.formatted(source, assetType);
    }
}
