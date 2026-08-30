import { useTranslation } from 'react-i18next'

function AuthField({ id, label, type = 'text', value, onChange, autoComplete, required = true }) {
  const { t } = useTranslation()
  return (
    <label className="auth-field" htmlFor={id}>
      <span>{label.includes('.') ? t(label) : label}</span>
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
