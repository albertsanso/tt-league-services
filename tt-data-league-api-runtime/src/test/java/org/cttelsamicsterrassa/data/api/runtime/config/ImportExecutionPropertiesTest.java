package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.AmendedActaMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FEAT-00089: the amended-acta detection property is disabled by default and maps to a nullable
 * {@link AmendedActaMode}; an unsupported value fails at startup rather than silently disabling.
 */
class ImportExecutionPropertiesTest {

    @Test
    void amendedActaDetectionIsDisabledByDefault() {
        assertNull(new ImportExecutionProperties().toOptions().amendedActaMode());
    }

    @Test
    void disabledNoneAndBlankMapToNull() {
        assertEquals(null, optionsWith("disabled").amendedActaMode());
        assertEquals(null, optionsWith("none").amendedActaMode());
        assertEquals(null, optionsWith("  ").amendedActaMode());
        assertEquals(null, optionsWith(null).amendedActaMode());
    }

    @Test
    void writeAndReportAreMappedCaseInsensitively() {
        assertEquals(AmendedActaMode.WRITE, optionsWith("write").amendedActaMode());
        assertEquals(AmendedActaMode.WRITE, optionsWith("WRITE").amendedActaMode());
        assertEquals(AmendedActaMode.REPORT, optionsWith("report").amendedActaMode());
    }

    @Test
    void anUnsupportedValueFailsAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> optionsWith("preview"));
    }

    private static org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions optionsWith(String value) {
        ImportExecutionProperties properties = new ImportExecutionProperties();
        properties.setAmendedActaDetection(value);
        return properties.toOptions();
    }
}