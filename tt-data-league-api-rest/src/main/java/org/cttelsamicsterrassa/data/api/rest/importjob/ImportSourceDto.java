package org.cttelsamicsterrassa.data.api.rest.importjob;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

/**
 * REST representation of a supported import source.
 *
 * @param id    machine-readable identifier (enum name, e.g. {@code RFETM})
 * @param label human-friendly display name
 */
public record ImportSourceDto(String id, String label) {

    public static ImportSourceDto from(ImportSource source) {
        return new ImportSourceDto(source.name(), labelFor(source));
    }

    private static String labelFor(ImportSource source) {
        return switch (source) {
            case RFETM -> "RFETM";
            case BCNESA -> "BCNESA";
            case FCTT -> "FCTT";
        };
    }
}
