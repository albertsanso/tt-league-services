function SectionLabel({ children }) {
  return (
    <p className="section-label">
      <span>{children}</span>
      <span className="section-label-line" aria-hidden="true" />
    </p>
  )
}

export default SectionLabel
