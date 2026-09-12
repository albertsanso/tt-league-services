 dev# FEATURES.md — Feature Registry & Build Plans

This file is the single source of truth for planned, in-progress, and completed features.

**For humans:** Add new features under `## Backlog` using the template in [`task-management.md`](./task-management.md).
**For agents:** Only work on features marked `status: ready`. Update status as you progress. Never modify features marked `status: done` or `status: in-progress` unless explicitly asked.

---

## Status Legend

| Status | Meaning |
|-|-|
| `idea` | Captured but not planned yet — no build plan written |
| `planned` | Build plan written, not yet ready to implement |
| `ready` | Build plan approved, agent can start |
| `in-progress` | Currently being implemented |
| `in-review` | Implementation finalized and awaiting user review |
| `done` | Shipped after explicit user approval |
| `blocked` | Waiting on a dependency or decision |

---

## Main index

- [FEAT-00055: Access actas from Matches search](### [FEAT-00055] Access actas from Matches search)
- [FEAT-00054: Club detail Summary and Stats tabs](### [FEAT-00054] Club detail Summary and Stats tabs)
- [FEAT-00053: Link list of club players to Player details](### [FEAT-00053] Link list of club players to Player details)


## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

No features currently in the backlog.
## Done

### [FEAT-00055] Access actas from Matches search
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Let users open the original acta (match report) document for a match directly from the Matches search results.

#### Description
- Align styling and layout of the Matches search results with other search results (Players, Clubs) to include a link or action to view the source acta document for each match.
- Each match row/detail in the Matches search results should show a link or action to view its source acta document.
- Selecting the acta action should open or download the acta file associated with that match.
- Matches with no associated acta available should show the action as disabled or hidden, without erroring.
- Frontend rendering of the acta must follow the wiremock spec in [acta-simplified-rfetm.md](../frontend/actas/acta-simplified-rfetm.md).

#### Acceptance Criteria
- [x] Matches search results show a link or action to view the source acta document for each match
- [x] Align frontend styling and layout of the Matches search results with other search results (Players, Clubs) to include the acta link/action
- [x] Each match row/detail in Matches search shows a link or action to view its source acta document
- [x] Selecting the acta action opens or downloads the acta file associated with that match
- [x] Matches with no associated acta available show the action as disabled or hidden, without erroring
- [x] The frontend acta view matches the structure/fields defined in [acta-simplified-rfetm.md](../frontend/actas/acta-simplified-rfetm.md)

#### Feature Details
→ See [FEAT-00055-DETAILS.md](./FEAT-00055-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00054] Club detail Summary and Stats tabs
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Give club detail page visitors a landing overview and a dedicated stats breakdown, instead of arriving directly on the raw Players list.

#### Acceptance Criteria
- [x] Club detail page defaults to a new Summary tab instead of Players. 
- [x] Summary tab shows player/match/win-rate/competition stat tiles, a recent-matches card, and a top-players card, each linking into the corresponding tab. 
- [x] A new Stats tab shows win/draw/loss breakdown per competition for a selected season plus a win-rate-by-season trend; existing Players and Matches tabs and their filters are unchanged.
- [x] Implementation matches the layout, tab order, and content documented in [`/docs/frontend/club_details_landing.md`](../frontend/club_details_landing.md); any deviation is noted in FEAT-00054-DETAILS.md before merging
- [x] Summary tab adds a "Top performer player" ranking by actual win rate (backed by a per-player win/draw/loss aggregation added to the club detail API), distinct from the existing "Most-capped players" card which ranks by number of competitions played.

#### Feature Details
→ See [FEAT-00054-DETAILS.md](./FEAT-00054-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00053] Link list of club players to Player details
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Let a user viewing a club's players list click a player to open that player's details page, matching the linking pattern already used in the player search results.

#### Acceptance Criteria
- [x] Each player in the club detail Players panel that has a canonicalPlayerId is rendered as a link to the player details page (matching PlayersSearchPage's linking pattern), carrying source/season context as return search params
- [x] Players without a canonicalPlayerId (unmatched/unconsolidated players) render as before, without a link
- [x] Existing club players list tests are updated/extended to cover the new link behavior

#### Feature Details
→ See [FEAT-00053-DETAILS.md](./FEAT-00053-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.


