# Summary

 Consolidate duplicate PlayerSeason entities into a single entity per unique player, ensuring data integrity and simplifying management.

# Description

PlayerSeason entities are related to a unique Player entity in the system.
However, there are instances where multiple PlayerSeason entities exist for the same player, 
leading to redundancy and potential confusion. 
This prompt aims to consolidate these duplicate PlayerSeason entities into a single entity per unique player, ensuring data 
integrity and simplifying management.

In module `tt-data-league-import`, the `PlayerSeason` entity is designed to represent a player's participation in a specific season.
However, due to various data import processes or manual entries, there can be multiple `PlayerSeason` entities for the same player, which can lead to inconsistencies and difficulties in managing player data.

# Goals

Create a new processor that identifies and consolidates duplicate `PlayerSeason` entities for the same player. The processor should:
1. Identify all `PlayerSeason` entities associated with the same player.
2. Create a unique Player instance that consolidates the data from the duplicate `PlayerSeason`
3. The matching criteria is based on the PlayerSeason's name. Apply an algorithm to determine if two PlayerSeason names refer to the same player (e.g., ignoring case, whitespace, and common abbreviations).
4. Update all references to the old `PlayerSeason` entities to point to the new consolidated `Player` instance.
5. Ensure that the consolidation process maintains data integrity and does not result in any loss of important player information.

# Technical Requirements

1. The processor should be implemented in a way that it can be easily integrated into the existing `tt-data-league-import` module, following the module's coding standards and practices.
2. The processor should be designed to be easily extendable for future enhancements, such as adding new matching criteria or handling additional data fields in the `PlayerSeason` entity.
3. The processor should log its actions, including the number of duplicates found, the names of the consolidated players, and any issues encountered during the process.
4. The processor should be idempotent, meaning that running it multiple times should not create additional duplicates or alter the data incorrectly after the first run.
5. The processor should be tested with a variety of scenarios, including players with different naming conventions, to ensure robustness and accuracy in identifying duplicates.
6. The processor should provide a summary report of the consolidation process, including the number of duplicates consolidated, the names of the new consolidated players, and any errors or warnings encountered.

# Architecture

Take TeamConsolidationProcessor as a reference for the architecture of the PlayerSeasonConsolidationProcessor.
The processor should be designed to efficiently handle large datasets and ensure that the consolidation process is performed in a timely manner.

# Matching algorithm

The matching algorithm should consider the following factors to determine if two `PlayerSeason` names refer to the same player:
1. Case Insensitivity: The comparison should ignore case differences (e.g., "John Doe" and "john doe" should be considered the same).
2. Whitespace Normalization: Leading and trailing whitespace should be ignored, and multiple consecutive spaces should be treated as a single space (e.g., "John Doe" and "John   Doe" should be considered the same).
3. Common Abbreviations: Recognize and normalize common abbreviations (e.g., "J. Doe" for "John Doe", "Jr." for "Junior", etc.) to ensure that variations in naming conventions do not lead to false negatives in identifying duplicates.
4. Special Characters: Ignore special characters and punctuation that do not affect the core identity of the player name (e.g., "John Doe" and "John-Doe" should be considered the same).
5. Name-terms combinations: Consider the order of name terms and their combinations (e.g., "Doe, John" and "John Doe" should be considered the same).
6. Fuzzy Matching: Implement a fuzzy matching algorithm to account for minor typographical errors or variations in spelling (e.g., "John Doe" and "Jon Doe" should be considered the same).

# Acceptance Criteria
1. The processor should successfully identify duplicate `PlayerSeason` entities based on the defined matching criteria.
2. A new consolidated `Player` instance should be created for each unique player identified.
3. All references to the old `PlayerSeason` entities should be updated to point to the new consolidated `Player` instance.
4. The processor should handle edge cases, such as players with similar names but different identities, ensuring that only true duplicates are consolidated.
5. The processor should log its actions, including the number of duplicates found, the names of the consolidated players, and any issues encountered during the process.
6. The processor should be idempotent, meaning that running it multiple times should not create additional duplicates or alter the data incorrectly after the first run.
7. The processor should be tested with a variety of scenarios, including players with different naming conventions, to ensure robustness and accuracy in identifying duplicates.
8. The processor should provide a summary report of the consolidation process, including the number of duplicates consolidated, the names of the new consolidated players, and any errors or warnings encountered.
9. The processor should be designed to be easily extendable for future enhancements, such as adding new matching criteria or handling additional data fields in the `PlayerSeason` entity.
10. The processor should be implemented in a way that it can be easily integrated into the existing `tt-data-league-import` module, following the module's coding standards and practices.
11. The processor must consume source-scoped `PlayerSeason.name` values and choose one deterministic canonical `Player.name`. Preserve every PlayerSeason row and UUID, update associations only through `PlayerSeason.withPlayer`, and never retarget MATCH or LINEUP references.

# Implementation plan

When requested, provide a detailed implementation plan for the PlayerSeasonConsolidationProcessor, including the following steps:
1. Data Collection: Gather all `PlayerSeason` entities from the database, including their associated `Player` entities and relevant metadata.
2. Preprocessing: Normalize the names of the `PlayerSeason` entities based on the defined matching criteria (case insensitivity, whitespace normalization, common abbreviations, special characters, name-terms combinations, and fuzzy matching).
3. Duplicate Identification: Use the normalized names to identify potential duplicates among the `PlayerSeason` entities. Group the duplicates together for further processing.
4. Consolidation: For each group of duplicates, create a new consolidated `Player` instance that combines the relevant data from the duplicate `PlayerSeason` entities. Ensure that all important player information is preserved and that the new `Player` instance accurately represents the unique player.
5. Reference Update: Update all references to the old `PlayerSeason` entities to point to the new consolidated `Player` instance. This includes updating any foreign key relationships and ensuring that all associated data is correctly linked to the new `Player` instance.
6. Logging and Reporting: Log the actions taken during the consolidation process, including the number of duplicates found, the names of the consolidated players, and any issues encountered. Generate a summary report of the consolidation process, including the number of duplicates consolidated, the names of the new consolidated players, and any errors or warnings encountered.
7. Testing: Conduct thorough testing of the processor with a variety of scenarios, including players with different naming conventions, to ensure robustness and accuracy in identifying duplicates. Validate that the consolidation process maintains data integrity and does not result in any loss of important player information.
8. Integration: Integrate the PlayerSeasonConsolidationProcessor into the existing `tt-data-league-import` module, following the module's coding standards and practices. Ensure that the processor can be easily invoked as part of the data import process and that it operates correctly within the overall system architecture.
9. Documentation: Provide comprehensive documentation for the PlayerSeasonConsolidationProcessor, including usage instructions, configuration options, and details on the matching algorithm and consolidation process. Ensure that the documentation is clear and accessible for future developers and maintainers of the system.

Plan output file: `docs/implementation-plans/player-season-consolidation-processor.md`
