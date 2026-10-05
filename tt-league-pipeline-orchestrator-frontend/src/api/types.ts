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
  readonly steps?: readonly StepStatusSummary[]
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
}

export interface Artifact {
  readonly kind: string
  readonly sha256: string
  readonly sizeBytes: number
  readonly createdAt: string
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
  readonly receivedAt: string | null
}

export type RunDetail = Omit<RunSummary, 'steps'> & {
  readonly steps: readonly Step[]
  readonly artifacts: readonly Artifact[]
  readonly importReport: ImportReport | null
  readonly issues: readonly string[]
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
