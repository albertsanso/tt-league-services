function Card({ children, as: Element = 'div', className = '', ...props }) {
  const classes = ['card', className].filter(Boolean).join(' ')

  return <Element className={classes} {...props}>{children}</Element>
}

export default Card
