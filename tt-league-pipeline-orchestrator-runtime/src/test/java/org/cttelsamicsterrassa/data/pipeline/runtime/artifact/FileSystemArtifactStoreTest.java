package org.cttelsamicsterrassa.data.pipeline.runtime.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStoreException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.StoredArtifact;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemArtifactStoreTest {

    @TempDir
    Path root;

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void storesWithHashAndSizeUnderNestedKeys() throws Exception {
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);

        StoredArtifact stored = store.store("rfetm/2025-2026/run/ingest-1.zip", stream("hello zip"));

        assertThat(stored.sha256()).isEqualTo(sha256("hello zip"));
        assertThat(stored.sizeBytes()).isEqualTo(9);
        assertThat(Files.readString(root.resolve("rfetm/2025-2026/run/ingest-1.zip"))).isEqualTo("hello zip");
        assertThat(store.exists("rfetm/2025-2026/run/ingest-1.zip")).isTrue();
        assertThat(store.content("rfetm/2025-2026/run/ingest-1.zip").size()).isEqualTo(9);
        assertThat(stripStreams(store)).isEqualTo("hello zip");
    }

    private static String stripStreams(FileSystemArtifactStore store) throws IOException {
        try (InputStream in = store.content("rfetm/2025-2026/run/ingest-1.zip").open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void contentCanBeOpenedAgainForEachAttempt() throws Exception {
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);
        store.store("a/b.zip", stream("abc"));

        for (int i = 0; i < 2; i++) {
            try (InputStream in = store.content("a/b.zip").open()) {
                assertThat(in.readAllBytes()).hasSize(3);
            }
        }
    }

    @Test
    void aFailingStreamLeavesNoVisibleFile() throws Exception {
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);
        InputStream failing = new InputStream() {
            private int count;

            @Override
            public int read() throws IOException {
                if (count++ < 5) {
                    return 'x';
                }
                throw new IOException("connection reset");
            }
        };

        assertThatThrownBy(() -> store.store("a/b.zip", failing)).isInstanceOf(ArtifactStoreException.class);

        assertThat(store.exists("a/b.zip")).isFalse();
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void anExistingKeyIsRejectedAndKeepsTheOriginal() throws Exception {
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);
        store.store("a/b.zip", stream("first"));

        assertThatThrownBy(() -> store.store("a/b.zip", stream("second")))
                .isInstanceOf(ArtifactStoreException.class)
                .hasMessageContaining("already exists");

        assertThat(Files.readString(root.resolve("a/b.zip"))).isEqualTo("first");
    }

    @Test
    void keysThatEscapeTheRootAreRejected() {
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);
        Path outside = root.getParent().toAbsolutePath().resolve("outside.zip");

        assertThatThrownBy(() -> store.store("../outside.zip", stream("x"))).isInstanceOf(ArtifactStoreException.class);
        assertThatThrownBy(() -> store.store("a/../../outside.zip", stream("x")))
                .isInstanceOf(ArtifactStoreException.class);
        assertThatThrownBy(() -> store.store(outside.toString(), stream("x")))
                .isInstanceOf(ArtifactStoreException.class);
        assertThatThrownBy(() -> store.store(".tmp/x.zip", stream("x"))).isInstanceOf(ArtifactStoreException.class);
        assertThatThrownBy(() -> store.exists("../outside.zip")).isInstanceOf(ArtifactStoreException.class);
        assertThat(Files.exists(outside)).isFalse();
    }

    @Test
    void deleteIsIdempotent() {
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);
        store.store("a/b.zip", stream("x"));

        store.delete("a/b.zip");
        store.delete("a/b.zip");

        assertThat(store.exists("a/b.zip")).isFalse();
    }

    @Test
    void unknownContentIsAnError() {
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);

        assertThatThrownBy(() -> store.content("missing.zip")).isInstanceOf(ArtifactStoreException.class);
    }

    @Test
    void aMissingOrNonDirectoryRootFailsAtConstruction() throws IOException {
        Path file = Files.createFile(root.resolve("a-file"));

        assertThatThrownBy(() -> new FileSystemArtifactStore(root.resolve("missing")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> new FileSystemArtifactStore(file))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("a-file");
    }

    @Test
    void leftoverTemporaryFilesAreDeletedAtStartup() throws IOException {
        Path temp = Files.createDirectories(root.resolve(".tmp"));
        Path leftover = Files.writeString(temp.resolve("old.part"), "partial");

        new FileSystemArtifactStore(root);

        assertThat(leftover).doesNotExist();
    }
}
