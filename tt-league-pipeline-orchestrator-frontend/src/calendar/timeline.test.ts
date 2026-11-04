import { makeRun } from '../test/runFixtures'
import { makeEvent, makeMatchDayDetail } from '../test/matchDayFixtures'
import { actorText, buildTimeline, eventText } from './timeline'
import type { MatchDayEventKind } from '../api/types'

describe('timeline', () => {
  it('writes a text for every event kind', () => {
    const kinds: readonly [MatchDayEventKind, string][] = [
      ['OPENED', 'Match day opened'],
      ['CLOSED', 'Match day closed by ana'],
      ['REOPENED', 'Match day reopened by ana'],
      ['MATCH_REPORTED', 'Match reported'],
      ['MATCH_IGNORED', 'Match ignored by ana'],
      ['MATCH_UNIGNORED', 'Match no longer ignored (ana)'],
      ['MATCH_REMOVED', 'Match removed from the match day'],
      ['NOTE', 'Note by ana'],
      ['REFRESH_REQUESTED', 'Refresh requested by ana'],
    ]
    for (const [kind, text] of kinds) {
      expect(eventText({ kind, actor: 'ana' })).toBe(text)
    }
  })

  it('names the tracker for the system actor and adds the match when it is known', () => {
    expect(actorText('system:tracker')).toBe('the tracker')
    expect(eventText({ kind: 'CLOSED', actor: 'system:tracker' })).toBe('Match day closed by the tracker')
    expect(eventText({ kind: 'MATCH_IGNORED', actor: 'ana' }, 'CTT A – CTT B')).toBe('Match ignored by ana · CTT A – CTT B')
  })

  it('merges events and runs, newest first, with runs at their creation time', () => {
    const detail = makeMatchDayDetail()

    const items = buildTimeline(detail.events, detail.runs)

    expect(items).toHaveLength(detail.events.length + detail.runs.length)
    const times = items.map((item) => Date.parse(item.at))
    expect(times).toEqual([...times].sort((a, b) => b - a))
    expect(items[0]).toMatchObject({ type: 'event', text: 'Match day reopened by ana' })
    const runItem = items.find((item) => item.type === 'run' && item.run.id === 'run-2')
    expect(runItem).toMatchObject({ at: '2026-10-05T07:30:00Z' })
  })

  it('keeps the run id of an event so the view can link to the run', () => {
    const detail = makeMatchDayDetail()

    const refresh = buildTimeline(detail.events, detail.runs).find(
      (item) => item.type === 'event' && item.text.startsWith('Refresh requested'),
    )

    expect(refresh).toMatchObject({ type: 'event', runId: 'run-2', actor: 'ana' })
  })

  it('puts events before runs and later events first when the times are equal', () => {
    const at = '2026-10-05T10:00:00Z'
    const events = [makeEvent('a', 'OPENED', { occurredAt: at }), makeEvent('b', 'NOTE', { occurredAt: at, note: 'x', actor: 'ana' })]
    const runs = [makeRun('r', { createdAt: at })]

    const items = buildTimeline(events, runs)

    expect(items.map((item) => item.key)).toEqual(['event-b', 'event-a', 'run-r'])
  })

  it('labels the matches of the events from the given lookup', () => {
    const events = [makeEvent('a', 'MATCH_REPORTED', { matchId: 'm1' })]

    const [item] = buildTimeline(events, [], new Map([['m1', 'CTT A – CTT B']]))

    expect(item).toMatchObject({ text: 'Match reported · CTT A – CTT B', matchId: 'm1' })
  })

  it('handles a match day without events or runs', () => {
    expect(buildTimeline([], [])).toEqual([])
  })
})
