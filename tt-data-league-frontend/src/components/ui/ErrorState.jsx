function ErrorState({ children, action }) {
  return (
    <div className="ui-state card" role="alert">
      <p>{children}</p>
      {action}
    </div>
  )
}

export default ErrorState
