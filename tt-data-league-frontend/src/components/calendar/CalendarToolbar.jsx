import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import Button from '../ui/Button.jsx'
import { VIEWS } from './calendarRange.js'

function CalendarToolbar({
  view, title, onViewChange, onPrevious, onNext, onToday, rounds = [], round = null, onRoundChange,
}) {
  const { t } = useTranslation()
  const jornada = view === 'jornada'
  const roundIndex = rounds.indexOf(round)

  return (
    <div className="calendar-toolbar">
      <div className="calendar-toolbar-nav">
        {jornada ? (
          <>
            <Button
              variant="secondary"
              aria-label={t('calendarPage.previousRound')}
              disabled={roundIndex <= 0}
              onClick={() => onRoundChange(rounds[roundIndex - 1])}
            >
              <ChevronLeft aria-hidden="true" size={16} />
            </Button>
            <label className="calendar-round-select">
              <span>{t('calendarPage.round')}</span>
              <select
                value={round ?? ''}
                onChange={(event) => onRoundChange(Number(event.target.value))}
              >
                {rounds.map((value) => (
                  <option key={value} value={value}>{t('calendarPage.roundLabel', { round: value })}</option>
                ))}
              </select>
            </label>
            <Button
              variant="secondary"
              aria-label={t('calendarPage.nextRound')}
              disabled={roundIndex < 0 || roundIndex >= rounds.length - 1}
              onClick={() => onRoundChange(rounds[roundIndex + 1])}
            >
              <ChevronRight aria-hidden="true" size={16} />
            </Button>
          </>
        ) : (
          <>
            <Button variant="secondary" aria-label={t('calendarPage.previous')} onClick={onPrevious}>
              <ChevronLeft aria-hidden="true" size={16} />
            </Button>
            <Button variant="secondary" onClick={onToday}>{t('calendarPage.today')}</Button>
            <Button variant="secondary" aria-label={t('calendarPage.next')} onClick={onNext}>
              <ChevronRight aria-hidden="true" size={16} />
            </Button>
          </>
        )}
      </div>
      <h2 className="calendar-toolbar-title" aria-live="polite">
        {jornada && round != null ? t('calendarPage.roundLabel', { round }) : title}
      </h2>
      <div className="calendar-toolbar-views" role="group" aria-label={t('calendarPage.views')}>
        {VIEWS.map((value) => (
          <Button
            key={value}
            variant={value === view ? 'primary' : 'secondary'}
            aria-pressed={value === view}
            onClick={() => onViewChange(value)}
          >
            {t(`calendarPage.view.${value}`)}
          </Button>
        ))}
      </div>
    </div>
  )
}

export default CalendarToolbar
