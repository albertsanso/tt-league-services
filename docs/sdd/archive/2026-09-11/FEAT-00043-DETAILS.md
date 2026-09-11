# Build Plan
1. **`tt-data-league-frontend/src/pages/ClubsConsolidationPanel.jsx`: lock the Canonical Name field by default**
   - Add `const [canonicalNameEditable, setCanonicalNameEditable] = useState(false)` alongside the existing `canonicalName`/`primaryClubId` state.
   - In `openDialog()`, reset `setCanonicalNameEditable(false)` each time the dialog opens (so it always starts locked, even on repeated opens with different selections).
   - On the `#consolidate-canonical-name` input, add `readOnly={!canonicalNameEditable}` (keep it `required`/focusable; do not use `disabled`, which would drop it from the submitted/validated value and hurt keyboard access) and `aria-readonly={!canonicalNameEditable}`.
   - Add an "Edit" button next to the input (reuse `secondary-button` styling, wrap input + button in the existing `auth-field` label or a small flex wrapper) with `type="button"`, `onClick={() => setCanonicalNameEditable(true)}`, and an accessible label (`t('clubsConsolidation.editCanonicalName')`). Hide/disable it once already editable, or simply leave it visible but inert since re-clicking has no effect.
   - `canConfirm` and `handleConfirm` logic stay unchanged — the field's value (defaulted via `longestName(selectedClubs)`) remains a valid canonical name whether or not the user chose to edit it.

2. **i18n**: add `clubsConsolidation.editCanonicalName` key to `src/i18n/ca.js` (canonical source, e.g. `'Edita el nom'`) and confirm `en.js`/other locale files continue to spread `ca.clubsConsolidation` so the new key propagates without duplication.

3. **Tests**: update `ClubsConsolidationPanel.test.jsx`
   - Assert the canonical name input is read-only immediately after opening the dialog (`readOnly` true / typing into it without clicking Edit does not change the value).
   - Assert clicking the Edit button makes the field editable and typing updates `canonicalName`/the input value.
   - Assert re-opening the dialog (e.g. close then select a different pair and reopen) resets the field back to read-only.
   - Keep existing coverage for dialog open/submit flow, success/error banners green.

4. **Verification**: `npm run lint` and the frontend test suite for the changed files (per `tt-data-league-frontend` `AGENTS.md`); manually confirm in the running app that the Canonical Name box is greyed out/non-typable on dialog open and becomes editable only after clicking Edit.

5. **Sync the Canonical Name to the selected primary (Main) club**
   - Track whether the user has manually edited the canonical name in this dialog session, e.g. `const [canonicalNameTouched, setCanonicalNameTouched] = useState(false)`, reset to `false` in `openDialog()` alongside `canonicalNameEditable`.
   - While `canonicalNameTouched` is `false` (i.e. the user hasn't typed a custom value yet), `canonicalName` must always mirror the currently selected primary club's `name` — set it in `openDialog()` from `selectedClubs[0]` as today, and also update it whenever the radio selection changes (`onChange={() => setPrimaryClubId(club.id)}` in the primary-club fieldset): if `!canonicalNameTouched`, set `canonicalName` to `club.name`.
   - In the canonical-name input's `onChange`, set `canonicalNameTouched` to `true` before updating `canonicalName`, so once the user edits the field, further primary-club selection changes no longer overwrite their custom value (avoids clobbering an intentional edit).
   - Rationale: "the Canonical Name displayed must be related to the Main selected club" is satisfied by default-mirroring; "if the Canonical Name is edited, when consolidating the name must be changed in the selected main club" is already satisfied end-to-end because `ConsolidateClubsCommandHandler` (FEAT-00041) renames the primary club to `canonicalName` on consolidation — no backend change needed, this step only fixes the frontend default/sync behavior that feeds that existing rename.

6. **Tests**: extend `ClubsConsolidationPanel.test.jsx`
   - Assert the canonical name defaults to the first (default primary) selected club's name on dialog open.
   - Assert switching the primary-club radio selection updates the (untouched) canonical name field to the newly selected club's name.
   - Assert that once the user edits the canonical name (after clicking Edit), switching the primary-club radio selection no longer overwrites the custom value.

# Implementation Guidelines
- No backend/domain changes required — this is presentation-only; the consolidate request payload, validation (`canConfirm`), and the existing primary-club rename-on-consolidate behavior are unaffected.
- Use `readOnly`, not `disabled`, on the input so its value is still included in form semantics and it stays keyboard-focusable/reachable by screen readers per the frontend `AGENTS.md` accessibility requirements.

# Notes
- Reported defect: the Canonical Name field in the Consolidate dialog is editable from the moment the dialog opens; it should start locked and only become editable after an explicit user action (e.g. an "Edit" button).
- Implemented: `canonicalNameEditable` state (defaults false, reset on each `openDialog()`), input uses `readOnly`/`aria-readonly` instead of `disabled`, and an "Edit" button (`clubsConsolidation.editCanonicalName` key) unlocks it. Added `.canonical-name-field`/`input[readonly]` CSS. Tests: 168/168 pass (added a lock/unlock/reopen-reset case to `ClubsConsolidationPanel.test.jsx`). Lint clean, build succeeds.
- Added requirement: canonical name must stay synchronized to the selected Main (primary) club by default, and only stop syncing once the user has manually edited it; the existing consolidate flow already applies the (possibly edited) canonical name to the primary club, so no backend change is needed.
- Implemented: dropped the old `longestName(...)` default; `openDialog()` now seeds `canonicalName` from `selectedClubs[0].name` (the default primary club). Added `canonicalNameTouched` state (reset per dialog open) and a `selectPrimaryClub(club)` helper used by the primary-club radio `onChange` that updates `canonicalName` to the newly selected club's name only while `!canonicalNameTouched`. The canonical-name input's `onChange` sets `canonicalNameTouched` before updating the value, so a manual edit is preserved across later primary-club changes. Updated `ClubsConsolidationPanel.test.jsx` defaults/assertions and added a sync-then-touch test. Tests: 169/169 pass, lint clean, build succeeds.
