import { useTranslation } from 'react-i18next'
import { SORTS } from './calendarEvents.js'

const SOURCES = ['RFETM', 'FCTT', 'BCNESA']

function CalendarFilters({
  filters, options, loadingOptions = false, optionalScope = true, onChange,
}) {
  const { t } = useTranslation()
  const groups = options.groups.filter((group) => group != null)
  const namesRepeat = !filters.competition

  return (
    <article className="card match-filter-card">
      <h2>{t('calendarPage.filters')}</h2>
      <div className="match-filter-grid">
        <label className="match-filter-field">
          {t('calendarPage.source')}
          <select value={filters.source} onChange={(event) => onChange('source', event.target.value)}>
            <option value="">{t('calendarPage.allSources')}</option>
            {SOURCES.map((source) => <option key={source} value={source}>{source}</option>)}
          </select>
        </label>
        <label className="match-filter-field">
          {t('calendarPage.season')}
          <select
            value={filters.season}
            disabled={!filters.source || loadingOptions}
            onChange={(event) => onChange('season', event.target.value)}
          >
            <option value="">{t('calendarPage.select')}</option>
            {options.seasons.map((season) => <option key={season} value={season}>{season}</option>)}
          </select>
        </label>
        <label className="match-filter-field">
          {t('calendarPage.competition')}
          <select
            value={filters.competition}
            disabled={!filters.season || loadingOptions}
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
            disabled={!filters.season}
            onChange={(event) => onChange('team', event.target.value)}
          >
            <option value="">{t('calendarPage.allTeams')}</option>
            {options.teams.map((team) => (
              <option key={team.teamId} value={team.teamId}>
                {namesRepeat && team.competition ? `${team.name} · ${team.competition}` : team.name}
              </option>
            ))}
          </select>
        </label>
        <label className="match-filter-field">
          {t('calendarPage.sort')}
          <select value={filters.sort} onChange={(event) => onChange('sort', event.target.value)}>
            {SORTS.map((sort) => <option key={sort} value={sort}>{t(`calendarPage.sortBy.${sort}`)}</option>)}
          </select>
        </label>
      </div>
    </article>
  )
}

export default CalendarFilters
