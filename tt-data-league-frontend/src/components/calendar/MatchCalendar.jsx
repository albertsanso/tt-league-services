import { useEffect, useMemo, useRef, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import listPlugin from '@fullcalendar/list'
import caLocale from '@fullcalendar/core/locales/ca'
import esLocale from '@fullcalendar/core/locales/es'
import enGbLocale from '@fullcalendar/core/locales/en-gb'
import { useTranslation } from 'react-i18next'
import CalendarEventContent from './CalendarEventContent.jsx'
import { fullCalendarView } from './calendarEvents.js'
import { toIsoDate } from './calendarRange.js'
import { addDays, parseISO } from 'date-fns'

const PLUGINS = [listPlugin]
const LOCALES = [caLocale, esLocale, enGbLocale]
const LOCALE_CODES = { ca: 'ca', es: 'es', en: 'en-gb' }
const TIME_FORMAT = { hour: '2-digit', minute: '2-digit', hour12: false }

function exclusiveEnd(lastDate) {
  return toIsoDate(addDays(parseISO(lastDate), 1))
}

function MatchCalendar({
  view, date, events, visibleRange = null, onMatchSelect,
}) {
  const { t, i18n } = useTranslation()
  const calendarRef = useRef(null)
  const fcView = fullCalendarView(view)
  const locale = LOCALE_CODES[String(i18n.language ?? '').slice(0, 2)] ?? 'ca'
  const range = useMemo(
    () => (view === 'jornada' && visibleRange
      ? { start: visibleRange.firstDate, end: exclusiveEnd(visibleRange.lastDate) }
      : undefined),
    [view, visibleRange],
  )
  const [initial] = useState(() => ({ fcView, date }))
  const views = useMemo(
    () => ({ listJornada: { type: 'list', visibleRange: range ?? { start: date, end: date } } }),
    [range, date],
  )

  useEffect(() => {
    const api = calendarRef.current?.getApi()
    if (!api) return
    if (api.view.type !== fcView) api.changeView(fcView)
    if (view !== 'jornada') api.gotoDate(date)
  }, [fcView, view, date])

  return (
    <div className="match-calendar" data-view={fcView}>
      <FullCalendar
        ref={calendarRef}
        plugins={PLUGINS}
        locales={LOCALES}
        locale={locale}
        initialView={initial.fcView}
        initialDate={initial.date}
        views={views}
        headerToolbar={false}
        firstDay={1}
        timeZone="Europe/Madrid"
        height="auto"
        events={events}
        eventTimeFormat={TIME_FORMAT}
        eventContent={(arg) => (
          <CalendarEventContent match={arg.event.extendedProps.match} />
        )}
        noEventsContent={() => t('calendarPage.empty')}
        eventClick={(info) => {
          info.jsEvent.preventDefault()
          onMatchSelect?.(info.event.extendedProps.match)
        }}
      />
    </div>
  )
}

export default MatchCalendar
