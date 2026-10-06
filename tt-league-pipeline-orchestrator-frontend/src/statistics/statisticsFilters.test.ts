import {
  DEFAULT_RANGE_DAYS,
  defaultRange,
  formatLocalDate,
  isValidSeason,
  MAX_RANGE_DAYS,
  parseStatisticsFilters,
  rangeDays,
  rangeError,
  serializeStatisticsFilters,
} from './statisticsFilters'

const TODAY = new Date(2026, 9, 5)

describe('parseStatisticsFilters', () => {
  it('defaults to every source and the last 30 days', () => {
    const filters = parseStatisticsFilters(new URLSearchParams(), TODAY)

    expect(filters.sources).toEqual([])
    expect(filters.season).toBeNull()
    expect(filters.unitKey).toBeNull()
    expect(filters.toDate).toBe('2026-10-05')
    expect(filters.fromDate).toBe('2026-09-06')
    expect(rangeDays(filters.fromDate, filters.toDate)).toBe(DEFAULT_RANGE_DAYS)
    expect(filters.errors).toEqual([])
  })

  it('reads sources, season and range from the URL', () => {
    const filters = parseStatisticsFilters(
      new URLSearchParams('source=FCTT&source=RFETM&source=FCTT&season=2026-2027&from=2026-09-01&to=2026-09-30'),
      TODAY,
    )

    expect(filters).toEqual({
      sources: ['FCTT', 'RFETM'],
      season: '2026-2027',
      unitKey: null,
      fromDate: '2026-09-01',
      toDate: '2026-09-30',
      errors: [],
    })
  })

  it('ignores invalid values and reports them', () => {
    const filters = parseStatisticsFilters(
      new URLSearchParams('source=NOPE&season=2026-2028&from=2026-02-31&to=soon'),
      TODAY,
    )

    expect(filters.sources).toEqual([])
    expect(filters.season).toBeNull()
    expect(filters.fromDate).toBe('2026-09-06')
    expect(filters.toDate).toBe('2026-10-05')
    expect(filters.errors).toHaveLength(4)
  })

  it('round-trips through the URL', () => {
    const original = parseStatisticsFilters(
      new URLSearchParams('source=BCNESA&season=2025-2026&from=2026-01-02&to=2026-03-04'),
      TODAY,
    )
    const { errors, ...values } = original
    void errors

    const again = parseStatisticsFilters(serializeStatisticsFilters(values), TODAY)

    expect(again).toEqual(original)
  })

  it('writes the range and omits an unset season', () => {
    const params = serializeStatisticsFilters({
      sources: [],
      season: null,
      unitKey: null,
      fromDate: '2026-09-06',
      toDate: '2026-10-05',
    })

    expect(params.toString()).toBe('from=2026-09-06&to=2026-10-05')
  })
})

describe('the unit filter', () => {
  it('reads and writes the unit key', () => {
    const filters = parseStatisticsFilters(new URLSearchParams('unit=season&from=2026-09-01&to=2026-09-30'), TODAY)

    expect(filters.unitKey).toBe('season')
    expect(serializeStatisticsFilters(filters).get('unit')).toBe('season')
    expect(serializeStatisticsFilters({ ...filters, unitKey: null }).has('unit')).toBe(false)
  })

  it('ignores an empty unit and reports one that is too long', () => {
    expect(parseStatisticsFilters(new URLSearchParams('unit='), TODAY)).toMatchObject({ unitKey: null, errors: [] })

    const tooLong = parseStatisticsFilters(new URLSearchParams(`unit=${'x'.repeat(65)}`), TODAY)

    expect(tooLong.unitKey).toBeNull()
    expect(tooLong.errors).toHaveLength(1)
  })
})

describe('range helpers', () => {
  it('counts days inclusively and flags reversed or oversized ranges', () => {
    expect(rangeDays('2026-10-05', '2026-10-05')).toBe(1)
    expect(rangeError({ fromDate: '2026-10-05', toDate: '2026-10-05' })).toBeNull()
    expect(rangeError({ fromDate: '2026-10-06', toDate: '2026-10-05' })).toMatch(/after/)
    expect(rangeError({ fromDate: '2025-10-05', toDate: '2026-10-04' })).toBeNull()
    expect(rangeDays('2025-09-30', '2026-10-01')).toBe(MAX_RANGE_DAYS + 1)
    expect(rangeError({ fromDate: '2025-09-30', toDate: '2026-10-01' })).toMatch(/longer/)
  })

  it('formats local dates and the default range', () => {
    expect(formatLocalDate(new Date(2026, 0, 2))).toBe('2026-01-02')
    expect(formatLocalDate(new Date(2026, 0, 1), -1)).toBe('2025-12-31')
    expect(defaultRange(new Date(2026, 2, 1))).toEqual({ fromDate: '2026-01-31', toDate: '2026-03-01' })
  })

  it('validates seasons', () => {
    expect(isValidSeason('2026-2027')).toBe(true)
    expect(isValidSeason('2026-2028')).toBe(false)
    expect(isValidSeason('26-27')).toBe(false)
  })
})
