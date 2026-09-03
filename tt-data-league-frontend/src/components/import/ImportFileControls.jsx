import Button from '../ui/Button.jsx'
import Input from '../ui/Input.jsx'
import { useTranslation } from 'react-i18next'

export default function ImportFileControls({ file, onFileChange, onLoad, disabled, uploadState = { status: 'idle', progress: 0 } }) {
  const { t } = useTranslation()
  const uploading = uploadState.status === 'uploading'
  const failed = uploadState.status === 'error'
  const statusId = 'import-upload-status'
  const buttonLabel = failed ? t('importPanel.retryUpload') : t('importPanel.load')

  return <div className="import-file-controls">
    <label htmlFor="import-file">{t('importPanel.fileChooser')}</label>
    <Input
      id="import-file"
      type="file"
      accept=".zip,application/zip,application/x-zip-compressed"
      aria-describedby={statusId}
      disabled={disabled || uploading}
      onChange={(event) => onFileChange(event.target.files?.[0] ?? null)}
    />
    {file && <span className="import-file-name">{file.name}</span>}
    <Button variant="primary" onClick={onLoad} disabled={disabled || !file || uploading}>{buttonLabel}</Button>
    {uploading && <div className="import-upload-progress">
      <progress max="100" value={uploadState.progress} aria-label={t('importPanel.uploadProgress')} />
      <span>{t('importPanel.uploadProgressValue', { progress: uploadState.progress })}</span>
    </div>}
    {uploadState.status === 'success' && <p id={statusId} className="import-upload-status" role="status">{t('importPanel.uploadSuccess')}</p>}
    {uploadState.status === 'error' && <p id={statusId} className="import-upload-status import-upload-status--error" role="alert">
      {uploadState.error?.status === 401
        ? t('importPanel.unauthorized')
        : uploadState.error?.status === 403
          ? t('importPanel.forbidden')
          : uploadState.error?.validation
            ? t('importPanel.invalidFile')
            : t('importPanel.uploadError')}
    </p>}
    {uploading && <p id={statusId} className="sr-only" role="status">{t('importPanel.uploading')}</p>}
  </div>
}
