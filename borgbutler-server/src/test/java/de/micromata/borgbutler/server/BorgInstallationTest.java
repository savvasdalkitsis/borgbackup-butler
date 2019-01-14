package de.micromata.borgbutler.server;

import de.micromata.borgbutler.config.ConfigurationHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class BorgInstallationTest {
    private Logger log = LoggerFactory.getLogger(BorgInstallationTest.class);

    @Test
    void foo() throws Exception {
        ConfigurationHandler.getConfiguration().setBorgCommand("hurzel");
        BorgInstallation borgInstallation = BorgInstallation.getInstance();
        borgInstallation.initialize();
    }

    @Test
    void downloadTest() {
        checkDownload(RunningMode.OSType.LINUX, "borg-linux64");
        checkDownload(RunningMode.OSType.MAC_OS, "borg-macosx64");
        checkDownload(RunningMode.OSType.FREEBSD, "borg-freebsd64");
        assertNull(BorgInstallation.getInstance().download(RunningMode.OSType.WINDOWS));
    }

    private void checkDownload(RunningMode.OSType osType, String expectedName) {
        File file = BorgInstallation.getInstance().download(osType);
        assertEquals(expectedName, file.getName());
        assertTrue(file.canExecute());
    }
}
