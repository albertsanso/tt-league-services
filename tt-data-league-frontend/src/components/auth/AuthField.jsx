function AuthField({ id, label, type = 'text', value, onChange, autoComplete, required = true }) {
  return (
    <label className="auth-field" htmlFor={id}>
      <span>{label}</span>
      <input
        id={id}
        name={id}
        type={type}
        value={value}
        onChange={onChange}
        autoComplete={autoComplete}
        required={required}
      />
    </label>
  )
}

export default AuthField
