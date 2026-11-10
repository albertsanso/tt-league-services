import { useTranslation } from 'react-i18next'
import StateIcon from './StateIcon.jsx'
import { formatMatchTime, matchTitle } from './calendarEvents.js'

/**
 * Content of a FullCalendar list entry. FullCalendar's list rows are not focusable, so the content is a
 * native button: a click, Enter or Space bubbles to the row and reaches FullCalendar's `eventClick`.
 */
function CalendarEventContent({ match }) {
  const { t, i18n } = useTranslation()
  const state = t(`calendarPage.states.${match.calendarState}`)
  const time = match.dateTime ? formatMatchTime(match.dateTime, i18n.language) : ''
  const label = [matchTitle(match), time, match.source, state].filter(Boolean).join(', ')

  return (
    <button type="button" className="cal-entry-list" aria-label={label}>
      <span className="cal-entry-teams">{match.homeTeamName ?? '—'} – {match.awayTeamName ?? '—'}</span>
      {match.source ? <span className="cal-source-tag">{match.source}</span> : null}
      <span className="cal-entry-state">
        <StateIcon state={match.calendarState} />
        {state}
      </span>
    </button>
  )
}

export default CalendarEventContent
