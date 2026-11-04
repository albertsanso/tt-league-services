package org.cttelsamicsterrassa.data.core.domain.settings.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerFilesystemTest {

    @Test
    void recognisesWindowsOperatingSystemNames() {
        assertTrue(ServerFilesystem.isWindows("Windows 11"));
        assertTrue(ServerFilesystem.isWindows("Windows Server 2022"));
        assertTrue(ServerFilesystem.isWindows("WINDOWS 10"));
    }

    @Test
    void treatsEveryOtherOperatingSystemAsPosix() {
        assertFalse(ServerFilesystem.isWindows("Linux"));
        assertFalse(ServerFilesystem.isWindows("Mac OS X"));
        assertFalse(ServerFilesystem.isWindows("FreeBSD"));
    }

    @Test
    void readsTheRunningOperatingSystem() {
        assertEquals(System.getProperty("os.name"), ServerFilesystem.OS_NAME);
        assertEquals(ServerFilesystem.isWindows(System.getProperty("os.name")), ServerFilesystem.WINDOWS);
    }
}
