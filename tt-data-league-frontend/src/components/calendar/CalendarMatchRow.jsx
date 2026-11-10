import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import StateIcon from './StateIcon.jsx'
import { routePaths } from '../../config/routes.js'
import { formatMatchDateTime, matchTitle, STATE_TONES } from './calendarEvents.js'

function CalendarMatchRow({
  match, returnSearch, canWrite, busyId, focused, onToggleMark,
}) {
  const { t, i18n } = useTranslation()
  const linkRef = useRef(null)
  const title = matchTitle(match)
  const manual = match.overdueMarked
  // The backend opens marking the day after the match date; an existing mark can always be cleared.
  const markable = canWrite && match.status === 'SCHEDULED' && (manual || match.overdueMarkable)

  useEffect(() => {
    if (focused) linkRef.current?.focus()
  }, [focused])

  return (
    <li className="club-result card calendar-detail-row">
      <Link ref={linkRef} className="club-result-link" to={routePaths.matchSummary(match.id, returnSearch)}>
        <span className="calendar-match-row">
          <strong>{title}</strong>
          <span className="club-source">
            {match.dateTime ? formatMatchDateTime(match.dateTime, i18n.language) : t('calendarPage.undated')}
            {match.venue ? ` · ${match.venue}` : ''}
            {match.city ? ` (${match.city})` : ''}
          </span>
          <span className="club-source">
            {[match.competition, match.groupNumber != null
              ? t('calendarPage.groupLabel', { group: match.groupNumber })
              : null].filter(Boolean).join(' · ')}
          </span>
          {match.status === 'PLAYED' ? (
            <span className="club-source">{match.homeGamesWon ?? '—'}–{match.awayGamesWon ?? '—'}</span>
          ) : null}
        </span>
      </Link>
      {match.source ? <span className="cal-source-tag">{match.source}</span> : null}
      <Badge tone={STATE_TONES[match.calendarState] ?? 'subtle'}>
        <StateIcon state={match.calendarState} size={12} />
        {t(`calendarPage.states.${match.calendarState}`)}
      </Badge>
      {manual ? (
        <span className="club-source">
          {t('calendarPage.manualOverdue')}
          {match.overdueMarkedBy
            ? ` ${t('calendarPage.markedBy', {
              user: match.overdueMarkedBy,
              date: match.overdueMarkedAt ? formatMatchDateTime(match.overdueMarkedAt, i18n.language) : '',
            })}`
            : ''}
        </span>
      ) : null}
      {markable ? (
        <Button
          variant="secondary"
          onClick={() => onToggleMark(match)}
          disabled={busyId === match.id}
          aria-label={`${manual ? t('calendarPage.clearAction') : t('calendarPage.markAction')} ${title}`}
        >
          {busyId === match.id
            ? t('calendarPage.markBusy')
            : manual ? t('calendarPage.clearAction') : t('calendarPage.markAction')}
        </Button>
      ) : null}
    </li>
  )
}

export default CalendarMatchRow
