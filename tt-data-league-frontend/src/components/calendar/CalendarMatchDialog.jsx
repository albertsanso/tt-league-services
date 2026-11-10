import { useEffect, useId, useRef } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import MatchDialogBody from './MatchDialogBody.jsx'
import { matchTitle } from './calendarEvents.js'
import { routePaths } from '../../config/routes.js'

const FOCUSABLE = 'a[href], button:not([disabled]), select, input, textarea, [tabindex]:not([tabindex="-1"])'

/**
 * The details of one calendar match in a modal dialog. It shows what the calendar response already carries.
 * Focus moves in on open, stays inside while open and returns to the control that opened it on close.
 */
function CalendarMatchDialog({
  match, returnSearch, canWrite = false, busyId = null, onToggleMark, onClose,
}) {
  const { t } = useTranslation()
  const dialogRef = useRef(null)
  const closeRef = useRef(onClose)
  const titleId = useId()
  const title = matchTitle(match)

  useEffect(() => {
    closeRef.current = onClose
  })

  useEffect(() => {
    const dialog = dialogRef.current
    const opener = document.activeElement
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog?.focus()

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        closeRef.current?.()
        return
      }
      if (event.key !== 'Tab' || !dialog) return
      const items = [...dialog.querySelectorAll(FOCUSABLE)]
      if (items.length === 0) {
        event.preventDefault()
        dialog.focus()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement
      if (!dialog.contains(active)) {
        event.preventDefault()
        first.focus()
      } else if (event.shiftKey && (active === first || active === dialog)) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = overflow
      if (opener?.isConnected) opener.focus()
    }
  }, [])

  return (
    <div className="acta-dialog-overlay calendar-dialog-overlay" role="presentation" onMouseDown={() => onClose?.()}>
      <div
        className="acta-dialog card calendar-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        ref={dialogRef}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button type="button" className="icon-button calendar-dialog-close" aria-label={t('common.close')} onClick={() => onClose?.()}>
          &times;
        </button>
        <h2 id={titleId} className="calendar-dialog-title">{title}</h2>
        <MatchDialogBody
          match={match}
          title={title}
          canWrite={canWrite}
          busyId={busyId}
          onToggleMark={onToggleMark}
        />
        <Link className="ui-button ui-button-primary calendar-dialog-link" to={routePaths.matchSummary(match.id, returnSearch)}>
          {t('calendarPage.dialog.summaryLink')}
        </Link>
      </div>
    </div>
  )
}

export default CalendarMatchDialog
