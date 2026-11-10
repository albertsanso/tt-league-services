import { useTranslation } from 'react-i18next'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import StateIcon from './StateIcon.jsx'
import { formatMatchDateTime, STATE_TONES } from './calendarEvents.js'

function Field({ label, children }) {
  if (children === null || children === undefined || children === '') return null
  return (
    <div className="calendar-dialog-field">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

/** The fields, the manual overdue note and the mark action of a calendar match. */
function MatchDialogBody({
  match, title, canWrite, busyId, onToggleMark,
}) {
  const { t, i18n } = useTranslation()
  const manual = match.overdueMarked
  // The backend opens marking the day after the match date; an existing mark can always be cleared.
  const markable = canWrite && match.status === 'SCHEDULED' && (manual || match.overdueMarkable)
  const place = [match.venue, match.city ? `(${match.city})` : null].filter(Boolean).join(' ')

  return (
    <>
      <div className="calendar-dialog-badges">
        {match.source ? <span className="cal-source-tag">{match.source}</span> : null}
        <Badge tone={STATE_TONES[match.calendarState] ?? 'subtle'}>
          <StateIcon state={match.calendarState} size={12} />
          {t(`calendarPage.states.${match.calendarState}`)}
        </Badge>
      </div>
      <dl className="calendar-dialog-fields">
        <Field label={t('calendarPage.dialog.when')}>
          {match.dateTime ? formatMatchDateTime(match.dateTime, i18n.language) : t('calendarPage.undated')}
        </Field>
        <Field label={t('calendarPage.dialog.venue')}>{place}</Field>
        <Field label={t('calendarPage.dialog.competition')}>{match.competition}</Field>
        <Field label={t('calendarPage.dialog.group')}>
          {match.groupNumber != null ? t('calendarPage.groupLabel', { group: match.groupNumber }) : null}
        </Field>
        <Field label={t('calendarPage.dialog.phase')}>{match.phase}</Field>
        <Field label={t('calendarPage.dialog.round')}>{match.round}</Field>
        {match.status === 'PLAYED' ? (
          <Field label={t('calendarPage.dialog.result')}>
            {match.homeGamesWon ?? '—'}–{match.awayGamesWon ?? '—'}
          </Field>
        ) : null}
      </dl>
      {manual ? (
        <p className="calendar-dialog-note">
          {t('calendarPage.manualOverdue')}
          {match.overdueMarkedBy
            ? ` ${t('calendarPage.markedBy', {
              user: match.overdueMarkedBy,
              date: match.overdueMarkedAt ? formatMatchDateTime(match.overdueMarkedAt, i18n.language) : '',
            })}`
            : ''}
        </p>
      ) : null}
      {markable ? (
        <Button
          variant="secondary"
          onClick={() => onToggleMark?.(match)}
          disabled={busyId === match.id}
          aria-label={`${manual ? t('calendarPage.clearAction') : t('calendarPage.markAction')} ${title}`}
        >
          {busyId === match.id
            ? t('calendarPage.markBusy')
            : manual ? t('calendarPage.clearAction') : t('calendarPage.markAction')}
        </Button>
      ) : null}
    </>
  )
}

export default MatchDialogBody
