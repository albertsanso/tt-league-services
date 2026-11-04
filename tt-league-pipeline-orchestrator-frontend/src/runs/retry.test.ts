import { unitRetryBlockedLabel } from './retry'

describe('unitRetryBlockedLabel', () => {
  it('words the codes of the server decision', () => {
    expect(unitRetryBlockedLabel('RUN_ACTIVE')).toMatch(/has not finished/)
    expect(unitRetryBlockedLabel('UNIT_NOT_RETRYABLE')).toMatch(/failed or skipped/)
  })

  it('falls back to a generic text for an unknown or missing code', () => {
    expect(unitRetryBlockedLabel('SOMETHING_NEW')).toBe('This unit cannot be retried')
    expect(unitRetryBlockedLabel(null)).toBe('This unit cannot be retried')
  })
})
