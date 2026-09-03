import Button from '../ui/Button.jsx'
import Input from '../ui/Input.jsx'
import { useTranslation } from 'react-i18next'

export default function ImportFileControls({ file, onFileChange, onLoad, disabled }) {
  const { t } = useTranslation()
  return <div className="import-file-controls">
    <label htmlFor="import-file">{t('importPanel.fileChooser')}</label>
    <Input id="import-file" type="file" onChange={(event) => onFileChange(event.target.files?.[0] ?? null)} />
    {file && <span className="import-file-name">{file.name}</span>}
    <Button variant="primary" onClick={onLoad} disabled={disabled || !file}>{t('importPanel.load')}</Button>
  </div>
}
