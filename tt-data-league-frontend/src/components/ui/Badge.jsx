function Badge({ children, tone = 'subtle' }) {
  return <span className={`badge ${tone}`}>{children}</span>
}

export default Badge
