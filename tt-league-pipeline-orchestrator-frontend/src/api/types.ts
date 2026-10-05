// Hand-written mirrors of the orchestrator runtime DTOs (package `...pipeline.runtime.api`).
// Instants, dates and durations are ISO-8601 strings; UUIDs are strings.

export type PipelineSource = 'RFETM' | 'BCNESA' | 'FCTT'
export type RunStatus =
  | 'QUEUED'
  | 'RUNNING_INGEST'
  | 'NO_CHANGES'
  | 'PACKED'
  | 'IMPORTING'
  | 'SUCCEEDED'
  | 'PARTIAL'
  | 'FAILED'
export type RunTrigger = 'SCHEDULED' | 'MANUAL' | 'RETRY'
export type StepKind = 'INGEST' | 'FETCH_PACKAGE' | 'IMPORT'
export type StepStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type ScopeType = 'OPEN_MATCH_DAYS' | 'GROUP' | 'FULL_SEASON'
export type TriggerOutcome = 'CREATED' | 'QUEUED' | 'REJECTED' | 'UNAVAILABLE'
export type MatchDayState = 'UPCOMING' | 'OPEN' | 'CLOSED'
export type MatchDayCompletion = 'COMPLETE' | 'IN_PROGRESS' | 'HAS_OVERDUE' | 'FUTURE'
export type TrackedMatchStatus = 'SCHEDULED' | 'AWAITING_RESULT' | 'REPORTED' | 'POSTPONED' | 'OVERDUE'
export type PolicyLevel =
  | 'MATCH_DAY'
  | 'DAY_AFTER'
  | 'DAYS_2_TO_7'
  | 'OPEN'
  | 'OVERDUE'
  | 'STOPPED'
  | 'FULL_REFRESH'

export interface ScopeFilter {
  readonly category: string | null
  readonly group: string | null
  readonly phase: string | null
  readonly territory: string | null
  readonly gender: string | null
  readonly matchDays: readonly number[] | null
}

export interface RunError {
  readonly code: string
  readonly message: string
}

export interface StepStatusSummary {
  readonly kind: StepKind
  readonly status: StepStatus
  readonly attempt: number
}

export interface RunSummary {
  readonly id: string
  readonly source: PipelineSource
  readonly season: string
  readonly filters: readonly ScopeFilter[]
  readonly fullSeason: boolean
  readonly trigger: RunTrigger
  readonly requestedBy: string | null
  readonly force: boolean
  readonly status: RunStatus
  readonly createdAt: string
  readonly startedAt: string | null
  readonly finishedAt: string | null
  readonly durationMs: number | null
  readonly error: RunError | null
  readonly ingestRunId: string | null
  readonly importJobId: string | null
  readonly retryOfRunId: string | null
  /** The platform returned an existing import job for the same content; null when unknown or no import yet. */
  readonly importJobReused: boolean | null
  readonly steps?: readonly StepStatusSummary[]
}

/** Source failures an INGEST attempt reported; null on other steps and when the ingest service did not report them. */
export interface StepHealth {
  readonly httpErrors: number
  readonly timeouts: number
  readonly parseErrors: number
}

export interface Step {
  readonly runId: string
  readonly kind: StepKind
  readonly attempt: number
  readonly status: StepStatus
  readonly startedAt: string | null
  readonly finishedAt: string | null
  readonly durationMs: number | null
  readonly externalRef: string | null
  readonly outcome: string | null
  readonly retryable: boolean | null
  readonly error: RunError | null
  readonly health: StepHealth | null
  /** Set on IMPORT steps: the platform returned an existing job instead of creating one. */
  readonly importJobReused: boolean | null
}

export interface Artifact {
  readonly kind: string
  readonly sha256: string
  readonly sizeBytes: number
  readonly createdAt: string
  /** Set once the retention cleanup deleted the file; the row is kept as history. */
  readonly purgedAt: string | null
}

export interface ImportReport {
  readonly status: string
  readonly filesSeen: number
  readonly itemsPersisted: number
  readonly skipped: number
  readonly processorFailures: number
  readonly scheduledCreated: number
  readonly upgradedToPlayed: number
  readonly rescheduled: number
  readonly partialActas: number
  readonly invalidActas: number
  readonly unresolvedPendingFixtures: number
  /** Stored matches re-applied from an amended acta; stays 0 unless the platform runs with amended-acta detection. */
  readonly amendedPlayed: number
  readonly receivedAt: string | null
}

/** Whether a run can be replayed, decided by the server (`ReplayRules`); `code` names the reason when it cannot. */
export interface ReplayEligibility {
  readonly allowed: boolean
  readonly code: string | null
}

export type RunDetail = Omit<RunSummary, 'steps'> & {
  readonly steps: readonly Step[]
  readonly artifacts: readonly Artifact[]
  readonly importReport: ImportReport | null
  readonly issues: readonly string[]
  readonly replay: ReplayEligibility
}

export interface Page<T> {
  readonly items: readonly T[]
  readonly page: number
  readonly size: number
  readonly totalItems: number
  readonly totalPages: number
}

export interface TriggerRunRequest {
  readonly source: PipelineSource | 'ALL'
  readonly season: string
  readonly scopeType: ScopeType
  readonly filters?: readonly Partial<ScopeFilter>[]
  readonly force: boolean
}

export interface TriggerResult {
  readonly source: PipelineSource
  readonly outcome: TriggerOutcome
  readonly code?: string
  readonly message?: string
  readonly run?: RunSummary
  readonly activeRunId?: string
}

export interface TriggerResponse {
  readonly results: readonly TriggerResult[]
}

export interface PendingTrigger {
  readonly source: PipelineSource
  readonly season: string
  readonly scopeType: ScopeType
  readonly filters: readonly ScopeFilter[]
  readonly force: boolean
  readonly requestedBy: string
  readonly requestedAt: string
}

export interface MatchDaySummary {
  readonly id: string
  readonly source: PipelineSource
  readonly season: string
  readonly competition: string
  readonly groupNumber: number | null
  readonly phase: string | null
  readonly round: number
  readonly firstDate: string | null
  readonly lastDate: string | null
  readonly windowEnd: string | null
  readonly graceDays: number
  readonly state: MatchDayState
  readonly closeReason: string | null
  readonly closedAt: string | null
  readonly closedBy: string | null
  readonly openedAt: string | null
  readonly lastRecomputedAt: string | null
  /** Includes the ignored matches. */
  readonly matchCounts: Readonly<Record<TrackedMatchStatus, number>>
  readonly ignoredMatches: number
  /** Computed by the server (`TrackerRules.completion`); the UI only maps it to colours and labels. */
  readonly completion: MatchDayCompletion
  /** Active (non-ignored) matches that are reported, and active matches in total. */
  readonly reportedMatches: number
  readonly totalMatches: number
}

export interface MatchDayFacets {
  readonly seasons: readonly string[]
  readonly competitions: readonly string[]
  readonly phases: readonly string[]
}

export interface MatchResult {
  readonly matchId: string
  readonly platformStatus: string
  readonly homeGamesWon: number | null
  readonly awayGamesWon: number | null
  readonly winnerTeamName: string | null
}

export interface MatchDayResults {
  readonly matchDayId: string
  readonly platformToday: string
  readonly results: readonly MatchResult[]
}

export type MatchDayEventKind =
  | 'OPENED'
  | 'CLOSED'
  | 'REOPENED'
  | 'MATCH_REPORTED'
  | 'MATCH_IGNORED'
  | 'MATCH_UNIGNORED'
  | 'MATCH_REMOVED'
  | 'NOTE'
  | 'REFRESH_REQUESTED'

export interface TrackedMatch {
  readonly matchId: string
  readonly status: TrackedMatchStatus
  readonly matchDateTime: string | null
  readonly homeTeamName: string
  readonly awayTeamName: string
  readonly firstSeenAt: string
  readonly statusChangedAt: string
  readonly lastSeenAt: string
  readonly reportedAt: string | null
  readonly reportedRunId: string | null
  readonly ignoredAt: string | null
  readonly ignoredBy: string | null
}

export interface MatchDayEvent {
  readonly id: string
  readonly matchId: string | null
  readonly kind: MatchDayEventKind
  readonly actor: string
  readonly occurredAt: string
  readonly runId: string | null
  readonly note: string | null
}

export interface MatchDayDetail {
  readonly matchDay: MatchDaySummary
  readonly matches: readonly TrackedMatch[]
  readonly events: readonly MatchDayEvent[]
  /** The runs that touched the match day, newest first, without steps. */
  readonly runs: readonly RunSummary[]
}

export interface MatchDayActionRequest {
  readonly note?: string
}

export interface MatchDayNoteRequest {
  readonly text: string
  readonly matchId?: string
}

export interface PollingPolicy {
  readonly source: PipelineSource
  readonly matchDay: string
  readonly matchDayStartOffset: string
  readonly dayAfter: string
  readonly daysTwoToSeven: string
  readonly open: string
  readonly overdue: string
  readonly overdueStopAfterDays: number
  readonly fullRefresh: string
  readonly noChangeThreshold: number
  readonly overridden: boolean
  readonly version: number
  readonly updatedBy: string | null
  readonly updatedAt: string | null
}

export interface PollingPolicyRequest {
  readonly matchDay: string
  readonly matchDayStartOffset: string
  readonly dayAfter: string
  readonly daysTwoToSeven: string
  readonly open: string
  readonly overdue: string
  readonly overdueStopAfterDays: number
  readonly fullRefresh: string
  readonly noChangeThreshold: number
  readonly version: number
}

export interface PollSchedule {
  readonly id: string
  readonly source: PipelineSource
  readonly season: string
  readonly kind: 'FULL_REFRESH' | 'GROUP'
  readonly scopeKey: string
  readonly filter: ScopeFilter | null
  readonly level: PolicyLevel
  readonly interval: string | null
  readonly consecutiveNoChange: number
  readonly nextRunAt: string | null
  readonly lastRunAt: string | null
  readonly pendingRunId: string | null
  readonly stoppedAt: string | null
  readonly stopReason: string | null
  readonly version: number
}

/** Orchestrator ProblemDetail body, or the platform error body (`message`). */
export interface Problem {
  readonly type?: string
  readonly title?: string
  readonly status?: number
  readonly detail?: string
  readonly instance?: string
  readonly message?: string
  readonly code?: string
  readonly field?: string
  readonly errors?: unknown
  readonly results?: readonly TriggerResult[]
}

export type RunEvent = Omit<RunSummary, 'steps'>
export type StepEvent = Step

export interface PendingTriggerEvent {
  readonly source: PipelineSource
  readonly state: 'QUEUED' | 'LAUNCHED' | 'DROPPED'
  readonly requestedBy: string
  readonly runId?: string
  readonly code?: string
}

export interface MatchDaysEvent {
  readonly source: PipelineSource
  readonly season: string
  /** Null after a recompute, which can touch any match day of the source and season. */
  readonly matchDayId: string | null
  readonly cause: 'RECOMPUTED' | 'ACTION'
}

export interface LoginResponse {
  readonly token: string
  readonly type: string
  readonly username: string
}

export interface CurrentUser {
  readonly id: string
  readonly username: string
  readonly email: string | null
  readonly createdAt: string
  readonly active: boolean
  readonly roles: readonly string[]
  readonly permissions: readonly string[]
}

// ---- Statistics (`/api/pipeline/statistics/...`). Durations are whole seconds; dates are local days in `zone`.

export interface DailyStatsRow {
  readonly date: string
  readonly source: PipelineSource
  readonly runs: number
  readonly failures: number
  readonly matchesReported: number
  readonly avgTimeToReportSeconds: number | null
  readonly pendingEndOfDay: number
  readonly computedAt: string
}

export interface DailyStatsResponse {
  readonly zone: string
  readonly rows: readonly DailyStatsRow[]
}

export interface DayOutcomes {
  readonly date: string
  readonly source: PipelineSource
  readonly succeeded: number
  readonly noChanges: number
  readonly partial: number
  readonly failed: number
}

export interface StepAverage {
  readonly source: PipelineSource
  readonly kind: StepKind
  readonly attempts: number
  readonly avgStepSeconds: number | null
}

export interface RunOutcomesResponse {
  readonly zone: string
  readonly days: readonly DayOutcomes[]
  readonly stepAverages: readonly StepAverage[]
}

/** `competition` is null on the total row of a source. */
export interface TimeToReportRow {
  readonly source: PipelineSource
  readonly competition: string | null
  readonly count: number
  readonly medianSeconds: number | null
  readonly p90Seconds: number | null
}

export interface TimeToReportResponse {
  readonly season: string
  readonly rows: readonly TimeToReportRow[]
}

export interface SourcePending {
  readonly source: PipelineSource
  readonly under1Day: number
  readonly days1To2: number
  readonly days2To7: number
  readonly over7Days: number
  readonly overdue: number
}

export interface PendingResponse {
  readonly asOf: string
  readonly sources: readonly SourcePending[]
}

export interface DayCorrections {
  readonly date: string
  readonly source: PipelineSource
  readonly amendedPlayed: number
}

export interface SourceCorrections {
  readonly source: PipelineSource
  readonly amendedPlayed: number
}

export interface CorrectionsResponse {
  readonly zone: string
  readonly days: readonly DayCorrections[]
  readonly totals: readonly SourceCorrections[]
}

export interface SourceHealthCounts {
  readonly source: PipelineSource
  readonly httpErrors: number
  readonly timeouts: number
  readonly parseErrors: number
  readonly ingestAttempts: number
  readonly sourceUnavailable: number
  readonly healthUnknown: number
}

export interface DayHealth extends SourceHealthCounts {
  readonly date: string
}

export interface SourceHealthResponse {
  readonly zone: string
  readonly days: readonly DayHealth[]
  readonly totals: readonly SourceHealthCounts[]
}

export interface ProgressPoint {
  readonly date: string
  readonly reported: number
  readonly pending: number
}

export interface MatchDayProgress {
  readonly matchDayId: string
  readonly competition: string
  readonly groupNumber: number | null
  readonly phase: string | null
  readonly round: number
  readonly state: MatchDayState
  readonly windowStart: string
  readonly windowEnd: string
  readonly active: number
  readonly reported: number
  readonly postponed: number
  readonly pending: number
  readonly points: readonly ProgressPoint[]
}

export interface ReportingProgressResponse {
  readonly source: PipelineSource
  readonly season: string
  readonly zone: string
  readonly matchDays: readonly MatchDayProgress[]
}
