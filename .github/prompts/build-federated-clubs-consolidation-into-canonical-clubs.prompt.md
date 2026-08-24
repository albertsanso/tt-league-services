# Summary

Add a process at the end of the federated clubs consolidation process to merge the federated clubs into canonical clubs. 

This will ensure that all federated clubs are properly integrated into the main club structure, maintaining consistency and organization across the platform.

# Description

The process should identify similar FederatedClub entities based on their names, apply a matching algorithm to determine which 
canonical `Club` entity they refer to, and create or reuse one consolidated canonical `Club` instance per accepted group of `FederatedClub` entities.

`FederatedClub` entity properties:
- `name`: The canonical name of the club, derived from the consolidated Team names.
- `source`: The federation identifier, which should match the source of the consolidated Team entities.

Canonical `Club` entity properties:
- `name`: The canonical name of the club, derived from the consolidated Team names.

All references from `FederatedClub` entities to their corresponding `Club` entity should be updated to point 
to the new or existing canonical `Club` instance.

As `source` property can hold different values: ["RFETM", "BCNESA", "FCTT"] the cardinality of the `FederatedClub`
to `Club` relationship should be many-to-one, meaning multiple `FederatedClub` entities can point to a single `Club` entity,
but each `FederatedClub` can only point to one `Club` entity. And many-to-one can vary from 3-to-1, 2-to-1, or 1-to-1 
depending on the number of `FederatedClub` entities that match a single `Club` entity.

# Git prohibition

Do not use Git under any circumstances.
Never invoke git, GitHub APIs, IDE Git features, or commands that read or modify Git metadata (including status, log,
diff, show, branch, checkout, reset, commit, and revert).
Do not delegate Git-related work to agents.
Work exclusively from the current filesystem contents and run only non-Git validation commands.

# Execution workflow

This process should be executed after the federated clubs consolidation process has completed. The steps are as follows:
1. **Identify Similar Federated Clubs**: Use a matching algorithm to group `FederatedClub` entities based on their names and other relevant properties. This will help in identifying which federated clubs refer to the same canonical club.
2. **Determine Canonical Club**: For each group of similar `FederatedClub` entities, determine the corresponding canonical `Club` entity. If a matching `Club` entity already exists, reuse it; otherwise, create a new `Club` entity with the consolidated name.
3. **Update References**: Update all references from the `FederatedClub` entities to point to the new or existing canonical `Club` instance. Ensure that the `source` property is preserved and correctly associated with the `Club` entity.
4. **Maintain Consistency**: Ensure that the many-to-one relationship between `FederatedClub` and `Club` is maintained, allowing multiple `FederatedClub` entities to point to a single `Club` entity while ensuring that each `FederatedClub` can only point to one `Club` entity. Validate that the relationships are correctly established and that there are no orphaned `FederatedClub` entities without a corresponding `Club` entity.
5. **Final Validation**: Perform a final validation to ensure that all `FederatedClub` entities have been successfully merged into their corresponding canonical `Club` entities. Check for any discrepancies or errors in the relationships and resolve them as necessary.
6. **Documentation and Reporting**: Document the changes made during the merging process, including the list of `FederatedClub` entities that were merged, the corresponding `Club` entities they were associated with, and any new `Club` entities created. Generate a report summarizing the consolidation process for future reference and auditing purposes.

# Matching Algorithm - Similarity Metrics

The matching algorithm should be designed to identify similar `FederatedClub` entities based on their names and other relevant properties. 
It must enforce a composite similarity score that uses all of the following metrics:

- normalized-name Levenshtein similarity, to capture spelling and abbreviation differences;
- character n-gram Jaccard similarity, to compare the overlap of name fragments;
- character n-gram cosine similarity, to compare the weighted frequency of those fragments.

The score must be calculated deterministically from the normalized names and compared
with an explicit acceptance threshold. Short names and names without a shared
meaningful term must be rejected even when their raw string score is high. Matching
must remain scoped to the same federation source.

Accepted pairwise matches must be combined into transitive clusters, so a name
matching another accepted name is not left out merely because it is not the
best pairwise match. Each cluster must receive a deterministic label extracted
from its most common meaningful terms, excluding generic terms such as `CLUB`,
`CTT`, `TT`, `TENNIS`, `TAULA`, and connector words. Labels should preserve
meaningful term order without adding a synthetic prefix. For example:

```text
["CLUB TENNIS TAULA ELS AMICS TERRASSA",
 "CTT ELS AMICS DE TERRASSA",
 "CTT ELS AMICS TERRASSA"]
-> "AMICS TERRASSA"
```

The cluster label must first be compared with every existing canonical `Club`
using the same composite metrics and acceptance threshold. If an accepted
canonical `Club` match exists, assign all cluster members to the highest-scoring
deterministic match. Otherwise, create a canonical `Club` named after the
cluster label and assign every member to it.
Exact groups with no extractable common terms may retain their deterministic
preferred display name. The same clustering and labelling analysis must be
performed in report mode, without persistence writes, and the consolidation
report must include the cluster rule and confidence.
