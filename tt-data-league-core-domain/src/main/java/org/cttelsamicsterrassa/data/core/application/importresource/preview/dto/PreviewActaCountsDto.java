package org.cttelsamicsterrassa.data.core.application.importresource.preview.dto;

/**
 * The acta buckets of an import preview (FEAT-00088): {@code published} (PLAYED),
 * {@code unpublished} (PENDING with resolvable teams), {@code partial}, {@code invalid} and
 * {@code unresolved} (pending fixtures without team names). Informational only.
 */
public record PreviewActaCountsDto(
        long published,
        long unpublished,
        long partial,
        long invalid,
        long unresolved) {
}
