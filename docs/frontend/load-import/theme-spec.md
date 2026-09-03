# Data Import / Load Web Page UI Design Specification

This document provides a comprehensive design specification, component breakdown, and ASCII art wireframe representation of the Data Import/Load web interface and its individual components based on the provided design mockups.

---

## 1. Overall Interface Layout (ASCII Art)

```text
+-------------------------------------------------------------------------------------------------------------+
|                                              <@load-button>                                                 |
|  +-----------------------------------+   +--------------------+                                             |
|  | File chooser <@file-browser>      |   |        Load        |                                             |
|  +-----------------------------------+   +--------------------+                                             |
|                                                                                                             |
|  +------------------+-------------------------------------------------+  +-------------------------------+  |
|  |  RFETM       [★] |                                                 |  |                               |  |
|  | <@source-selector>|  +-------------------------------------------+  |  |                               |  |
|  +------------------+  | 2022-2024      [ Load ] (O) [ Simulate ] (O)|  |  |                               |  |
|  |  BCNESA      [★] |  | 2026-09-01 14:37:03                          |  |  |                               |  |
|  | <@source-selector>|  +-------------------------------------------+  |  | <@season-import-report-and-   |  |
|  +------------------+                                                 |  |   status>                     |  |
|  |  FCTT        [★] |             <@seasons-import-list>              |  |                               |  |
|  | <@source-selector>|                                                 |  |                               |  |
|  +------------------+                                                 |  |                               |  |
|  |                  |                                                 |  |                               |  |
|  |                  |                                                 |  |                               |  |
|  +------------------+-------------------------------------------------+  +-------------------------------+  |
+-------------------------------------------------------------------------------------------------------------+
```

---

## 2. Component Design Specifications

### A. Season Import List Item (`<@season-import-list-item>`)

This component represents an individual record within the `<@seasons-import-list>` container.

```text
+-----------------------------------------------------------------------+
|  2022-2024                 +----------+ (O)   +--------------+ (O)    |
|  2026-09-01 14:37:03       |   Load   |       |   Simulate   |        |
|                            +----------+       +--------------+        |
+-----------------------------------------------------------------------+
```

* **Element Type**: List Item Card / Row Component
* **Identifier**: `<@season-import-list-item>`
* **Sub-Components & Structure**:
    1. **Season Identifier / Title**: Displays the season range or name (e.g., `2022-2024`) in bold text at the top left.
    2. **Timestamp Label**: Displays the import or update timestamp (e.g., `2026-09-01 14:37:03`) in smaller text below the title.
    3. **Load Trigger Block**:
        * **Button**: Light green rounded button labeled **Load**. Triggers active loading for this specific season.
        * **Status Indicator**: Circular indicator/radio element adjacent to the button to signal state (e.g., ready, loading, active).
    4. **Simulate Trigger Block**:
        * **Button**: Light pink rounded button labeled **Simulate**. Triggers a test/dry-run import execution without committing changes.
        * **Status Indicator**: Circular indicator/radio element adjacent to the button for simulation status feedback.

---

### B. Structural & Layout Architecture

The user interface follows a three-column structure with top control parameters:

1. **Top Header Control Section**: Positioned at the top of the interface for immediate file upload and execution triggers.
2. **Left Panel (Source Selector Sidebar)**: Stacked vertically to allow single/multi-selection of data providers/sources (`RFETM`, `BCNESA`, `FCTT`).
3. **Center Panel (Main Content/Data Area)**: Workspace housing the `<@seasons-import-list>` container, populated by individual `<@season-import-list-item>` components.
4. **Right Panel (Report & Status Workspace)**: Dedicated sidebar providing real-time feedback, execution logs, and detailed operation summaries.

---

### C. Top Control Bar

* **File Chooser (`<@file-browser>`)**:
    * **Type**: Input / File Selection Component
    * **Label**: `File chooser <@file-browser>`
    * **Purpose**: Allows users to select a local data file to load into the system.
* **Load Button (`<@load-button>`)**:
    * **Type**: Action Button
    * **Label**: `Load`
    * **Purpose**: Triggers the global load sequence for the chosen file.
    * **Enablement**: Becomes enabled immediately after a local file is chosen. No season, source, or other selection is required before loading.