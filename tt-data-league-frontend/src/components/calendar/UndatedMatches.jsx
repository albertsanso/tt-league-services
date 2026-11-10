import { useTranslation } from 'react-i18next'
import StateIcon from './StateIcon.jsx'
import { matchTitle } from './calendarEvents.js'

/** Matches without a date cannot sit on any day of the calendar; they are listed here and open the match dialog. */
function UndatedMatches({ matches, onMatchSelect }) {
  const { t } = useTranslation()
  if (matches.length === 0) return null

  return (
    <section className="calendar-undated" aria-labelledby="calendar-undated-title">
      <h2 id="calendar-undated-title">
        {t('calendarPage.undated')}
        <span className="calendar-undated-count">{t('calendarPage.matchCount', { count: matches.length })}</span>
      </h2>
      <ul className="calendar-undated-list">
        {matches.map((match) => {
          const state = t(`calendarPage.states.${match.calendarState}`)
          return (
            <li key={match.id}>
              <button
                type="button"
                className={`week-entry cal-state-${String(match.calendarState ?? '').toLowerCase()}`}
                aria-label={[matchTitle(match), match.source, match.competition, state].filter(Boolean).join(', ')}
                onClick={() => onMatchSelect?.(match)}
              >
                <span className="week-entry-time"><StateIcon state={match.calendarState} />{state}</span>
                <span className="week-entry-team">{match.homeTeamName ?? '—'}</span>
                <span className="week-entry-team">{match.awayTeamName ?? '—'}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

export default UndatedMatches
