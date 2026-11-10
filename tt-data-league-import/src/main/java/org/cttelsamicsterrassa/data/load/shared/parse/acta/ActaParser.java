package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Region;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads an RFETM match report file into an {@link Acta}.
 *
 * <h2>Character encoding</h2>
 * <p>Report files are UTF-8; every one of the 20,619 files in the reference export decodes cleanly.
 * The byte stream is therefore handed to Jackson, which decodes JSON input as UTF-8 and reports a
 * malformed byte sequence as a parse failure instead of substituting replacement characters. Text
 * is stored exactly as written: some reports contain case or accent damage introduced upstream
 * (for example {@code JIMéNEZ}), and normalising it here would make the imported name disagree with
 * the federation record.</p>
 */
@Component
public class ActaParser {

    private final ObjectMapper objectMapper;

    public ActaParser() {
        this(defaultObjectMapper());
    }

    public ActaParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses the given match report file.
     *
     * @throws ActaParseException if the file cannot be read, is not valid UTF-8 JSON, or does not
     *                            match the acta schema
     */
    public Acta parse(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            Acta acta = objectMapper.readValue(in, Acta.class);
            requireKnownRegion(acta);
            return acta;
        } catch (IOException | RuntimeException e) {
            throw new ActaParseException(file, e);
        }
    }

    /** FEAT-00121: the optional region fields are closed vocabularies; an unknown value is a parse error. */
    private static void requireKnownRegion(Acta acta) {
        if (acta.region() != null && !acta.region().isBlank() && Region.fromValue(acta.region()).isEmpty()) {
            throw new IllegalArgumentException("unknown region '" + acta.region() + "'");
        }
        if (acta.regionType() != null && !acta.regionType().isBlank()
                && RegionType.fromValue(acta.regionType()).isEmpty()) {
            throw new IllegalArgumentException("unknown tipo_region '" + acta.regionType() + "'");
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
    }
}
