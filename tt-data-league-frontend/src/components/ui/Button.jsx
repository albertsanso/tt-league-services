function Button({
  children,
  variant = 'primary',
  type = 'button',
  className = '',
  ...props
}) {
  const classes = ['ui-button', `ui-button-${variant}`, className]
    .filter(Boolean)
    .join(' ')

  return (
    <button className={classes} type={type} {...props}>
      {children}
    </button>
  )
}

export default Button
