import AccentBar from '../ui/AccentBar.jsx'

function HeroBanner() {
  return (
    <section className="hero-banner card">
      <span className="hero-circle-big" aria-hidden="true" />
      <span className="hero-circle-small" aria-hidden="true" />
      <div className="hero-banner-content">
        <p className="hero-kicker">Benvingut a</p>
        <h1 className="hero-title">TT League</h1>
        <AccentBar />
        <p className="hero-description">
          Plataforma oberta de dades per a la comunitat del tennis de taula. Consulta
          resultats oficials, estadístiques de jugadors i classificacions de les darreres
          temporades.
        </p>
        <p className="hero-note">
          En properes fases incorporarem una capa d&apos;analítica i comparació basada en IA,
          utilitzant els LLM més actuals.
        </p>
      </div>
    </section>
  )
}

export default HeroBanner
