import {
  addDays,
  dayLabel,
  isValidIsoDate,
  monthGrid,
  periodTitle,
  shift,
  todayIso,
  visibleRange,
  weekDays,
  weekStart,
} from './calendarDates'

describe('calendarDates', () => {
  it('validates ISO calendar dates strictly', () => {
    expect(isValidIsoDate('2026-10-05')).toBe(true)
    expect(isValidIsoDate('2028-02-29')).toBe(true)
    expect(isValidIsoDate('2027-02-29')).toBe(false)
    expect(isValidIsoDate('2026-13-01')).toBe(false)
    expect(isValidIsoDate('2026-1-5')).toBe(false)
    expect(isValidIsoDate('yesterday')).toBe(false)
  })

  it('adds days across month and year ends without a time-zone shift', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
    expect(addDays('2026-03-29', 1)).toBe('2026-03-30')
    expect(addDays('2026-10-25', 1)).toBe('2026-10-26')
  })

  it('starts weeks on Monday', () => {
    expect(weekStart('2026-10-05')).toBe('2026-10-05')
    expect(weekStart('2026-10-11')).toBe('2026-10-05')
    expect(weekStart('2026-10-07')).toBe('2026-10-05')
    expect(weekStart('2026-11-01')).toBe('2026-10-26')
  })

  it('builds the grid of a month that starts on a Monday', () => {
    const weeks = monthGrid('2026-06-17')
    expect(weeks[0][0]).toBe('2026-06-01')
    expect(weeks).toHaveLength(5)
    expect(weeks[4][6]).toBe('2026-07-05')
  })

  it('builds the grid of a month that starts on a Sunday', () => {
    const weeks = monthGrid('2026-02-10')
    expect(weeks[0][0]).toBe('2026-01-26')
    expect(weeks[0][6]).toBe('2026-02-01')
    expect(weeks).toHaveLength(5)
    expect(weeks[weeks.length - 1][6]).toBe('2026-03-01')
    expect(weeks.every((week) => week.length === 7)).toBe(true)
  })

  it('spans six weeks when the month needs them', () => {
    const weeks = monthGrid('2026-03-15')
    expect(weeks).toHaveLength(6)
    expect(weeks[0][0]).toBe('2026-02-23')
    expect(weeks[5][6]).toBe('2026-04-05')
  })

  it('handles a leap February', () => {
    const leap = monthGrid('2028-02-14')
    expect(leap.flat()).toContain('2028-02-29')
    expect(leap.flat()).not.toContain('2028-02-30')
    const plain = monthGrid('2027-02-14')
    expect(plain.flat()).toContain('2027-02-28')
    expect(plain.flat()).not.toContain('2027-02-29')
  })

  it('builds a week across months and years', () => {
    expect(weekDays('2026-12-30')).toEqual([
      '2026-12-28',
      '2026-12-29',
      '2026-12-30',
      '2026-12-31',
      '2027-01-01',
      '2027-01-02',
      '2027-01-03',
    ])
    expect(weekDays('2026-10-31')[0]).toBe('2026-10-26')
    expect(weekDays('2026-10-31')[6]).toBe('2026-11-01')
  })

  it('reports the visible range of each view', () => {
    expect(visibleRange('month', '2026-10-05')).toEqual({ from: '2026-09-28', to: '2026-11-01' })
    expect(visibleRange('week', '2026-10-05')).toEqual({ from: '2026-10-05', to: '2026-10-11' })
  })

  it('shifts a week by seven days', () => {
    expect(shift('week', '2026-12-30', 1)).toBe('2027-01-06')
    expect(shift('week', '2026-01-03', -1)).toBe('2025-12-27')
  })

  it('shifts a month keeping the day, clamped to the length of the target month', () => {
    expect(shift('month', '2026-10-05', 1)).toBe('2026-11-05')
    expect(shift('month', '2026-12-15', 1)).toBe('2027-01-15')
    expect(shift('month', '2026-01-31', 1)).toBe('2026-02-28')
    expect(shift('month', '2028-03-31', -1)).toBe('2028-02-29')
    expect(shift('month', '2026-01-10', -1)).toBe('2025-12-10')
  })

  it('formats the period title and day labels', () => {
    expect(periodTitle('month', '2026-10-05')).toBe('October 2026')
    expect(periodTitle('week', '2026-10-05')).toBe('5 Oct – 11 Oct 2026')
    expect(periodTitle('week', '2026-12-30')).toBe('28 Dec – 3 Jan 2027')
    expect(dayLabel('2026-10-05')).toBe('Monday 5 October 2026')
  })

  it('reads today from the local date of the browser', () => {
    expect(todayIso(new Date(2026, 9, 5, 23, 59))).toBe('2026-10-05')
    expect(todayIso(new Date(2026, 0, 1, 0, 0))).toBe('2026-01-01')
  })
})
