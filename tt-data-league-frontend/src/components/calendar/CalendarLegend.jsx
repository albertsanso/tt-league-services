import { useTranslation } from 'react-i18next'
import StateIcon from './StateIcon.jsx'
import { STATE_ORDER } from './calendarEvents.js'

function CalendarLegend({ overdueGraceDays = null }) {
  const { t } = useTranslation()

  return (
    <section className="calendar-legend" aria-label={t('calendarPage.legendTitle')}>
      <ul className="calendar-legend-list">
        {STATE_ORDER.map((state) => (
          <li key={state} className="calendar-legend-item">
            <StateIcon state={state} />
            <span>{t(`calendarPage.states.${state}`)}</span>
          </li>
        ))}
      </ul>
      {overdueGraceDays != null ? (
        <p className="calendar-legend-note">{t('calendarPage.legend', { days: overdueGraceDays })}</p>
      ) : null}
    </section>
  )
}

export default CalendarLegend
