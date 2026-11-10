/** Text of a match count and its count per state, for the accessible names of calendar cells and groups. */
export function stateSummary(t, total, states) {
  return [
    t('calendarPage.matchCount', { count: total }),
    ...states.map((entry) => `${t(`calendarPage.states.${entry.state}`)} ${entry.count}`),
  ].join(', ')
}
