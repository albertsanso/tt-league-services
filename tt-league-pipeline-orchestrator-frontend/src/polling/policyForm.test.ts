import { makePolicy } from '../test/pollingFixtures'
import { toFormValues, toRequest, validatePolicyForm } from './policyForm'
import type { PolicyFormValues } from './policyForm'

const defaults = toFormValues(makePolicy('FCTT'))

function with_(patch: Partial<PolicyFormValues>): PolicyFormValues {
  return { ...defaults, ...patch }
}

describe('validatePolicyForm', () => {
  it('accepts the documented defaults', () => {
    expect(validatePolicyForm(defaults)).toEqual({})
  })

  it('accepts equal intervals along the chain', () => {
    const same = with_({ matchDay: 'PT2H', dayAfter: 'PT2H', daysTwoToSeven: 'PT2H', open: 'PT2H', overdue: 'PT2H', fullRefresh: 'PT2H' })
    expect(validatePolicyForm(same)).toEqual({})
  })

  it.each(['matchDay', 'matchDayStartOffset', 'dayAfter', 'daysTwoToSeven', 'open', 'overdue', 'fullRefresh'] as const)(
    'rejects a %s that is not a duration or not positive',
    (field) => {
      expect(validatePolicyForm(with_({ [field]: 'soon' }))[field]).toContain('ISO-8601')
      expect(validatePolicyForm(with_({ [field]: 'PT0S' }))[field]).toContain('positive')
    },
  )

  it.each([
    ['matchDay', 'PT4H'],
    ['dayAfter', 'PT13H'],
    ['daysTwoToSeven', 'PT25H'],
    ['open', 'PT169H'],
  ] as const)('rejects %s of %s above the next interval of the chain', (field, value) => {
    expect(validatePolicyForm(with_({ [field]: value }))[field]).toContain('Must not exceed')
  })

  it('rejects an overdue interval above the full refresh', () => {
    expect(validatePolicyForm(with_({ overdue: 'P8D' })).overdue).toContain('full refresh')
  })

  it.each(['overdueStopAfterDays', 'noChangeThreshold', 'recentMatchDays'] as const)(
    'needs %s to be a whole number of at least 1',
    (field) => {
      expect(validatePolicyForm(with_({ [field]: '0' }))[field]).toContain('at least 1')
      expect(validatePolicyForm(with_({ [field]: '-2' }))[field]).toContain('at least 1')
      expect(validatePolicyForm(with_({ [field]: '1.5' }))[field]).toContain('at least 1')
      expect(validatePolicyForm(with_({ [field]: '' }))[field]).toContain('at least 1')
      expect(validatePolicyForm(with_({ [field]: '1' }))[field]).toBeUndefined()
    },
  )
})

describe('toRequest', () => {
  it('sends all ten settings and the loaded version', () => {
    expect(toRequest(with_({ recentMatchDays: ' 2 ', matchDay: ' PT1H ' }), 4)).toEqual({
      matchDay: 'PT1H',
      matchDayStartOffset: 'PT2H',
      dayAfter: 'PT3H',
      daysTwoToSeven: 'PT12H',
      open: 'PT24H',
      overdue: 'PT24H',
      overdueStopAfterDays: 21,
      fullRefresh: 'PT168H',
      noChangeThreshold: 3,
      recentMatchDays: 2,
      version: 4,
    })
  })
})
