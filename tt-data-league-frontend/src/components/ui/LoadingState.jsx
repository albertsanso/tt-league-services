function LoadingState({ children }) {
  return <p className="ui-state card" role="status" aria-live="polite">{children}</p>
}

export default LoadingState
