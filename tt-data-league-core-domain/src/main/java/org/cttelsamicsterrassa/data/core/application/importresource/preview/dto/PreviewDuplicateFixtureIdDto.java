package org.cttelsamicsterrassa.data.core.application.importresource.preview.dto;

import java.util.List;

/**
 * One source fixture id ({@code id_partido}) seen on more than one file of the previewed snapshot
 * (FEAT-00088), with the file locations carrying it. Reported as a warning; the fixture counts once
 * in the projected progress.
 */
public record PreviewDuplicateFixtureIdDto(
        String sourceFixtureId,
        List<String> locations) {
}
