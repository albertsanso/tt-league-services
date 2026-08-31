# Build Plan

1. Establish regression coverage for the existing settings behavior before
   changing contracts. Expand `SystemSettingsServiceTest` to cover the ten-key
   catalog and declaration order, defaults at version `0`, category and
   case-insensitive key/label filtering, all supported value types, validation,
   optimistic conflicts, preview without persistence, atomic bulk replacement,
   complete backups, and restore version increments.
2. Replace the `SystemSetting` record in `tt-data-league-core-domain` with a
   class extending `org.albertsanso.commons.model.Entity`. Preserve its current
   key, category, type, value, default value, version, label, description,
   allowed values, and numeric bounds through immutable metadata and explicit
   getters. Use factories for new and rehydrated settings so persistence reads
   do not emit creation events.
3. Add settings domain events under the domain settings package. Creation and
   value-changing actions must publish events containing the setting key and
   relevant state; rehydration and unchanged-value operations must not publish
   events. Keep version advancement in the entity action used by updates so
   value and version cannot diverge.
4. Extract the allowlisted setting definitions and typed validation/encoding
   rules currently embedded in `SystemSettingsService` into framework-light
   domain collaborators. The catalog must remain the single source for
   metadata and defaults and must support both default construction and
   rehydration of persisted key/type/value/version data.
5. Refactor `SettingsRepository` so `findAll`, `save`, and `replaceAll` accept
   and return only domain `SystemSetting` entities. Remove the domain
   `PersistedSetting` record after all callers and in-memory test repositories
   have moved to the domain-only contract.
6. Refactor `SystemSettingsService` to orchestrate catalog lookup, validation,
   domain actions, conflicts, preview, bulk update, backup, and restore using
   only domain entities. Remove persistence encoding and persistence-object
   construction from the service while preserving its public methods,
   synchronization, exception types, catalog behavior, and version semantics.
7. In `tt-data-league-core-repository-jpa`, replace `SettingJPA` with the
   persistence entity `PersistedSetting`. Map it explicitly to table
   `system_settings`, map its primary-key field to column `key`, retain
   `setting_type`, `setting_value`, and `version`, and add the required
   `PersistedSetting(SystemSetting)` constructor.
8. Add separate `SystemSettingToPersistedSettingMapper` and
   `PersistedSettingToSystemSettingMapper` components, following the existing
   Club and Player directional `Function` mapper pattern. Use the domain
   catalog when the persistence-to-domain direction needs to restore metadata
   and typed values; rehydration must not publish domain events.
9. Update `SettingRepositoryHelper` and `SettingsRepositoryJpa` to use the new
   persistence entity and both mappers for reads, inserts, conditional updates,
   and transactional replacement. Preserve the conditional
   `updateIfVersion` write and the flush/clear boundary used by replacement;
   translate stale writes to the existing conflict behavior rather than
   weakening optimistic concurrency.
10. Update REST mapping code from record accessors to `SystemSetting` getters
    without changing routes, authorization, DTO field names, JSON shapes,
    backup schema version, restore size limit, or HTTP error mappings. No
    frontend behavior or API-client change is required.
11. Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` to make
    `system_settings` and primary-key column `key` the schema contract. Define
    and execute a data-preserving migration from the existing `SystemSetting`
    table and `setting_key` column before enabling the renamed mapping in an
    environment containing persisted administrator settings.
12. Add focused tests for `SystemSetting` factories/actions/events, both mapper
    directions, JPA metadata and typed round trips, inserts, conditional
    updates, stale writes, and replacement. Retain REST contract coverage, then
    run the domain, JPA, and REST module tests followed by the full Maven
    reactor.

# Implementation Guidelines

- Keep the domain independent of Spring, Jakarta Persistence, and database
  encoding. Persistence conversion belongs only in the JPA adapter.
- Use the established string setting key as `SystemSetting` identity; do not
  add a UUID that would conflict with the REST and database contracts.
- Preserve the FEAT-00022 allowlist, categories, types, defaults, validation,
  search behavior, catalog ordering, and exclusion of deployment secrets.
- Preserve version `0` for defaults, version `1` for the first persisted
  update, explicit stale-write conflicts, validation-before-write for bulk
  operations, and transactional complete-catalog restore.
- Preserve preview as side-effect-free and non-persistent. Do not emit domain
  events while listing, previewing, or rehydrating existing settings.
- Follow existing entity conventions: private construction where practical,
  explicit factories and getters, null/invariant validation, and no public
  setters that bypass actions.
- Follow existing persistence conventions: Jakarta annotations, explicit table
  and column mappings, directional mapper components, and focused H2 repository
  tests.
- Keep the REST contract and frontend unchanged. This feature is an
  architecture refactor, not a settings-catalog or user-interface expansion.
- Do not rely on Hibernate `ddl-auto: update` to rename the existing table or
  primary-key column; it cannot guarantee preservation of existing rows.

# Notes

- FEAT-00022 is complete and supplies the behavior that this refactor must
  preserve.
- The feature terminology is intentional: the current domain
  `PersistedSetting` record moves out of the domain and becomes the JPA entity;
  the current JPA class `SettingJPA` is replaced. The existing repository type
  names are `SettingsRepository` and `SettingsRepositoryJpa`.
- The current physical mapping and datamodel use table `SystemSetting` and
  primary-key column `setting_key`; FEAT-00025 deliberately changes them to
  `system_settings` and `key`.
- The catalog should remain domain-owned because it defines supported settings,
  defaults, metadata, and validation. The JPA mapper may consume that domain
  collaborator but must not duplicate catalog definitions.
- Event scope is limited to creation and effective value changes. Bulk update
  and restore use those per-setting actions rather than introducing separate
  aggregate events unless implementation reveals a concrete consumer need.
- Existing settings rows are migrated at startup by
  `SystemSettingsSchemaMigration`, before the renamed mapping is used. The
  migration handles both a legacy-only schema and the case where Hibernate has
  already created the new table. The runtime currently uses Hibernate
  `ddl-auto: update`, while repository tests use `create-drop`.
- Validation commands:
  `mvn -pl tt-data-league-core-domain -am test`,
  `mvn -pl tt-data-league-core-repository-jpa -am test`,
  `mvn -pl tt-data-league-api-rest -am test`, and `mvn test`.
