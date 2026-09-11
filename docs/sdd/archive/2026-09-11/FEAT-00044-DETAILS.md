# Build Plan
1. **Data shape stays flat; hierarchy is a client-side view concern.** Keep
   `getClubMatchesByCompetitions` / `useClubMatches` (`tt-data-league-frontend/src/api/clubs.js`,
   `src/hooks/useClubs.js`) returning a flat array of `{ competition, season, source, matches }`
   groups, one per input competition ref — no API/hook contract change. Correct the `source`
   used for hierarchy grouping to come from the input competition ref
   (`competition.source ?? club.source`, the same value `ClubDetailPage.jsx` already uses for
   its source filter) rather than the per-response `source` field returned by
   `getClubCompetitionDetails`, since the response source is not guaranteed to match the
   requested competition's source when "all sources" is selected. Pass this resolved source
   alongside each competition ref into `getClubMatchesByCompetitions` and use it (not
   `result.source`) when building each returned group.

2. **Add a hierarchy-building helper** (e.g. `groupMatchesHierarchy(matchGroups)` in
   `ClubDetailPage.jsx` or a small new `src/utils/clubMatches.js` if it grows past a few
   lines) that buckets the flat `matchGroups` array into
   `[{ source, seasons: [{ season, competitions: [{ competition, season, source, matches }] }] }]`,
   sorted: sources alphabetically, seasons **descending** (most recent season first — this is
   the opposite of the existing `uniqueSorted` ascending order used for the season *filter*
   dropdown; that filter is unaffected, only the hierarchy's season ordering is descending),
   competitions alphabetically by name (matches current `availableCompetitions` sort).

3. **Rework `MatchesPanel` into a 3-level collapsible tree.**
   - Level 1 (Source): one row per distinct source in the hierarchy, showing the source name
     and an aggregate match count; a toggle button following the existing `SidebarItem.jsx`
     disclosure pattern (`aria-expanded`, `ChevronRight`/`ChevronDown` from `lucide-react`,
     `onClick` toggling that node's key in expansion state).
   - Level 2 (Season): rendered only while its parent Source is expanded; same disclosure
     pattern, one row per season within that source, aggregate match count.
   - Level 3 (Competition): rendered only while its parent Season is expanded; reuses the
     current per-competition group markup (heading with competition name, "view competition"
     link via `routePaths.clubCompetitionDetails(...)`, `club-match-list` / `club-match-card`
     match list sorted by `round` ascending) — but now also collapsible/collapsed by default,
     consistent with the other two levels, using the same toggle pattern.
   - Expansion state: a single `Set<string>` of expanded node keys (e.g.
     `source`, `${source}::${season}`, `${source}::${season}::${competition}`) held in
     `MatchesPanel` local state via `useState(() => new Set())` — empty by default, satisfying
     "all groups collapsed by default". Toggling adds/removes the node's key.
   - Keep the loading state (reuse `club-state card`), error state with retry (reuse the
     existing top-level error branch pattern), and the top-level empty state (
     `t('detail.clubMatchesEmpty')`, shown when `filteredCompetitions` itself is empty) as
     already implemented; the per-competition empty state (`t('detail.competitionEmpty')`)
     still applies inside an expanded, matchless Competition node.

4. **i18n**: add new keys to `ca.js`/`en.js`/`es.js` under `detail.*` — e.g.
   `detail.expandGroup` / `detail.collapseGroup` (or a single templated
   `detail.toggleGroup` used for `aria-label`s on all three levels — decide based on whichever
   reads more naturally per locale), `detail.matchesCount` (aggregate count shown on
   Source/Season rows, reuse `detail.matchesAvailable`'s `{{count}}` pattern if the wording
   fits instead of adding a new key). Keep the already-shipped
   `detail.clubMatchesEmpty` / `detail.clubMatchesLoadError` / `detail.viewCompetition` keys
   as-is.

5. **CSS**: extend `app.css` with disclosure-row styles for the Source/Season levels (a
   button-like row with the chevron, label, and count — can largely mirror
   `.sidebar-item`'s disclosure styling rather than inventing a new visual language) and
   indentation per level. Keep the existing `.club-match-group` / `.club-match-group-heading` /
   `.club-match-list` / `.club-match-card` rules for the Competition level and its matches
   unchanged.

6. **Tests** (`pages/ClubDetailPage.test.jsx`, plus a focused test file for the hierarchy
   helper if it lands in its own module):
   - All groups render collapsed by default (Source rows visible; Season/Competition content
     and match cards not in the document until expanded).
   - Expanding a Source reveals its Seasons; expanding a Season reveals its Competitions;
     expanding a Competition reveals its match list — collapsing re-hides each in turn.
   - Existing source/season/competition filter behavior still narrows which
     Source/Season/Competition nodes appear (adapt the current filter tests, which assumed a
     flat competition-group list, to locate nodes via the new disclosure structure).
   - Empty state still renders when `filteredCompetitions` is empty.
   - `getClubMatchesByCompetitions` / `useClubMatches` tests updated only if the `source`
     resolution change from step 1 affects their existing assertions.

7. **Add a 4th hierarchy level, Team, below Competition (Source > Season > Competition > Team).**
   For each match in a Competition group, resolve which side (`homeTeam` or `awayTeam`) is the
   viewed club's own team: match `homeTeam`/`awayTeam` (`api/clubs.js` `normalizeMatch`) against
   `club.teams` (`{ id, name, source, season }`, already loaded via `getClubDetails` /
   `normalizeClubDetailsResponse`) filtered to the Competition group's `source`/`season`, by
   exact name; the resolved team name is the Team-level bucket key. A match whose neither side
   matches a known club team (data inconsistency) falls back to a single `t('detail.unknownTeam')`
   bucket rather than being dropped, so no match silently disappears from the hierarchy.
   - Extend `groupMatchesHierarchy` (`utils/clubMatches.js`) to take the club's `teams` list and
     produce `[{ source, seasons: [{ season, competitions: [{ competition, teams: [{ team,
     matches }] }] }] }]`, sorted: sources alphabetically, seasons descending, competitions
     alphabetically (all unchanged), teams alphabetically by name.
   - Extend `MatchesPanel`'s tree with a 4th disclosure level (Team) between Competition and the
     match list, reusing the same `HierarchyToggle` component and expansion-state `Set<string>`
     pattern (node key `${source}::${season}::${competition}::${team}`) — collapsed by default
     like the other three levels; the existing per-competition "view competition" link and
     empty state move to sit above the Team list, one level up from the match cards.
   - Add `detail.unknownTeam` i18n key (`ca.js`/`en.js`/`es.js`) for the fallback bucket; reuse
     `detail.matchesAvailable` for the Team row's aggregate count, consistent with the other
     levels.
   - Tests: `clubMatches.test.js` covers Team bucketing (home-side team, away-side team, and the
     unknown-team fallback) and alphabetical team ordering; `ClubDetailPage.test.jsx` covers
     expanding a Competition to reveal its Teams and expanding a Team to reveal its matches,
     plus that existing filters still narrow correctly through the added level.

8. **Omit a hierarchy level when its filter fixes a single value.** `ClubDetailContent`
   (`ClubDetailPage.jsx`) already computes `sourceFilter`, `season`, and `competition` as
   non-empty strings only when a specific value is selected (empty string means "all", per the
   existing filter logic around lines 90-134). Pass these three values down into `MatchesPanel`
   alongside `club`/`competitions`, and thread them into `groupMatchesHierarchy` (or a sibling
   helper) as an `omitLevels` set (`source` when `sourceFilter` is set, `season` when `season` is
   set, `competition` when `competition` is set).
   - `groupMatchesHierarchy` skips building a grouping array for each omitted level and instead
     passes the (already-filtered, necessarily single-valued) matches straight through to the
     next level down — e.g. with only `season` omitted, the shape collapses to
     `[{ source, competitions: [{ competition, teams: [...] }] }]` with no `seasons` array in
     between; with `season` and `competition` both omitted, it collapses further to
     `[{ source, teams: [...] }]` (no `seasons` and no `competitions` array); omitting `source`
     as well changes nothing about this data shape, since Source is never omitted from the
     *data* shape (it is always the outermost grouping) — only skip rendering it as a disclosure
     row when `sourceFilter` is set, since with one source selected there is always exactly one
     Source group.
   - `MatchesPanel` renders each level conditionally: an omitted level's node key segment and
     `HierarchyToggle` row are not rendered — its children render directly, one nesting level
     shallower, without needing that level's own expand/collapse state (a level with a fixed,
     single value adds no useful disclosure). Non-omitted levels keep their existing disclosure
     behavior unchanged, including collapsed-by-default and independent expansion per node.
   - This changes only which grouping *rows* are shown, never which matches are shown — the
     existing filter narrowing (`filteredCompetitions`, per-level match content) is unaffected.
   - Tests: `clubMatches.test.js` covers the hierarchy shape with each level individually omitted
     and with combinations omitted (e.g. season+competition, all three); `ClubDetailPage.test.jsx`
     covers that selecting a specific Source/Season/Competition in the filter removes that
     level's toggle row from the tree while its matches remain reachable one level up, and that
     switching a filter back to "all" restores that level's grouping.

# Implementation Guidelines

- Do not change the `/api/v1/club/{id}/competition/{season}/{competition}` backend endpoint
  or its DTOs (`ClubCompetitionDetailsDto`, `FederatedClubCompetitionDetailsReadModel`) —
  this feature is scoped to the frontend Matches tab only.
- Keep the existing source/season/competition filter UI and URL-param behavior in
  `ClubDetailPage.jsx` untouched; only the Matches-tab body (`MatchesPanel`) changes.
- Reuse the existing disclosure pattern from `components/sidebar/SidebarItem.jsx`
  (`aria-expanded`, `ChevronRight`/`ChevronDown` from `lucide-react`) for the Source/Season/
  Competition toggles rather than introducing a different expand/collapse idiom (no native
  `<details>`, no third-party accordion dependency).
- Reuse `club-match-list` / `club-match-card` CSS classes already defined in `src/app.css`
  (used today by `ClubCompetitionDetailPage.jsx`) — do not introduce new match-card styles.
- Reuse the existing `normalizeMatch` / `getClubCompetitionDetails` request+normalization
  logic in `api/clubs.js` rather than duplicating match-shape parsing.
- Out of scope: persisting expand/collapse state across navigations or reloads, encoding it
  in the URL, pagination/virtualization of large match lists, a bulk backend endpoint,
  grouping by round instead of by competition, a Team-level filter (the existing
  source/season/competition filters are unaffected; Team is a display-only grouping level),
  and changes to
  `ClubCompetitionDetailPage.jsx` itself (it keeps working as the per-competition drill-down
  view, now optionally reachable from a Competition-level "view competition" link as well as
  from the old competition-card flow).

# Notes

- Hierarchy implementation complete (2026-09-09). `getClubMatchesByCompetitions`
  (`api/clubs.js`) now uses each input competition ref's own resolved `source`
  (`competition.source ?? club.source`) instead of the API response's `source` field, per
  Build Plan step 1. Added `groupMatchesHierarchy` in the new `utils/clubMatches.js` (with
  its own `clubMatches.test.js`), bucketing flat match groups into
  Source (alphabetical) > Season (descending) > Competition (alphabetical). `MatchesPanel` in
  `ClubDetailPage.jsx` reworked into a 3-level collapsible tree using a `Set<string>` of
  expanded node keys (`useState(() => new Set())`, empty by default) and a shared
  `HierarchyToggle` button component (`aria-expanded`, `ChevronRight`/`ChevronDown` from
  `lucide-react`, following the existing `SidebarItem.jsx` disclosure pattern). Reused
  `detail.matchesAvailable` for the aggregate match count shown on each toggle row instead of
  adding new i18n keys. Added CSS: `.club-match-hierarchy` / `.club-match-hierarchy-list` /
  `.club-match-hierarchy-item` / `.club-match-hierarchy-toggle` / `.club-match-hierarchy-count`
  / `.club-match-group-body`; removed the now-unused `.club-match-group` /
  `.club-match-group-heading` rules from the flat version. `ClubDetailPage.test.jsx` rewritten
  to drive the tree via the toggle buttons (collapsed-by-default, cascading expand/collapse,
  descending season order, filter narrowing, empty state). Full frontend test suite
  (181 tests), `eslint .`, and `vite build` all pass. Not verified in a running browser
  against a live backend in this session.
- Known pre-existing dead CSS, out of scope: `.club-competition-heading` /
  `.club-competition-summary` / `.club-competition-card` / `.club-competition-list` in
  `app.css` have had no JSX references since the flat `MatchesPanel` replaced the old
  `CompetitionsPanel` in an earlier iteration of this feature; left as-is here since removing
  them isn't part of this build plan.
- Requirement added 2026-09-09 (after the prior flat, competition-only grouping shipped and
  reached `in-review`): all groups must be collapsible, collapsed by default, with hierarchy
  Source > Season > Competition (3 levels). This supersedes the flat per-competition grouping
  from the original build — the previously shipped `MatchesPanel` / `getClubMatchesByCompetitions`
  / `useClubMatches` code (see "Implementation complete" note below) is the starting point for
  Build Plan steps 1-3, not a rewrite from scratch.
- Resolved 2026-09-09: season sort order within a Source is **descending** (most recent
  season first), per explicit user instruction. This only affects the hierarchy's season
  grouping order (Build Plan step 2) — the existing season *filter* dropdown is unaffected and
  stays ascending.
- Implementation complete (2026-09-09) for the flat, non-hierarchical version.
  `getClubMatchesByCompetitions` added to
  `api/clubs.js`, `useClubMatches` added to `hooks/useClubs.js`, `CompetitionsPanel` replaced
  by `MatchesPanel` in `ClubDetailPage.jsx` (grouped by competition, per-group "view
  competition" link, per-group and club-level empty states, matches sorted by round). New
  i18n keys (`detail.clubMatchesEmpty`, `detail.clubMatchesLoadError`, `detail.viewCompetition`)
  added to `ca.js`/`en.js`/`es.js`. Tests added: `api/clubs.test.js` (parallel fetch, grouping,
  round sort, rejection propagation), `hooks/useClubs.test.jsx` (parallel fetch, no re-fetch on
  unchanged competition set, re-fetch on change, error propagation), and
  `pages/ClubDetailPage.test.jsx` updated for the new grouped-match markup. Full frontend test
  suite (177 tests), `eslint .`, and `vite build` all pass. Not verified in a running browser
  against a live backend in this session — the dev server starts and builds cleanly, but no
  backend/auth was wired up to click through the Matches tab live.
- Performance: when a club has many competitions across "all seasons" (no season/competition
  filter applied), `filteredCompetitions` can be large, and this plan issues one HTTP
  request per competition in parallel. Acceptable for the club sizes in this system today;
  if this proves too slow in practice, the follow-up is a dedicated backend query
  (`FindClubMatchesQuery` or similar) returning all matches for a club/filter set in one
  round trip, analogous to `FindFederatedClubCompetitionDetailsQuery` but club-scoped
  instead of competition-scoped. Not undertaken now to keep this change frontend-only.
- `CompetitionsPanel` is confirmed dead-code-to-be once `MatchesPanel` lands (only reference
  is its own definition/usage in `ClubDetailPage.jsx`).
- Reopened 2026-09-09 for re-verification per user request.
- Scope extended 2026-09-09: add a 4th hierarchy level, Team, below Competition (Source > Season > Competition > Team), lowest priority in the tree. Moved back to in-progress to implement this addition. Build Plan step 7 covers resolving the club's own team per match (matched against `club.teams` by name/source/season, with an `unknownTeam` fallback bucket), the extended `groupMatchesHierarchy` shape, the 4th `MatchesPanel` disclosure level, and the corresponding tests.
- Scope extended 2026-09-09: when the Source, Season, or Competition filter is set to a specific value rather than "all", that level's grouping must disappear from the hierarchy, since a fixed filter value collapses that level's cardinality from 1-N to 1-1 (a redundant single-item group). Build Plan step 8 covers threading the existing `sourceFilter`/`season`/`competition` filter values into the hierarchy builder as an omit-set, collapsing the data shape accordingly, and skipping the corresponding disclosure row(s) in `MatchesPanel` — matches themselves stay correctly scoped, only the redundant grouping row is omitted.
- Plan finalized and reviewed (step 8 example corrected); confirmed ready for implementation of Team level (step 7) and filter-driven level omission (step 8).
- Implementation started 2026-09-09: Build Plan steps 7 (Team level) and 8 (filter-driven level omission).
- Implementation complete (2026-09-09) for Build Plan steps 7 and 8. `utils/clubMatches.js`:
  `groupMatchesHierarchy` now takes `{ teams, omitLevels }`; resolves each match's own-club team
  via `homeTeam`/`awayTeam` matched against `club.teams` scoped by source+season (home side
  checked first, then away), falling back to the `UNKNOWN_TEAM` (`null`) bucket sorted last;
  `omitLevels` (`'source' | 'season' | 'competition'`) skips building the `seasons`/`competitions`
  wrapper arrays and passes groups straight through to the next level — `source` is accepted but
  has no effect on the data shape (always the outermost array), only on rendering. `ClubDetailPage.jsx`:
  `MatchesPanel` now receives `sourceFilter`/`season`/`competition` and derives `omitLevels` from
  them; the render tree was refactored into `renderSourceLevel` / `renderSeasonLevel` /
  `renderCompetitionLevel` / `CompetitionBody`, each returning an array of `<li>` items — an
  omitted level contributes no toggle row and its children's `<li>`s splice directly into the
  parent's list, one nesting level shallower. `CompetitionBody` now hosts the "view competition"
  link, the per-competition empty state (checked via `teams.length === 0`), and the Team-level
  toggle list (`detail.unknownTeam` label for the fallback bucket), used identically whether the
  Competition level renders its own toggle or is itself omitted. Added `detail.unknownTeam` i18n
  key to `ca.js`/`en.js`/`es.js`. Rewrote `clubMatches.test.js` (Team bucketing home/away/unknown,
  scoping by source+season, alphabetical-with-unknown-last sort, and level-omission combinations)
  and updated `ClubDetailPage.test.jsx` (full 4-level expand/collapse walk, omission of Season+
  Competition when both filters are fixed, omission of Source when a specific source is selected,
  restoring groupings when filters return to "all", and the view-competition link staying correct
  under omission). Full frontend test suite (191 tests), `eslint .`, and `vite build` all pass.
  Not verified in a running browser against a live backend in this session.
- Verified all acceptance criteria (Team level, filter-driven level omission) against delivered behavior; full test suite/eslint/build pass.
