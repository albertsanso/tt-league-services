package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RfetmTeamsFolderPathResolverTest {

    private static final String REPOSITORY_FOLDER =
            Path.of("").toAbsolutePath().resolve("tt-repository").toString();

    @Test
    void resolvesTheTeamsFolderRelativeToTheRepositoryFolder() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "repository-folder"))
                .thenReturn(Optional.of(Setting.createExisting(
                        UUID.randomUUID(), SettingCategory.IMPORT, "repository-folder", REPOSITORY_FOLDER)));
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "rfetm-teams-folder"))
                .thenReturn(Optional.of(Setting.createExisting(
                        UUID.randomUUID(), SettingCategory.IMPORT, "rfetm-teams-folder", "import-rfetm/teams")));
        RfetmTeamsFolderPathResolver resolver =
                new RfetmTeamsFolderPathResolver(new SettingFinderService(settingRepository));

        Path resolved = resolver.resolve();

        assertEquals(Path.of(REPOSITORY_FOLDER).resolve("import-rfetm").resolve("teams"), resolved);
    }

    @Test
    void resolvesALegacyBackslashTeamsFolderToNestedFolders() {
        RfetmTeamsFolderPathResolver resolver = resolverFor(REPOSITORY_FOLDER, "import-rfetm\\teams");

        Path resolved = resolver.resolve();

        assertEquals(Path.of(REPOSITORY_FOLDER).resolve("import-rfetm").resolve("teams"), resolved);
    }

    @Test
    void failsWhenTheStoredRepositoryFolderIsRelative() {
        RfetmTeamsFolderPathResolver resolver = resolverFor("tt-repository", "import-rfetm/teams");

        assertThrows(IllegalArgumentException.class, resolver::resolve);
    }

    @Test
    void failsWhenTheStoredRepositoryFolderIsWrittenForTheOtherOperatingSystem() {
        String foreign = Path.of("/").isAbsolute() ? "c:\\tt-repository" : "/data/tt-repository";
        RfetmTeamsFolderPathResolver resolver = resolverFor(foreign, "import-rfetm/teams");

        assertThrows(IllegalArgumentException.class, resolver::resolve);
    }

    @Test
    void failsWhenTheStoredTeamsFolderIsBlank() {
        RfetmTeamsFolderPathResolver resolver = resolverFor(REPOSITORY_FOLDER, " ");

        assertThrows(IllegalArgumentException.class, resolver::resolve);
    }

    private static RfetmTeamsFolderPathResolver resolverFor(String repositoryFolder, String teamsFolder) {
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "repository-folder"))
                .thenReturn(Optional.of(Setting.createExisting(
                        UUID.randomUUID(), SettingCategory.IMPORT, "repository-folder", repositoryFolder)));
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "rfetm-teams-folder"))
                .thenReturn(Optional.of(Setting.createExisting(
                        UUID.randomUUID(), SettingCategory.IMPORT, "rfetm-teams-folder", teamsFolder)));
        return new RfetmTeamsFolderPathResolver(new SettingFinderService(settingRepository));
    }

    @Test
    void failsWhenTheRepositoryFolderSettingIsMissing() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "repository-folder"))
                .thenReturn(Optional.empty());
        RfetmTeamsFolderPathResolver resolver =
                new RfetmTeamsFolderPathResolver(new SettingFinderService(settingRepository));

        assertThrows(IllegalStateException.class, resolver::resolve);
    }

    @Test
    void failsWhenTheRfetmTeamsFolderSettingIsMissing() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "repository-folder"))
                .thenReturn(Optional.of(Setting.createExisting(
                        UUID.randomUUID(), SettingCategory.IMPORT, "repository-folder", REPOSITORY_FOLDER)));
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "rfetm-teams-folder"))
                .thenReturn(Optional.empty());
        RfetmTeamsFolderPathResolver resolver =
                new RfetmTeamsFolderPathResolver(new SettingFinderService(settingRepository));

        assertThrows(IllegalStateException.class, resolver::resolve);
    }
}
