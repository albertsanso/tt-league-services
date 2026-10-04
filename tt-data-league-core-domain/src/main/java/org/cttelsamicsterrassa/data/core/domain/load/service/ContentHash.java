package org.cttelsamicsterrassa.data.core.domain.load.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Computes the manifest {@code contentSha256} of an upload ZIP.
 *
 * <p>Every entry except directories and the root {@code manifest.json} is taken with its name exactly as stored
 * in the ZIP. Names are sorted by their UTF-8 bytes and, for each one, {@code <name>\n<sha256 of the entry
 * bytes>\n} is appended to one buffer. The result is the lowercase hex SHA-256 of that buffer, so it depends on
 * neither ZIP timestamps, compression nor entry order. {@code tt-league-ingest} implements the same algorithm.
 */
public final class ContentHash {

    private static final String MANIFEST_NAME = "manifest.json";
    private static final HexFormat HEX = HexFormat.of();

    private ContentHash() {
    }

    public static String compute(byte[] zipContent) {
        Map<String, String> entryDigests = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && !MANIFEST_NAME.equals(name)) {
                    if (entryDigests.put(name, HEX.formatHex(digest(zipInputStream))) != null) {
                        throw new IllegalArgumentException("ZIP file contains a duplicate entry: " + name);
                    }
                }
                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid ZIP file", exception);
        }
        List<String> names = entryDigests.keySet().stream()
                .sorted((left, right) -> Arrays.compareUnsigned(
                        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8)))
                .toList();
        MessageDigest buffer = sha256();
        for (String name : names) {
            buffer.update((name + "\n" + entryDigests.get(name) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return HEX.formatHex(buffer.digest());
    }

    private static byte[] digest(InputStream inputStream) throws IOException {
        MessageDigest digest = sha256();
        byte[] chunk = new byte[8192];
        int read = inputStream.read(chunk);
        while (read != -1) {
            digest.update(chunk, 0, read);
            read = inputStream.read(chunk);
        }
        return digest.digest();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
