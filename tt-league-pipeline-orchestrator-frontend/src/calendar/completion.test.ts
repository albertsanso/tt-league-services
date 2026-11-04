import { makeSummary, summariesForEveryCompletion } from '../test/matchDayFixtures'
import { COMPLETION_LABELS, COMPLETION_ORDER, completionColor, entryDescription, entryLabel, progressText, suffixText } from './completion'

describe('completion', () => {
  it('maps every completion category to a colour and a label', () => {
    expect(COMPLETION_ORDER.map((completion) => [completion, completionColor(completion), COMPLETION_LABELS[completion]])).toEqual([
      ['COMPLETE', 'success', 'All reported'],
      ['IN_PROGRESS', 'info', 'In progress'],
      ['HAS_OVERDUE', 'error', 'Has overdue'],
      ['FUTURE', 'default', 'Future'],
    ])
  })

  it('writes the full label with every part', () => {
    expect(entryLabel(makeSummary('a'))).toBe('TERCERA-masculino · G2 · 1a Fase · J3')
  })

  it('leaves out a missing group or phase', () => {
    expect(entryLabel(makeSummary('a', { groupNumber: null, phase: null }))).toBe('TERCERA-masculino · J3')
    expect(entryLabel(makeSummary('a', { groupNumber: 1, phase: null }))).toBe('TERCERA-masculino · G1 · J3')
    expect(entryLabel(makeSummary('a', { groupNumber: null, phase: 'Fase 2' }))).toBe('TERCERA-masculino · Fase 2 · J3')
  })

  it('writes the progress as reported over active matches', () => {
    expect(progressText(makeSummary('a', { reportedMatches: 4, totalMatches: 6 }))).toBe('4 / 6')
    expect(progressText(makeSummary('a', { reportedMatches: 0, totalMatches: 0 }))).toBe('0 / 0')
  })

  it('writes the postponed and ignored suffixes', () => {
    const [, , overdue] = summariesForEveryCompletion()
    expect(suffixText(overdue)).toBe('1 postponed, 1 ignored')
    expect(suffixText(makeSummary('a'))).toBe('')
    expect(suffixText(makeSummary('a', { ignoredMatches: 2 }))).toBe('2 ignored')
  })

  it('describes an entry with completion, state and counts for assistive technology', () => {
    const [complete, , overdue] = summariesForEveryCompletion()
    expect(entryDescription(complete)).toBe('TERCERA-masculino · G2 · 1a Fase · J1, All reported, closed, 6 / 6 reported')
    expect(entryDescription(overdue)).toBe(
      'TERCERA-masculino · G2 · 1a Fase · J3, Has overdue, open, 3 / 5 reported, 1 postponed, 1 ignored',
    )
  })

  it('takes the category from the server and never derives it from the counts', () => {
    const odd = makeSummary('a', { completion: 'FUTURE', reportedMatches: 6, totalMatches: 6 })
    expect(COMPLETION_LABELS[odd.completion]).toBe('Future')
  })
})
