import { emptyFilter, emptyRunNowForm, hasErrors, parseMatchDays, toTriggerRequest, validateRunNow } from './runNowForm'
import type { RunNowForm } from './runNowForm'

function form(overrides: Partial<RunNowForm>): RunNowForm {
  return { ...emptyRunNowForm(), source: 'RFETM', season: '2025-2026', scopeType: 'OPEN_MATCH_DAYS', ...overrides }
}

describe('validateRunNow', () => {
  it('requires source, season and scope with no defaults', () => {
    const errors = validateRunNow(emptyRunNowForm())
    expect(errors.source).toBeDefined()
    expect(errors.season).toBeDefined()
    expect(errors.scopeType).toBeDefined()
  })

  it.each([
    ['2025', true],
    ['2025-26', true],
    ['2025-2027', true],
    ['2026-2025', true],
    ['2025-2026', false],
  ])('season %s invalid=%s', (season, invalid) => {
    expect(validateRunNow(form({ season })).season !== undefined).toBe(invalid)
  })

  it('accepts a valid open match days form', () => {
    expect(hasErrors(validateRunNow(form({})))).toBe(false)
  })

  it('rejects group scope with all sources', () => {
    const errors = validateRunNow(form({ source: 'ALL', scopeType: 'GROUP', filters: [{ ...emptyFilter(), group: 'A' }] }))
    expect(errors.scopeType).toMatch(/exactly one source/)
  })

  it('rejects group scope without a filter or with an empty one', () => {
    expect(validateRunNow(form({ scopeType: 'GROUP', filters: [] })).filters).toBeDefined()
    expect(validateRunNow(form({ scopeType: 'GROUP', filters: [emptyFilter()] })).filterRows?.[0]).toBeDefined()
  })

  it('rejects bad match days', () => {
    const errors = validateRunNow(
      form({ scopeType: 'GROUP', filters: [{ ...emptyFilter(), group: 'A', matchDays: '3, x' }] }),
    )
    expect(errors.filterRows?.[0]).toMatch(/Match days/)
    expect(parseMatchDays('0')).toBe('invalid')
    expect(parseMatchDays('3, 4')).toEqual([3, 4])
    expect(parseMatchDays(' ')).toBeNull()
  })

  it('ignores filters for other scope types', () => {
    expect(hasErrors(validateRunNow(form({ scopeType: 'FULL_SEASON', filters: [emptyFilter()] })))).toBe(false)
  })
})

describe('toTriggerRequest', () => {
  it('builds a request without filters for open match days and full season', () => {
    expect(toTriggerRequest(form({ season: ' 2025-2026 ', force: true }))).toEqual({
      source: 'RFETM',
      season: '2025-2026',
      scopeType: 'OPEN_MATCH_DAYS',
      force: true,
    })
    expect(toTriggerRequest(form({ source: 'ALL', scopeType: 'FULL_SEASON' }))).toEqual({
      source: 'ALL',
      season: '2025-2026',
      scopeType: 'FULL_SEASON',
      force: false,
    })
  })

  it('trims group filters, drops blank fields and blank rows, and parses match days', () => {
    const request = toTriggerRequest(
      form({
        scopeType: 'GROUP',
        filters: [{ ...emptyFilter(), category: ' Senior ', group: 'A', matchDays: '3, 4' }, emptyFilter()],
      }),
    )
    expect(request.filters).toEqual([{ category: 'Senior', group: 'A', matchDays: [3, 4] }])
    expect(JSON.stringify(request)).not.toContain('""')
  })
})
