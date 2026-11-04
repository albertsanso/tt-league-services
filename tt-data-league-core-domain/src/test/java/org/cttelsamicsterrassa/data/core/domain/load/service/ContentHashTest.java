package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentHashTest {

    /**
     * Shared with {@code test_packaging_pipeline.py} in {@code tt-league-ingest}: both implementations must give
     * this value for {@link #sharedFixture()}. The two {@code x?.json} names sort differently by UTF-16 units
     * than by UTF-8 bytes, so this also pins the sort order.
     */
    static final String SHARED_FIXTURE_HASH = "9784c2c5092b315727efac63ad618c3e4b0bae8c9cfe8bbb914ff654557e77c8";

    static Map<String, String> sharedFixture() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("equipos-json/2026-2027.json", "[]\n");
        entries.put("actas-json/2026-2027/x🏓.json", "{\"d\": 4}\n");
        entries.put("actas-json/2026-2027/x�.json", "{\"c\": 3}\n");
        entries.put("actas-json/2026-2027/jornada-1/b.json", "{\"b\": 2}\n");
        entries.put("actas-json/2026-2027/año.json", "{\"a\": 1}\n");
        return entries;
    }

    @Test
    void theSharedFixtureGivesTheValueTheIngestPackagerComputes() throws IOException {
        assertEquals(SHARED_FIXTURE_HASH, ContentHash.compute(zip(sharedFixture(), Deflater.DEFAULT_COMPRESSION)));
    }

    @Test
    void theHashDoesNotDependOnEntryOrderCompressionOrTimestamps() throws IOException {
        Map<String, String> reversed = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(sharedFixture().keySet());
        for (int index = names.size() - 1; index >= 0; index--) {
            reversed.put(names.get(index), sharedFixture().get(names.get(index)));
        }

        String first = ContentHash.compute(zip(sharedFixture(), Deflater.NO_COMPRESSION));
        String second = ContentHash.compute(zip(reversed, Deflater.BEST_COMPRESSION));

        assertEquals(first, second);
    }

    @Test
    void theRootManifestAndDirectoryEntriesAreIgnored() throws IOException {
        Map<String, String> withManifest = new LinkedHashMap<>(sharedFixture());
        withManifest.put("manifest.json", "{\"source\": \"RFETM\"}");
        withManifest.put("actas-json/", "");

        assertEquals(SHARED_FIXTURE_HASH, ContentHash.compute(zip(withManifest, Deflater.DEFAULT_COMPRESSION)));
    }

    @Test
    void aNestedManifestIsContent() throws IOException {
        Map<String, String> withNestedManifest = new LinkedHashMap<>(sharedFixture());
        withNestedManifest.put("actas-json/manifest.json", "{}");

        assertNotEquals(SHARED_FIXTURE_HASH,
                ContentHash.compute(zip(withNestedManifest, Deflater.DEFAULT_COMPRESSION)));
    }

    @Test
    void changedContentOrNamesChangeTheHash() throws IOException {
        Map<String, String> changedContent = new LinkedHashMap<>(sharedFixture());
        changedContent.put("equipos-json/2026-2027.json", "[ ]\n");
        Map<String, String> renamed = new LinkedHashMap<>(sharedFixture());
        renamed.put("equipos-json/2026-2027-renamed.json", renamed.remove("equipos-json/2026-2027.json"));

        assertNotEquals(SHARED_FIXTURE_HASH, ContentHash.compute(zip(changedContent, Deflater.DEFAULT_COMPRESSION)));
        assertNotEquals(SHARED_FIXTURE_HASH, ContentHash.compute(zip(renamed, Deflater.DEFAULT_COMPRESSION)));
    }

    @Test
    void aDuplicateEntryIsRejected() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("first-a.json", "1");
        entries.put("first-b.json", "2");
        byte[] content = zip(entries, Deflater.NO_COMPRESSION);
        // ZipOutputStream refuses duplicate names, so rename the second entry in place (same length).
        replaceAll(content, "first-b.json", "first-a.json");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ContentHash.compute(content));
        assertEquals("ZIP file contains a duplicate entry: first-a.json", exception.getMessage());
    }

    @Test
    void aTruncatedZipIsRejected() throws IOException {
        Map<String, String> entries = Map.of("large.json", "x".repeat(10_000));
        byte[] content = zip(entries, Deflater.NO_COMPRESSION);
        byte[] truncated = Arrays.copyOf(content, content.length / 2);

        assertThrows(IllegalArgumentException.class, () -> ContentHash.compute(truncated));
    }

    static byte[] zip(Map<String, String> entries, int level) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zipOutputStream.setLevel(level);
            long time = 1_700_000_000_000L + level * 86_400_000L;
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setLastModifiedTime(FileTime.fromMillis(time));
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void replaceAll(byte[] content, String target, String replacement) {
        byte[] from = target.getBytes(StandardCharsets.US_ASCII);
        byte[] to = replacement.getBytes(StandardCharsets.US_ASCII);
        for (int start = 0; start <= content.length - from.length; start++) {
            boolean matches = true;
            for (int offset = 0; offset < from.length && matches; offset++) {
                matches = content[start + offset] == from[offset];
            }
            if (matches) {
                System.arraycopy(to, 0, content, start, to.length);
            }
        }
    }
}
