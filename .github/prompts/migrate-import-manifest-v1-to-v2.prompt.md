---
name: migrate-import-manifest-v1-to-v2
description: Migrate import manifest v1 to v2
---

# Summary
This prompt is designed to assist in migrating an import manifest from version 1 to version 2

# Description

The new manifest format (v2) introduces a more structured and organized way to define the import manifest. 
The new manifest format fusions the `asset_type` and `files` properties into a single `assets` object, allowing for better categorization and organization of different asset types.
It includes a `source` field to specify the origin of the data, a `seasons` array to list the relevant seasons, 
and an `assets` object that categorizes different types of assets (e.g., ACTAS, TEAMS) along with their associated files.

v1 manifest example for ACTAS asset type:
```json
{
  "source": "RFETM",
  "asset_type": "ACTAS",
  "seasons": [
    "2024-2025", "2025-2026"
  ],
  "files": [
    "2025-2026/divisio-honor/acta_123.json",
    "2024-2025/super-divisio/acta_456.json"
  ]
}
```

v1 manifest example for TEAMS asset type:
```json
{
  "source": "RFETM",
  "asset_type": "TEAMS",
  "seasons": [
    "2025-2026"
  ],
  "files": [
    "2025-2026.json",
    "2025-2026.json"
  ]
}
```

v2 manifest example that combines both asset types:
```json
{
  "source": "RFETM",
  "seasons": [
    "2025-2026"
  ],
  "assets": {
    "ACTAS": {
      "files": ["actas-json/...", "..."]
    },
    "TEAMS": {
      "files": ["equipos-json/...", "..."]
    }
  }
}
```

1) **asset_type**: this property present in v1 is now represented as a key in the `assets` object in v2.
   Each asset type (e.g., ACTAS, TEAMS) will have its own entry in the `assets` object, and the associated files will be listed under the `files` array.
2) **files**: the `files` property in v1 is now nested under each asset type in the `assets` object in v2. Each asset type will have its own `files` array containing the relevant file paths.
3) **source**: this property remains unchanged and is still present in v2 to specify the origin of the data.
4) **seasons**: this property remains unchanged and is still present in v2 to list the relevant seasons.
5) **assets**: this new object in v2 serves as a container for different asset types, allowing for better organization and categorization of the files associated with each asset type.
6) **Overall Structure**: The overall structure of the manifest has been improved in v2 to provide a clearer and more organized representation of the import manifest, making it easier to manage and understand the different asset types and their associated files.

# Goal
The goal of this prompt is to assist in migrating an import manifest from version 1 to version 2 by providing a clear and structured approach to transforming the data. 
The migration process involves consolidating the `asset_type` and `files` properties into a single `assets` object, while retaining the `source` and `seasons` properties. 

1) Review ImportManifest.java v1 and migrate it to ImportManifest.java v2, ensuring that the new structure is followed and that all relevant data is preserved.
2) Review the migration to ImportManifest.java v2 takes into account Resources and ImportResources creation services, upload services, resource zip services, etc. to ensure that the new structure is compatible with the existing services and workflows.
3) Ensure that the migration process is thoroughly tested to verify that the new manifest structure is correctly implemented and that all functionalities are working as expected.