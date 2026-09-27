---
name: analyse-incremental-actas-for-current-jornada-import
description: This command analyzes the incremental actas for the current jornada import.
---

# Summary
The `analyse-incremental-actas-for-current-jornada-import` command is designed to analyze the current import process of actas (records) for a whole season and makes an analysis
of the needed modifications in order to handle incremental imports of actas for the current jornada (matchday). It helps identify any discrepancies, missing data, or inconsistencies in the imported records.

# Description

Now Actas import  is importing ZIP files with the whole season on all `jornadas` for historical actas. 
From now on, Actas import will be incremental for each current `jornada` so it is needed to keep track of the last/current jornada imported. 
The actas reports can be present but empty because no matches have been produced yet. 
That means the zip file can contain actas with results and played matches up to the last played match day, and also can contain empty actas records planned but not already played. 

Consider last modifications in `docs/acta-model-definition.json` in order to support published/non-published actas and empty actas for future matches.
- New property `acta_publicada` (boolean) to indicate if the acta is published or not.

# Goal 
Make a feasibility analysis and make a plan in steps with prioritized implementation tasks. Don't implement anything, just show the analysis and plan.
- Output report file `/docs/analysis/analysis-incremental-actas-for-current-jornada-import.md` with the analysis and plan.

# Acceptance Criteria
- The analysis should identify the current state of the actas import process and the requirements for handling incremental imports for the current jornada.
- The plan should outline the necessary modifications to the import process, including any changes to data structures, validation rules, and error handling.
- The plan should prioritize the implementation tasks based on their importance and dependencies, providing a clear roadmap for the development team to follow.
- The analysis and plan should be documented clearly, making it easy for stakeholders to understand the proposed changes and their impact.
- The analysis should include a risk assessment of the proposed changes, identifying potential challenges and mitigation strategies.

# Matches reports (Actas) examples

## Published acta with results and played matches
For RFETM can be found in the following path: `C:\git\rfetm-extract-2\resources\actas-json`
For BCNESA can be found in the following path: `C:\git\bcnesa-extract-2\resources\actas-json`
For FCTT can be found in the following path: `C:\git\fctt-extract\resources\actas-json`
- "acta_publicada" can be true
- "partidos" cannot be null or empty, must contain at least one match with results.
- "alineaciones" cannot be null or empty, must contain at least one alignment with players.



