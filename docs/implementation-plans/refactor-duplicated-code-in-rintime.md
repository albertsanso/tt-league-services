# Refactor duplicated code in import runtime

## Goal

Reduce duplicated CLI, source-dispatch, and consolidation-wiring logic in
`tt-data-league-import-runtime` without changing the command-line contract,
source-specific traversal behavior, consolidation ordering, transaction
boundaries, or public runtime entry points.

The requested filename uses `rintime` to match the supplied path. The module
itself remains `tt-data-league-import-runtime`.

## Scope

### In scope

- `App` source and actas-folder argument constants.
- `ImportRuntimeArguments` argument-token constants and repeated mode parsing.
- Repeated FCTT/BCNESA/RFETM traversal selection in `App`.
- Repeated club/player consolidation runner wiring where an abstraction is
  demonstrably simpler and does not hide the different result types.
- Focused runtime tests and the runtime README/usage documentation.

### Out of scope

- Source parsers, navigators, import processors, or consolidation algorithms.
- Moving source-specific behavior into a generic runtime abstraction.
- Changing the supported sources, default source, argument names, or flag
  semantics.
- Merging the domain `ImportSource` enum with the JPA `Source` enum.
- Parent POM or dependency changes.

## Findings

### 1. CLI argument contract is duplicated between `App` and the parser

`App` defines `SOURCE_ARGUMENT` and `ACTAS_FOLDER_ARGUMENT` and also repeats
the source values in its switch and usage/error messages
(`tt-data-league-import-runtime/src/main/java/org/cttelsamicsterrassa/data/load/runtime/App.java:23-25,47-51,80-107`).

`ImportRuntimeArguments` independently defines `SOURCE_ARGUMENT` and
`ACTAS_FOLDER_ARGUMENT`, plus the season and consolidation flag tokens
(`.../ImportRuntimeArguments.java:20-26`).

This permits the parser, runtime validation, usage text, and tests to drift.
The current contract is `--actas-folder`; no compatibility alias for the old
`--base-folder` spelling should be introduced as part of this refactor.

### 2. Club and player consolidation mode parsing is structurally duplicated

`ImportRuntimeArguments.parse` has two branches with the same algorithm:

1. detect a bare flag;
2. detect the `=<mode>` form;
3. enable consolidation;
4. normalize the value;
5. accept report/write/true/empty values;
6. throw an option-specific error for anything else
   (`.../ImportRuntimeArguments.java:40-63`).

Only the option names and destination fields differ. This is the clearest
candidate for a small private parser helper or immutable option value that
keeps club and player modes independent.

### 3. Source traversal dispatch repeats the same control flow

`App` has three switch branches that each select a navigator, call either
`traverse` or `traverseSeason`, log a source-specific message, and assign an
`ImportSource` (`App.java:89-110`).

The navigators intentionally remain source-specific and their summary types
are not identical, so a large generic navigator hierarchy would add coupling
rather than remove it. The duplication should be reduced only at the runtime
dispatch boundary, for example through a small source descriptor/adapter or a
focused `traverseSelectedSource` helper.

### 4. Consolidation runners are symmetrical adapters

`ClubConsolidationRunner` and `PlayerConsolidationRunner` each contain the
same constructor, transactional `run` method, and direct processor delegation.
Only processor and summary types differ
(`ClubConsolidationRunner.java:10-23`,
`PlayerConsolidationRunner.java:10-22`).

This is intentional runtime wiring and each class is a useful explicit Spring
boundary. A generic runner would likely require wildcard result types or
function objects and make the API less clear. Treat this as a low-priority
duplication: keep the two public beans unless an implementation can preserve
typed results, transaction behavior, and straightforward dependency
injection.

### 5. CLI documentation repeats the same contract

The argument names, supported sources, modes, and ordering are represented in
the `App` Javadoc, runtime usage/error text, `ImportRuntimeArgumentsTest`, and
`tt-data-league-import-runtime/README.md:74-188`.

Documentation cannot be fully generated from Java in this refactor, but the
implementation should centralize the executable contract and update the
README and usage text together. Tests should assert the externally visible
contract rather than private constant locations.

## Proposed design

### A. Introduce one package-private CLI contract

Add a small runtime-only class, for example
`ImportRuntimeCliContract`, in the existing runtime package. It should own:

- `--source=`;
- `--actas-folder=`;
- `--season=`;
- both consolidation flag prefixes;
- default source `rfetm`;
- supported source names `rfetm`, `bcnesa`, and `fctt`;
- accepted mode values `report`, `write`, `true`, and empty;
- shared usage text or usage fragments where doing so avoids duplicated
  literals.

Keep the class package-private unless another module needs the contract.
`ImportRuntimeArguments` should use it for parsing. `App` should use it for
required-argument messages, supported-source validation, and source labels.
Do not move source traversal or consolidation business logic into this class.

The contract should either expose immutable constants or intent-revealing
methods. Avoid making `App` depend on parser implementation details merely to
reuse a string.

### B. Extract one consolidation-option parser

Represent a parsed consolidation option as a small immutable value containing
`enabled` and `ConsolidationMode`, or use a private helper returning those two
values.

The helper must:

- distinguish bare flags from `=<value>` arguments;
- preserve independent club/player state;
- normalize with `Locale.ROOT`;
- preserve current acceptance of empty, `true`, and `write`;
- preserve `report`;
- include the actual option name in invalid-value errors;
- avoid silently accepting unknown values.

Use the helper for both consolidation options and retain the existing
four-argument constructor compatibility behavior.

### C. Consolidate source traversal dispatch conservatively

Create a focused runtime abstraction only if it keeps the existing navigator
types isolated. A suitable shape is a private source descriptor containing:

- the CLI source key;
- the domain `ImportSource`;
- a source display label;
- a traversal function accepting `Path` and optional season.

Because BCNESA has a distinct traversal summary type, the descriptor may use a
small runtime-local functional interface whose result is only logged. Do not
introduce a shared summary type in the import module just to remove three
switch branches.

The resulting flow should remain:

1. validate the actas folder;
2. resolve the selected source;
3. traverse all seasons or the requested season;
4. log the source traversal summary;
5. return the corresponding `ImportSource`;
6. run club consolidation, then player consolidation.

Unknown source behavior must remain an explicit `IllegalArgumentException`
with the supported values in the message.

### D. Decide runners after the first refactor

Do not merge `ClubConsolidationRunner` and `PlayerConsolidationRunner` in the
initial change. Their duplication is small, their typed return values are
useful, and separate Spring beans make sequencing and transaction boundaries
obvious.

If a later implementation demonstrates a typed generic adapter without
wildcards, casts, or loss of bean clarity, it may be considered separately.
Otherwise document the symmetry as intentional rather than replacing clear
code with an abstraction.

## Implementation sequence

1. Add the package-private CLI contract and migrate constants/literals from
   `App` and `ImportRuntimeArguments`.
2. Extract shared consolidation-mode parsing while preserving all current
   validation and constructor behavior.
3. Refactor `App` source traversal dispatch with a small runtime-local helper
   or descriptor; keep each navigator and source-specific summary intact.
4. Keep the two consolidation runners separate unless the typed abstraction
   is simpler than the existing adapters.
5. Update `App` usage documentation and
   `tt-data-league-import-runtime/README.md` from the same supported contract.
6. Extend focused tests, then review the diff for accidental argument,
   source, transaction, or sequencing changes.

## Tests and acceptance criteria

Extend `ImportRuntimeArgumentsTest` and add focused `App` tests if the
existing test infrastructure supports constructor-injected fake navigators and
runners.

Cover:

- default source remains `rfetm`;
- source parsing remains case-insensitive;
- `--actas-folder` remains required;
- `--base-folder` is not silently accepted;
- all three source keys dispatch to their own navigator;
- both all-season and `--season` traversal paths are preserved;
- unknown sources fail before consolidation;
- bare, write, true, empty, and report consolidation forms behave unchanged;
- club and player flags remain independent;
- invalid mode values identify the correct option;
- successful traversal is required before consolidation;
- club consolidation still runs before player consolidation;
- both runner beans preserve their typed summaries and transactional methods.

Acceptance requires:

- no duplicated CLI token or supported-source literal between `App` and
  `ImportRuntimeArguments`;
- one shared implementation for club/player mode parsing;
- no change to the documented CLI contract;
- no new dependency crossing between runtime, import, domain, or JPA modules;
- `mvn -pl tt-data-league-import-runtime -am test` passes;
- `mvn test` passes from the repository root.

## Files expected to change

- `tt-data-league-import-runtime/src/main/java/org/cttelsamicsterrassa/data/load/runtime/App.java`
- `tt-data-league-import-runtime/src/main/java/org/cttelsamicsterrassa/data/load/runtime/ImportRuntimeArguments.java`
- new package-private CLI contract/helper under the same runtime package
- `tt-data-league-import-runtime/src/test/java/org/cttelsamicsterrassa/data/load/runtime/ImportRuntimeArgumentsTest.java`
- focused runtime `App` test if required
- `tt-data-league-import-runtime/README.md`

No domain, persistence, import algorithm, schema, or parent POM changes are
expected.
