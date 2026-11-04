import { makeSummary } from '../test/matchDayFixtures'
import {
  hasDataFilters,
  parseCalendarFilters,
  placeEntries,
  serializeCalendarFilters,
  toRangeQuery,
  toUndatedQuery,
} from './calendarFilters'
import { weekDays } from './calendarDates'

const TODAY = '2026-10-05'

describe('parseCalendarFilters', () => {
  it('defaults the navigation to the month and today and leaves every data filter empty', () => {
    const filters = parseCalendarFilters(new URLSearchParams(), TODAY)
    expect(filters).toEqual({
      view: 'month',
      date: TODAY,
      source: null,
      season: null,
      competition: null,
      phase: null,
      state: null,
      errors: [],
    })
    expect(hasDataFilters(filters)).toBe(false)
  })

  it('round trips every value through the URL', () => {
    const params = new URLSearchParams(
      'view=week&date=2026-10-07&source=FCTT&season=2026-2027&competition=TERCERA-masculino&phase=1a%20Fase&state=OPEN',
    )
    const filters = parseCalendarFilters(params, TODAY)
    expect(filters).toMatchObject({
      view: 'week',
      date: '2026-10-07',
      source: 'FCTT',
      season: '2026-2027',
      competition: 'TERCERA-masculino',
      phase: '1a Fase',
      state: 'OPEN',
      errors: [],
    })
    expect(hasDataFilters(filters)).toBe(true)
    const { errors, ...values } = filters
    void errors
    expect(parseCalendarFilters(serializeCalendarFilters(values), TODAY)).toEqual(filters)
  })

  it('always writes view and date and leaves empty filters out', () => {
    const { errors, ...values } = parseCalendarFilters(new URLSearchParams('source=RFETM'), TODAY)
    void errors
    expect(serializeCalendarFilters(values).toString()).toBe('view=month&date=2026-10-05&source=RFETM')
  })

  it('drops invalid values and reports each one instead of replacing it', () => {
    const filters = parseCalendarFilters(
      new URLSearchParams('view=year&date=2026-02-30&source=NOPE&season=2026&state=WAITING&phase=%20%20'),
      TODAY,
    )
    expect(filters).toMatchObject({ view: 'month', date: TODAY, source: null, season: null, state: null, phase: null })
    expect(filters.errors).toHaveLength(6)
    expect(filters.errors.join(' ')).toContain('"year"')
    expect(filters.errors.join(' ')).toContain('"2026-02-30"')
    expect(filters.errors.join(' ')).toContain('"NOPE"')
    expect(filters.errors.join(' ')).toContain('"2026"')
    expect(filters.errors.join(' ')).toContain('"WAITING"')
  })

  it('rejects text longer than the server accepts', () => {
    const filters = parseCalendarFilters(new URLSearchParams({ competition: 'x'.repeat(256) }), TODAY)
    expect(filters.competition).toBeNull()
    expect(filters.errors).toHaveLength(1)
  })
})

describe('calendar queries', () => {
  it('asks for the visible month with every data filter and 200 per page', () => {
    const filters = parseCalendarFilters(
      new URLSearchParams('date=2026-10-05&source=FCTT&season=2026-2027&competition=TERCERA-masculino&phase=1a%20Fase&state=OPEN'),
      TODAY,
    )
    expect(toRangeQuery(filters, 2)).toEqual({
      source: 'FCTT',
      season: '2026-2027',
      competition: 'TERCERA-masculino',
      phase: '1a Fase',
      state: 'OPEN',
      from: '2026-09-28',
      to: '2026-11-01',
      page: 2,
      size: 200,
    })
  })

  it('asks for the visible week', () => {
    const filters = parseCalendarFilters(new URLSearchParams('view=week&date=2026-10-07'), TODAY)
    expect(toRangeQuery(filters, 0)).toMatchObject({ from: '2026-10-05', to: '2026-10-11', page: 0 })
  })

  it('asks for the undated match days without a date range', () => {
    const filters = parseCalendarFilters(new URLSearchParams('source=BCNESA'), TODAY)
    const query = toUndatedQuery(filters)
    expect(query).toMatchObject({ source: 'BCNESA', undated: true, page: 0, size: 200 })
    expect(query.from).toBeUndefined()
    expect(query.to).toBeUndefined()
  })
})

describe('placeEntries', () => {
  const days = weekDays('2026-10-05')

  it('puts each entry on its first date and keeps the API order', () => {
    const a = makeSummary('a', { firstDate: '2026-10-06' })
    const b = makeSummary('b', { firstDate: '2026-10-06' })
    const c = makeSummary('c', { firstDate: '2026-10-09' })
    const placed = placeEntries([a, b, c], days)
    expect(placed.get('2026-10-06')).toEqual([a, b])
    expect(placed.get('2026-10-09')).toEqual([c])
    expect(placed.get('2026-10-05')).toEqual([])
    expect([...placed.keys()]).toEqual(days)
  })

  it('puts an entry that started before the period on the first visible day', () => {
    const early = makeSummary('early', { firstDate: '2026-10-03', lastDate: '2026-10-06' })
    expect(placeEntries([early], days).get('2026-10-05')).toEqual([early])
  })

  it('skips undated entries and entries after the period', () => {
    const undated = makeSummary('u', { firstDate: null, lastDate: null })
    const late = makeSummary('l', { firstDate: '2026-10-20' })
    const placed = placeEntries([undated, late], days)
    expect([...placed.values()].flat()).toEqual([])
  })

  it('handles an empty period', () => {
    expect(placeEntries([makeSummary('a')], []).size).toBe(0)
  })
})
