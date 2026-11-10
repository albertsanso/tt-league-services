import { useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { CALENDAR_SOURCES } from '../../api/calendarSources.js'

const FIELDS_ID = 'calendar-filter-fields'

function CalendarFilters({
  filters, options, loadingOptions = false, optionalScope = true, collapsible = false, activeCount = 0, onChange,
}) {
  const { t } = useTranslation()
  const [opened, setOpened] = useState(false)
  const groups = options.groups.filter((group) => group != null)
  const namesRepeat = !filters.competition
  const needsSource = !optionalScope && !filters.source
  // The jornada view cannot load until a source and a competition are chosen, so the fields stay reachable.
  const forcedOpen = !optionalScope && (!filters.source || !filters.competition)
  const expanded = !collapsible || opened || forcedOpen
  const Chevron = expanded ? ChevronUp : ChevronDown

  return (
    <section className="card match-filter-card calendar-filter-card" aria-label={t('calendarPage.filters')}>
      {collapsible ? (
        <button
          type="button"
          className="calendar-filter-toggle"
          aria-expanded={expanded}
          aria-controls={FIELDS_ID}
          disabled={forcedOpen}
          onClick={() => setOpened((value) => !value)}
        >
          <span className="calendar-filter-toggle-text">
            <strong>{t('calendarPage.showFilters')}</strong>
            <span className="club-source">
              {[filters.source || t('calendarPage.allSources'), filters.season].filter(Boolean).join(' · ')}
            </span>
          </span>
          {activeCount > 0 ? (
            <span className="calendar-filter-count">{t('calendarPage.activeFilters', { count: activeCount })}</span>
          ) : null}
          <Chevron aria-hidden="true" size={16} />
        </button>
      ) : null}
      <div id={FIELDS_ID} className="match-filter-grid calendar-filter-grid" hidden={!expanded}>
        <label className="match-filter-field">
          {t('calendarPage.source')}
          <select value={filters.source} onChange={(event) => onChange('source', event.target.value)}>
            <option value="">{t('calendarPage.allSources')}</option>
            {CALENDAR_SOURCES.map((source) => <option key={source} value={source}>{source}</option>)}
          </select>
        </label>
        <label className="match-filter-field">
          {t('calendarPage.season')}
          <select
            value={filters.season}
            disabled={loadingOptions}
            onChange={(event) => onChange('season', event.target.value)}
          >
            {filters.season ? null : <option value="">{t('calendarPage.select')}</option>}
            {options.seasons.map((season) => <option key={season} value={season}>{season}</option>)}
          </select>
        </label>
        <label className="match-filter-field">
          {t('calendarPage.competition')}
          <select
            value={filters.competition}
            disabled={!filters.season || loadingOptions || needsSource}
            onChange={(event) => onChange('competition', event.target.value)}
          >
            <option value="">{optionalScope ? t('calendarPage.allCompetitions') : t('calendarPage.select')}</option>
            {options.competitions.map((competition) => (
              <option key={competition} value={competition}>{competition}</option>
            ))}
          </select>
        </label>
        <label className="match-filter-field">
          {t('calendarPage.group')}
          <select
            value={filters.group}
            disabled={!filters.competition}
            onChange={(event) => onChange('group', event.target.value)}
          >
            {optionalScope ? <option value="">{t('calendarPage.allGroups')}</option> : null}
            {groups.map((group) => <option key={group} value={group}>{group}</option>)}
          </select>
        </label>
        <label className="match-filter-field">
          {t('calendarPage.team')}
          <select
            value={filters.team}
            disabled={!filters.season || needsSource}
            onChange={(event) => onChange('team', event.target.value)}
          >
            <option value="">{t('calendarPage.allTeams')}</option>
            {options.teams.map((team) => (
              <option key={`${team.source ?? ''}|${team.teamId}`} value={team.teamId}>
                {[team.name, namesRepeat ? team.competition : null, team.source]
                  .filter(Boolean).join(' · ')}
              </option>
            ))}
          </select>
        </label>
      </div>
    </section>
  )
}

export default CalendarFilters
