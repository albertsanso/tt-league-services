package org.cttelsamicsterrassa.data.core.domain.load.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublishedActaCounterTest {

    private final PublishedActaCounter counter = new PublishedActaCounter(new ObjectMapper());

    @Test
    void countsFilesWithoutTheFieldAndWithTrueAsPublished(@TempDir Path workDir) throws Exception {
        Path missingField = Files.writeString(workDir.resolve("a.json"), "{\"jornada\": 1}");
        Path publishedTrue = Files.writeString(workDir.resolve("b.json"), "{\"acta_publicada\": true}");
        Path publishedFalse = Files.writeString(workDir.resolve("c.json"), "{\"acta_publicada\": false}");

        assertEquals(2, counter.countPublished(List.of(missingField, publishedTrue, publishedFalse)));
    }

    @Test
    void doesNotCountNonObjectRootsAndInvalidJson(@TempDir Path workDir) throws Exception {
        Path arrayRoot = Files.writeString(workDir.resolve("array.json"), "[{\"acta_publicada\": true}]");
        Path stringRoot = Files.writeString(workDir.resolve("string.json"), "\"published\"");
        Path invalidJson = Files.writeString(workDir.resolve("broken.json"), "{ not json");

        assertEquals(0, counter.countPublished(List.of(arrayRoot, stringRoot, invalidJson)));
    }

    @Test
    void ignoresFilesThatAreNotJson(@TempDir Path workDir) throws Exception {
        Path acta = Files.writeString(workDir.resolve("a.json"), "{}");
        Path readme = Files.writeString(workDir.resolve("README.md"), "{\"acta_publicada\": true}");
        Path upperCaseJson = Files.writeString(workDir.resolve("B.JSON"), "{}");

        assertEquals(2, counter.countPublished(List.of(acta, readme, upperCaseJson)));
    }

    @Test
    void walksNestedFoldersAndIgnoresDirectories(@TempDir Path workDir) throws Exception {
        Path nested = Files.createDirectories(workDir.resolve("jornada-1/sub"));
        Files.writeString(nested.resolve("a.json"), "{}");
        Files.writeString(nested.resolve("b.json"), "{\"acta_publicada\": false}");
        Files.writeString(workDir.resolve("c.json"), "{\"acta_publicada\": true}");
        Files.createDirectory(workDir.resolve("empty-folder"));

        assertEquals(2, counter.countPublishedIn(workDir));
    }

    @Test
    void missingFolderGivesZero(@TempDir Path workDir) {
        assertEquals(0, counter.countPublishedIn(workDir.resolve("does-not-exist")));
    }
}
