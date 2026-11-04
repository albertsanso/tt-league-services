/** Labels for the codes of the server's replay decision; the UI never derives the decision itself. */
export const REPLAY_BLOCKED_LABELS: Readonly<Record<string, string>> = {
  RUN_ACTIVE: 'The run has not finished yet',
  NO_PACKAGE: 'This run has no stored package to replay',
  ARTIFACT_PURGED: 'The stored package was purged by the retention policy',
}

export function replayBlockedLabel(code: string | null): string {
  return (code !== null && REPLAY_BLOCKED_LABELS[code]) || 'This run cannot be replayed'
}

export const REUSED_IMPORT_LABEL = 'Existing import job reused — nothing was re-imported'
