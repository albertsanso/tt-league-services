import { parseRunFilters, serializeRunFilters, toRunListQuery } from './runFilters'

describe('run filters in the URL', () => {
  it('round-trips repeated parameters', () => {
    const query = '?source=RFETM&source=FCTT&status=FAILED&unit=season&from=2026-10-01&to=2026-10-05&page=2'
    const filters = parseRunFilters(new URLSearchParams(query))
    expect(filters.errors).toEqual([])
    expect(filters.sources).toEqual(['RFETM', 'FCTT'])
    expect(filters.statuses).toEqual(['FAILED'])
    expect(filters.unitKey).toBe('season')
    expect(filters.page).toBe(2)
    expect(serializeRunFilters(filters).toString()).toBe(query.slice(1))
  })

  it('reports and drops invalid values instead of replacing them', () => {
    const filters = parseRunFilters(
      new URLSearchParams('source=NOPE&status=BAD&from=2026-13-40&to=yesterday&page=0'),
    )
    expect(filters.sources).toEqual([])
    expect(filters.statuses).toEqual([])
    expect(filters.fromDate).toBeNull()
    expect(filters.toDate).toBeNull()
    expect(filters.page).toBe(1)
    expect(filters.errors).toHaveLength(5)
  })

  it('ignores an empty unit and reports one that is too long', () => {
    expect(parseRunFilters(new URLSearchParams('unit='))).toMatchObject({ unitKey: null, errors: [] })

    const filters = parseRunFilters(new URLSearchParams(`unit=${'x'.repeat(65)}`))

    expect(filters.unitKey).toBeNull()
    expect(filters.errors).toHaveLength(1)
  })

  it('reports from after to', () => {
    const filters = parseRunFilters(new URLSearchParams('from=2026-10-05&to=2026-10-01'))
    expect(filters.errors).toHaveLength(1)
  })

  it('omits page 1 from the URL', () => {
    expect(serializeRunFilters({ sources: [], statuses: [], unitKey: null, fromDate: null, toDate: null, page: 1 }).toString()).toBe('')
  })

  it('converts local dates to instants with an exclusive, next-day "to" bound and a 0-based page', () => {
    const filters = parseRunFilters(new URLSearchParams('source=RFETM&unit=season&from=2026-10-01&to=2026-10-05&page=3'))
    const query = toRunListQuery(filters)
    expect(query.unitKey).toBe('season')
    expect(query.from).toBe(new Date(2026, 9, 1).toISOString())
    expect(query.to).toBe(new Date(2026, 9, 6).toISOString())
    expect(query.page).toBe(2)
    expect(query.size).toBe(20)
    expect(query.source).toEqual(['RFETM'])
    expect(query.status).toBeUndefined()
  })
})
