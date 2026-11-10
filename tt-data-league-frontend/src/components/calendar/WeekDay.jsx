import { format, parseISO } from 'date-fns'
import { useTranslation } from 'react-i18next'
import StateIcon from './StateIcon.jsx'
import WeekGroup from './WeekGroup.jsx'
import { formatMatchTime, groupBySourceAndCompetition, matchTitle } from './calendarEvents.js'
import { dateFnsLocale } from './calendarRange.js'
import { stateSummary } from './stateSummary.js'

function MatchEntry({ match, source, competition, onSelect }) {
  const { t, i18n } = useTranslation()
  const time = formatMatchTime(match.dateTime, i18n.language)
  const label = [matchTitle(match), time, source, competition, t(`calendarPage.states.${match.calendarState}`)]
    .filter(Boolean).join(', ')

  return (
    <button
      type="button"
      className={`week-entry cal-state-${String(match.calendarState ?? '').toLowerCase()}`}
      aria-label={label}
      onClick={() => onSelect?.(match)}
    >
      <span className="week-entry-time">
        <StateIcon state={match.calendarState} />
        {time}
      </span>
      <span className="week-entry-team">{match.homeTeamName ?? '—'}</span>
      <span className="week-entry-team">{match.awayTeamName ?? '—'}</span>
    </button>
  )
}

function WeekDay({
  day, index, summary, matches, fallbackSource, isToday, open, onToggle, onDateSelect, onMatchSelect,
}) {
  const { t, i18n } = useTranslation()
  const locale = dateFnsLocale(i18n.language)
  const date = parseISO(day)
  const groups = groupBySourceAndCompetition(matches, fallbackSource)
  const classes = ['week-day', isToday ? 'week-day-today' : ''].filter(Boolean).join(' ')

  return (
    <section className={classes} aria-label={format(date, 'PPPP', { locale })}>
      <button
        type="button"
        className="week-day-head"
        aria-current={isToday ? 'date' : undefined}
        aria-label={`${t('calendarPage.dayCellLabel', {
          date: format(date, 'PPPP', { locale }),
          summary: stateSummary(t, summary.total, summary.states),
        })}. ${t('calendarPage.openDay')}`}
        onClick={() => onDateSelect?.(day)}
      >
        <span className="week-day-name">{format(date, 'EEE d/M', { locale })}</span>
        {summary.total > 0 ? <span className="week-day-total" aria-hidden="true">{summary.total}</span> : null}
      </button>
      {groups.length === 0 ? <p className="week-day-empty">{t('calendarPage.noMatchesDay')}</p> : null}
      {groups.map((group, sourceIndex) => {
        const sourceKey = `${day}|${group.source}`
        return (
          <WeekGroup
            key={sourceKey}
            level="source"
            id={`week-${index}-${sourceIndex}`}
            name={group.source || t('calendarPage.noSource')}
            tag
            total={group.total}
            states={group.states}
            open={open.has(sourceKey)}
            onToggle={() => onToggle(sourceKey)}
          >
            {group.competitions.map((competition, competitionIndex) => {
              const competitionKey = `${sourceKey}|${competition.competition}`
              const name = competition.competition || t('calendarPage.noCompetition')
              return (
                <WeekGroup
                  key={competitionKey}
                  level="competition"
                  id={`week-${index}-${sourceIndex}-${competitionIndex}`}
                  name={name}
                  total={competition.total}
                  states={competition.states}
                  open={open.has(competitionKey)}
                  onToggle={() => onToggle(competitionKey)}
                >
                  {competition.matches.map((match) => (
                    <MatchEntry
                      key={match.id}
                      match={match}
                      source={group.source}
                      competition={competition.competition}
                      onSelect={onMatchSelect}
                    />
                  ))}
                </WeekGroup>
              )
            })}
          </WeekGroup>
        )
      })}
    </section>
  )
}

export default WeekDay
