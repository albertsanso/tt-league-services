/** Labels for the codes of the server retry decision for a unit; the UI never derives the decision itself. */
export const UNIT_RETRY_BLOCKED_LABELS: Readonly<Record<string, string>> = {
  RUN_ACTIVE: 'The run, or another run of this source, has not finished yet',
  UNIT_NOT_RETRYABLE: 'Only a failed or skipped unit can be retried',
}

export function unitRetryBlockedLabel(reason: string | null): string {
  return (reason !== null && UNIT_RETRY_BLOCKED_LABELS[reason]) || 'This unit cannot be retried'
}
