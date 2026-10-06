import { formatIsoDuration, lookbackLabel, modeLabel, parseIsoDuration } from './format'

describe('formatIsoDuration', () => {
  it.each([
    ['PT2H', '2 h'],
    ['P7D', '7 d'],
    ['PT168H', '7 d'],
    ['PT90M', '1 h 30 min'],
    ['PT24H', '1 d'],
    ['PT30S', '30 s'],
    ['P1DT2H3M4S', '1 d 2 h 3 min 4 s'],
    ['PT0S', '0 s'],
  ])('formats %s as %s', (value, expected) => {
    expect(formatIsoDuration(value)).toBe(expected)
  })

  it('shows a dash for a missing value and leaves text that is not a duration alone', () => {
    expect(formatIsoDuration(null)).toBe('—')
    expect(formatIsoDuration(undefined)).toBe('—')
    expect(formatIsoDuration('soon')).toBe('soon')
  })
})

describe('parseIsoDuration', () => {
  it('returns whole seconds', () => {
    expect(parseIsoDuration('PT2H')).toBe(7200)
    expect(parseIsoDuration('P7D')).toBe(604_800)
    expect(parseIsoDuration(' PT90M ')).toBe(5400)
  })

  it.each(['', 'P', 'PT', '2H', 'PT-1H', 'PT1.5H', 'PTH', 'P1W', 'soon'])('rejects %j', (value) => {
    expect(parseIsoDuration(value)).toBeNull()
  })
})

describe('labels', () => {
  it('words the lookback as the server sends it', () => {
    expect(lookbackLabel(3)).toBe('Last 3 match days per group')
    expect(lookbackLabel(1)).toBe('Last match day per group')
  })

  it('words the polling modes', () => {
    expect(modeLabel('ADAPTIVE', null)).toBe('Adaptive')
    expect(modeLabel('CRON', '0 0 3 * * *')).toBe('Cron 0 0 3 * * *')
    expect(modeLabel('NONE', null)).toBe('Not scheduled')
  })
})
