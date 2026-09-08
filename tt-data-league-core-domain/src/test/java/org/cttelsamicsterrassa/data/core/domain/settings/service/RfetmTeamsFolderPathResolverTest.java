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

    @Test
    void resolvesTheTeamsFolderRelativeToTheRepositoryFolder() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "repository-folder"))
                .thenReturn(Optional.of(Setting.createExisting(
                        UUID.randomUUID(), SettingCategory.IMPORT, "repository-folder", "c:\\tt-repository")));
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "rfetm-teams-folder"))
                .thenReturn(Optional.of(Setting.createExisting(
                        UUID.randomUUID(), SettingCategory.IMPORT, "rfetm-teams-folder", "import-rfetm\\teams")));
        RfetmTeamsFolderPathResolver resolver =
                new RfetmTeamsFolderPathResolver(new SettingFinderService(settingRepository));

        Path resolved = resolver.resolve();

        assertEquals(Path.of("c:\\tt-repository").resolve("import-rfetm\\teams"), resolved);
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
                        UUID.randomUUID(), SettingCategory.IMPORT, "repository-folder", "c:\\tt-repository")));
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "rfetm-teams-folder"))
                .thenReturn(Optional.empty());
        RfetmTeamsFolderPathResolver resolver =
                new RfetmTeamsFolderPathResolver(new SettingFinderService(settingRepository));

        assertThrows(IllegalStateException.class, resolver::resolve);
    }
}
