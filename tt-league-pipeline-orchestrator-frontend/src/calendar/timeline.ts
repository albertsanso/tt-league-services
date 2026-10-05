import type { MatchDayEvent, MatchDayEventKind, RunSummary } from '../api/types'

export type TimelineItem =
  | {
      readonly type: 'event'
      readonly key: string
      readonly at: string
      readonly text: string
      readonly actor: string
      readonly note: string | null
      readonly runId: string | null
      readonly matchId: string | null
    }
  | { readonly type: 'run'; readonly key: string; readonly at: string; readonly run: RunSummary }

const SYSTEM_ACTOR = 'system:tracker'

/** `ana`, or `the tracker` for the system actor. */
export function actorText(actor: string): string {
  return actor === SYSTEM_ACTOR ? 'the tracker' : actor
}

const EVENT_TEXT: Readonly<Record<MatchDayEventKind, (actor: string) => string>> = {
  OPENED: () => 'Match day opened',
  CLOSED: (actor) => `Match day closed by ${actor}`,
  REOPENED: (actor) => `Match day reopened by ${actor}`,
  MATCH_REPORTED: () => 'Match reported',
  MATCH_IGNORED: (actor) => `Match ignored by ${actor}`,
  MATCH_UNIGNORED: (actor) => `Match no longer ignored (${actor})`,
  MATCH_REMOVED: () => 'Match removed from the match day',
  NOTE: (actor) => `Note by ${actor}`,
  REFRESH_REQUESTED: (actor) => `Refresh requested by ${actor}`,
}

export function eventText(event: Pick<MatchDayEvent, 'kind' | 'actor'>, matchLabel?: string): string {
  const text = EVENT_TEXT[event.kind](actorText(event.actor))
  return matchLabel === undefined ? text : `${text} · ${matchLabel}`
}

/**
 * The events (oldest first, as the API sends them) and the runs that touched the match day, merged into one list,
 * newest first. A run sits at its creation time; events that carry a run id point to it. When times are equal,
 * events come before runs and later events come first.
 */
export function buildTimeline(
  events: readonly MatchDayEvent[],
  runs: readonly RunSummary[],
  matchLabels: ReadonlyMap<string, string> = new Map(),
): readonly TimelineItem[] {
  const items: { item: TimelineItem; order: number }[] = []
  events.forEach((event, index) => {
    items.push({
      order: index,
      item: {
        type: 'event',
        key: `event-${event.id}`,
        at: event.occurredAt,
        text: eventText(event, event.matchId === null ? undefined : matchLabels.get(event.matchId)),
        actor: event.actor,
        note: event.note,
        runId: event.runId,
        matchId: event.matchId,
      },
    })
  })
  runs.forEach((run, index) => {
    items.push({ order: events.length + index, item: { type: 'run', key: `run-${run.id}`, at: run.createdAt, run } })
  })
  items.sort((a, b) => {
    const byTime = Date.parse(b.item.at) - Date.parse(a.item.at)
    if (byTime !== 0) {
      return byTime
    }
    if (a.item.type !== b.item.type) {
      return a.item.type === 'event' ? -1 : 1
    }
    return b.order - a.order
  })
  return items.map(({ item }) => item)
}
