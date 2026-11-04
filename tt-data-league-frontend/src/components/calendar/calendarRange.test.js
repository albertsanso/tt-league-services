import { describe, expect, it, vi } from 'vitest'
import {
  normalizeDate, normalizeView, rangeFor, shift, titleFor, todayIso,
} from './calendarRange.js'

describe('calendarRange', () => {
  it('a day covers itself, exclusive of the next day', () => {
    expect(rangeFor('day', '2026-09-16')).toEqual({ from: '2026-09-16', to: '2026-09-17' })
  })

  it('a week starts on Monday', () => {
    // 2026-09-16 is a Wednesday; 2026-09-20 is a Sunday.
    expect(rangeFor('week', '2026-09-16')).toEqual({ from: '2026-09-14', to: '2026-09-21' })
    expect(rangeFor('week', '2026-09-20')).toEqual({ from: '2026-09-14', to: '2026-09-21' })
    expect(rangeFor('week', '2026-09-21')).toEqual({ from: '2026-09-21', to: '2026-09-28' })
  })

  it('a month covers the visible grid of whole Monday-based weeks', () => {
    // September 2026 starts on Tuesday and ends on Wednesday.
    expect(rangeFor('month', '2026-09-16')).toEqual({ from: '2026-08-31', to: '2026-10-05' })
    // February 2027 starts on Monday and ends on Sunday: exactly four weeks.
    expect(rangeFor('month', '2027-02-10')).toEqual({ from: '2027-02-01', to: '2027-03-01' })
  })

  it('never exceeds the 62-day backend limit for a month grid', () => {
    for (let month = 1; month <= 12; month += 1) {
      const anchor = `2026-${String(month).padStart(2, '0')}-15`
      const { from, to } = rangeFor('month', anchor)
      expect((new Date(to) - new Date(from)) / 86400000).toBeLessThanOrEqual(62)
    }
  })

  it('the jornada view has no date range', () => {
    expect(rangeFor('jornada', '2026-09-16')).toBeNull()
  })

  it('shifts forwards and backwards across year boundaries', () => {
    expect(shift('day', '2026-12-31', 1)).toBe('2027-01-01')
    expect(shift('week', '2026-12-28', 1)).toBe('2027-01-04')
    expect(shift('week', '2027-01-04', -1)).toBe('2026-12-28')
    expect(shift('month', '2026-12-15', 1)).toBe('2027-01-15')
    expect(shift('month', '2027-01-31', -1)).toBe('2026-12-31')
    expect(shift('month', '2026-03-31', -1)).toBe('2026-02-28')
    expect(shift('jornada', '2026-09-16', 1)).toBe('2026-09-16')
  })

  it('falls back to today for an invalid anchor', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 8, 16, 12))
    expect(todayIso()).toBe('2026-09-16')
    expect(normalizeDate('nope')).toBe('2026-09-16')
    expect(normalizeDate('2026-02-30')).toBe('2026-09-16')
    expect(normalizeDate(null)).toBe('2026-09-16')
    expect(normalizeDate('2026-09-01')).toBe('2026-09-01')
    expect(rangeFor('day', 'garbage')).toEqual({ from: '2026-09-16', to: '2026-09-17' })
    vi.useRealTimers()
  })

  it('normalises the view and titles each period', () => {
    expect(normalizeView('month')).toBe('month')
    expect(normalizeView('year')).toBe('week')
    expect(titleFor('month', '2026-09-16', 'en')).toBe('September 2026')
    expect(titleFor('week', '2026-09-16', 'en')).toContain('2026')
    expect(titleFor('day', '2026-09-16', 'en')).toContain('2026')
    expect(titleFor('jornada', '2026-09-16', 'en')).toBe('')
  })
})
