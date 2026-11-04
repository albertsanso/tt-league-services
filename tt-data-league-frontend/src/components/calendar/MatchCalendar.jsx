import { useEffect, useMemo, useRef, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import listPlugin from '@fullcalendar/list'
import interactionPlugin from '@fullcalendar/interaction'
import caLocale from '@fullcalendar/core/locales/ca'
import esLocale from '@fullcalendar/core/locales/es'
import enGbLocale from '@fullcalendar/core/locales/en-gb'
import { useTranslation } from 'react-i18next'
import { fullCalendarView, matchTitle } from './calendarEvents.js'
import { toIsoDate } from './calendarRange.js'
import { addDays, parseISO } from 'date-fns'

const PLUGINS = [dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin]
const LOCALES = [caLocale, esLocale, enGbLocale]
const LOCALE_CODES = { ca: 'ca', es: 'es', en: 'en-gb' }
const TIME_FORMAT = { hour: '2-digit', minute: '2-digit', hour12: false }

function exclusiveEnd(lastDate) {
  return toIsoDate(addDays(parseISO(lastDate), 1))
}

function MatchCalendar({
  view, date, events, visibleRange = null, compact = false, onDateSelect, onMatchSelect,
}) {
  const { t, i18n } = useTranslation()
  const calendarRef = useRef(null)
  const fcView = fullCalendarView(view, compact)
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

  const eventDidMount = (info) => {
    const match = info.event.extendedProps.match
    if (!match) return
    const state = t(`calendarPage.states.${match.calendarState}`)
    info.el.setAttribute('aria-label', `${matchTitle(match)}, ${info.timeText || ''} ${state}`.replace(/\s+/g, ' '))
  }

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
        slotLabelFormat={TIME_FORMAT}
        slotMinTime="08:00:00"
        slotMaxTime="23:00:00"
        allDaySlot={false}
        fixedWeekCount={false}
        dayMaxEvents={3}
        noEventsContent={() => t('calendarPage.empty')}
        eventDidMount={eventDidMount}
        eventClick={(info) => {
          info.jsEvent.preventDefault()
          onMatchSelect?.(info.event.extendedProps.match)
        }}
        dateClick={(info) => onDateSelect?.(info.dateStr.slice(0, 10))}
        moreLinkClick={(info) => {
          onDateSelect?.(toIsoDate(info.date))
        }}
      />
    </div>
  )
}

export default MatchCalendar
