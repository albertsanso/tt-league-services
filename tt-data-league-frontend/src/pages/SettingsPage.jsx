import Badge from '../components/ui/Badge.jsx'
import SectionLabel from '../components/ui/SectionLabel.jsx'

function SettingsPage() {
  return (
    <section className="page-block">
      <h1 className="page-title">Configuració</h1>
      <p className="page-description">
        Ajustos generals de la plataforma i preferències de visualització.
      </p>

      <SectionLabel>Preferències</SectionLabel>
      <article className="placeholder-panel card">
        <h2>Notificacions</h2>
        <p>Gestiona els avisos de noves dades i canvis de temporada.</p>
      </article>

      <article className="placeholder-panel card">
        <h2>Integracions</h2>
        <p>
          El mòdul d&apos;analítica avançada encara no està disponible.{' '}
          <Badge tone="warning">Aviat</Badge>
        </p>
      </article>
    </section>
  )
}

export default SettingsPage
