package org.cttelsamicsterrassa.data.core.domain.load.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Counts published acta files the same way the import model treats them: a file counts when it is a
 * regular {@code .json} file whose JSON root is an object and whose {@code acta_publicada} field is
 * absent or is not the boolean {@code false}.
 *
 * <p>A file that is unreadable or not valid JSON counts as not published, so a broken incoming file
 * makes rejection more likely and never raises the stored baseline.</p>
 */
final class PublishedActaCounter {

    private final ObjectMapper objectMapper;

    PublishedActaCounter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    int countPublished(Collection<Path> files) {
        int count = 0;
        for (Path file : files) {
            if (isPublishedActa(file)) {
                count++;
            }
        }
        return count;
    }

    int countPublishedIn(Path folder) {
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        try (Stream<Path> entries = Files.walk(folder)) {
            return countPublished(entries.filter(Files::isRegularFile).toList());
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to walk folder for published actas: " + folder, exception);
        }
    }

    private boolean isPublishedActa(Path file) {
        if (!Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            return false;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(file.toFile());
        } catch (IOException exception) {
            return false;
        }
        if (root == null || !root.isObject()) {
            return false;
        }
        JsonNode published = root.get("acta_publicada");
        return published == null || !published.isBoolean() || published.booleanValue();
    }
}
