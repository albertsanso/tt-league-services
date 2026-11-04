import { formatCount, formatHours, formatPercent, shortDate, toHours } from './format'

describe('formatHours', () => {
  it('shows server seconds as hours with one decimal', () => {
    expect(formatHours(5400)).toBe('1.5 h')
    expect(formatHours(0)).toBe('0.0 h')
    expect(formatHours(3600 * 26.04)).toBe('26.0 h')
    expect(formatHours(1800)).toBe('0.5 h')
  })

  it('shows a dash for a missing or negative value', () => {
    expect(formatHours(null)).toBe('—')
    expect(formatHours(undefined)).toBe('—')
    expect(formatHours(-1)).toBe('—')
  })
})

describe('toHours', () => {
  it('rounds to one decimal for chart axes and keeps missing values', () => {
    expect(toHours(5400)).toBe(1.5)
    expect(toHours(4000)).toBe(1.1)
    expect(toHours(null)).toBeNull()
    expect(toHours(-5)).toBeNull()
  })
})

describe('small formatters', () => {
  it('shortens a date and formats counts and percentages', () => {
    expect(shortDate('2026-10-05')).toBe('10-05')
    expect(shortDate('10-05')).toBe('10-05')
    expect(formatCount(3)).toBe('3')
    expect(formatCount(null)).toBe('—')
    expect(formatPercent(6, 8)).toBe('75%')
    expect(formatPercent(0, 8)).toBe('0%')
    expect(formatPercent(0, 0)).toBe('—')
  })
})
