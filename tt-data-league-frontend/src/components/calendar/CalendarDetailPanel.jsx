import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import { routePaths } from '../../config/routes.js'
import { matchTitle, sortMatches, STATE_TONES } from './calendarEvents.js'

function formatDateTime(value) {
  return new Date(value).toLocaleString(undefined, { timeZone: 'Europe/Madrid' })
}

function MatchRow({
  match, returnSearch, canWrite, busyId, focused, onToggleMark,
}) {
  const { t } = useTranslation()
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
            {match.dateTime ? formatDateTime(match.dateTime) : t('calendarPage.undated')}
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
      <Badge tone={STATE_TONES[match.calendarState] ?? 'subtle'}>
        {t(`calendarPage.states.${match.calendarState}`)}
      </Badge>
      {manual ? (
        <span className="club-source">
          {t('calendarPage.manualOverdue')}
          {match.overdueMarkedBy
            ? ` ${t('calendarPage.markedBy', {
              user: match.overdueMarkedBy,
              date: match.overdueMarkedAt ? formatDateTime(match.overdueMarkedAt) : '',
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

function CalendarDetailPanel({
  heading, matches, sort, returnSearch, canWrite = false, busyId = null, focusMatchId = null, onToggleMark,
}) {
  const { t } = useTranslation()
  const dated = sortMatches(matches.filter((match) => match.dateTime), sort)
  const undated = matches.filter((match) => !match.dateTime)
  const rowProps = { returnSearch, canWrite, busyId, onToggleMark }

  return (
    <section className="calendar-detail" aria-label={t('calendarPage.detail')}>
      <h2>{heading}</h2>
      {matches.length === 0 ? <p role="status">{t('calendarPage.detailEmpty')}</p> : null}
      {dated.length > 0 ? (
        <ul className="club-result-list">
          {dated.map((match) => (
            <MatchRow key={match.id} match={match} focused={focusMatchId === match.id} {...rowProps} />
          ))}
        </ul>
      ) : null}
      {undated.length > 0 ? (
        <>
          <h3>{t('calendarPage.undated')}</h3>
          <ul className="club-result-list">
            {undated.map((match) => (
              <MatchRow key={match.id} match={match} focused={focusMatchId === match.id} {...rowProps} />
            ))}
          </ul>
        </>
      ) : null}
    </section>
  )
}

export default CalendarDetailPanel
