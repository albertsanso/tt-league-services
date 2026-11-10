# FEATURES.md — Feature Registry & Build Plans

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

- [FEAT-00120: tt-league frontend client app season calendar redesign](#feat-00120-tt-league-frontend-client-app-season-calendar-redesign)

- [FEAT-00119: tt-league client app - allow all sources for current season as default filters in calendar view](#feat-00119-tt-league-client-app---allow-all-sources-for-current-season-as-default-filters-in-calendar-view)




## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

No features currently in the backlog.
## Done

### [FEAT-00120] tt-league frontend client app season calendar redesign
- **Status:** done
- **Priority:** medium
- **Effort:** large
- **Depends on:** —

#### Goal
Redesign the /calendari page of tt-data-league-frontend, following the approved mockup, so that, now that it opens on all sources by default, users can see the calendar at the full width of the page without scrolling past the filters, tell the state and source of each match at a glance, find any match of a busy day or week by expanding its source and then its competition, and open the details of a match in a dialog by selecting it in the calendar, on desktop and phone, with no backend change.

#### Acceptance Criteria
- [x] At desktop width the calendar grid is visible without scrolling on first load, and the filters take no more than one row
- [x] At phone width the filters collapse behind a keyboard-accessible control that shows how many filters are active
- [x] Calendar entries, group headers, month day cells and the match dialog show the match state by more than colour alone, and a translated legend explains every state
- [x] With all sources selected, the source is shown on each source group header in the week view, on each entry in the day and jornada views, and in the match dialog
- [x] In the month view each day shows its total match count and its count per state instead of individual matches, and selecting that day with mouse or keyboard opens the day view for that date, where all of its matches are listed
- [x] The week view has no hourly grid and shows each day's matches grouped by source and then by competition: seven day columns sharing the full width of the calendar from 1280px, and the days stacked in one column below 1280px; a day header selected with mouse or keyboard opens the day view for that date
- [x] When the week view loads, and whenever the week or a filter changes, each day shows only its source groups, all collapsed; each source header shows the source, the number of matches and the count per state, and no competition or match is visible
- [x] Expanding a source group shows its competitions as collapsed headers, each with the competition name, the number of matches and the count per state; expanding a competition shows all the matches of that source and competition for that day in time order, with no "more" link and no hidden match
- [x] Every group header can be collapsed again; collapsing a source also collapses its competitions, so that expanding it again shows them collapsed; each day and group expands independently of the others
- [x] Source and competition headers are native buttons with an accessible name (group, match count and state counts), `aria-expanded` and `aria-controls`, and work with Enter and Space; match entries in the week, day and jornada views are native buttons that open the match dialog
- [x] Expanding or collapsing a group changes neither the URL nor opens a dialog
- [x] There is no detail panel below or beside the calendar in any view: the calendar takes the full width of the content area, followed only by the legend, and there is no sort control
- [x] On wide screens the calendar page is not limited to the 1360px content width used by other pages: the calendar fills the horizontal space between the sidebar and the window edge, keeping the page padding, with no empty side gutter at 1920px; the width of other pages does not change
- [x] Clicking, or pressing Enter or Space on, a match entry in the week, day or jornada view opens a modal dialog showing that match's details: teams, date and time, venue and city, competition, group, phase and round, source, state with icon and text, the result when the match was played, and who marked it overdue and when, when it was marked manually
- [x] The match dialog is accessible: it has the role dialog, `aria-modal` and an accessible name containing the teams; focus moves into it when it opens and stays inside it while it is open; Escape, a close button and a click on the backdrop close it; focus returns to the entry that opened it when that entry still exists; the page behind does not scroll
- [x] The match dialog contains a link to the match summary page that keeps the existing return to the calendar with its URL state, and the mark and clear overdue action with its existing permission rule, busy state, and conflict and error messages; after marking or clearing, the open dialog shows the refreshed state
- [x] Opening and closing the match dialog changes neither the URL nor the calendar's groups, and a dialog never reopens on reload
- [x] The match dialog fits a 375px screen without horizontal scroll and scrolls inside itself when its content is taller than the screen
- [x] Matches without a date are still reachable: they are listed in an undated group below the calendar, each as a native button that opens the match dialog
- [x] Dates and times display in the selected app language and in Europe/Madrid time, including in the match dialog
- [x] Existing behavior is preserved: all four views, source/season/competition/group/team filters, navigation, URL state and return from a match summary; links that still carry `sort` or `selected` open without error and ignore them
- [x] All-sources behavior from FEAT-00119 is preserved: all sources and newest season by default, an error instead of a partial calendar if a source fails, and a prompt to pick a source in the jornada view
- [x] Mark and clear overdue, including its permission rule, and the loading, empty and error states keep working
- [x] All new copy is translated in ca, es and en, new controls have accessible names and keyboard support, and styling follows docs/frontend/design-contract.md
- [x] No REST, GraphQL, MCP or backend change is made, the grouping and the dialog use only data already returned by the calendar endpoints, and calendar states and deadlines still come from the API
- [x] npm run lint, npm test, npm run build and mvn -pl tt-data-league-frontend -am test pass, and the redesign is checked in the running app at 1920px, 1440px, 1280px, tablet and phone widths, including the three expansion levels, opening and closing the dialog, and marking overdue, all with the keyboard

#### Feature Details
→ See [FEAT-00120-DETAILS.md](./FEAT-00120-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00119] tt-league client app - allow all sources for current season as default filters in calendar view
- **Status:** done
- **Priority:** medium
- **Effort:** small
- **Depends on:** —

#### Goal
Calendar view opens with all sources selected for the current season by default, so users see the full current-season calendar without first choosing a source.

#### Acceptance Criteria
- [x] Opening the calendar view without source/season URL params pre-selects the current season with all sources included
- [x] The default all-sources selection aggregates matches from RFETM, FCTT and BCNESA for that season
- [x] Users can still narrow the calendar to a single source, and the season/competition/group/team filters keep working
- [x] Choosing an explicit source in the URL overrides the default
- [x] Filter labels and the all-sources option remain translated in ca, es and en

#### Feature Details
→ See [FEAT-00119-DETAILS.md](./FEAT-00119-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---
