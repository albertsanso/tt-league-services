import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CalendarMatchDialog from './CalendarMatchDialog.jsx'

afterEach(() => {
  cleanup()
  document.body.style.overflow = ''
})

const match = (overrides = {}) => ({
  id: 'm1',
  source: 'RFETM',
  dateTime: '2026-09-19T16:30:00Z',
  venue: 'Pavelló',
  city: 'Terrassa',
  homeTeamName: 'Club A',
  awayTeamName: 'Club B',
  homeGamesWon: null,
  awayGamesWon: null,
  status: 'SCHEDULED',
  calendarState: 'UPCOMING',
  overdueMarked: false,
  overdueMarkedAt: null,
  overdueMarkedBy: null,
  overdueMarkable: false,
  competition: 'tercera',
  groupNumber: 1,
  phase: '1a Fase',
  round: 3,
  ...overrides,
})

function renderDialog(overrides = {}, props = {}) {
  const onClose = vi.fn()
  const onToggleMark = vi.fn()
  const rendered = render(
    <MemoryRouter>
      <CalendarMatchDialog
        match={match(overrides)}
        returnSearch="source=FCTT&season=2026-2027"
        onToggleMark={onToggleMark}
        onClose={onClose}
        {...props}
      />
    </MemoryRouter>,
  )
  return { ...rendered, onClose, onToggleMark }
}

describe('CalendarMatchDialog', () => {
  it('is a modal dialog named by the teams and shows every field in the app language and Madrid time', () => {
    const { container } = renderDialog()

    const dialog = screen.getByRole('dialog', { name: 'Club A – Club B' })
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(container.querySelector('.cal-source-tag')).toHaveTextContent('RFETM')
    expect(within(dialog).getByText('Proper')).toBeInTheDocument()
    expect(container.querySelector('.badge svg.state-icon-upcoming')).toHaveAttribute('aria-hidden', 'true')
    const fields = Object.fromEntries([...container.querySelectorAll('.calendar-dialog-field')]
      .map((field) => [field.querySelector('dt').textContent, field.querySelector('dd').textContent]))
    expect(fields).toEqual({
      'Data i hora': expect.stringMatching(/19 de set\. del 2026, 18:30/),
      Pavelló: 'Pavelló (Terrassa)',
      Competició: 'tercera',
      Grup: 'Grup 1',
      Fase: '1a Fase',
      Jornada: '3',
    })
  })

  it('shows the result of a played match and omits it otherwise', () => {
    const { unmount } = renderDialog({ status: 'PLAYED', calendarState: 'PLAYED', homeGamesWon: 4, awayGamesWon: 1 })
    expect(screen.getByText('Resultat')).toBeInTheDocument()
    expect(screen.getByText('4–1')).toBeInTheDocument()
    unmount()

    renderDialog()
    expect(screen.queryByText('Resultat')).toBeNull()
  })

  it('shows an undated match as such and leaves out the missing fields', () => {
    const { container } = renderDialog({ dateTime: null, calendarState: 'UNDATED', venue: null, city: null, phase: null, round: null, source: undefined })

    const labels = [...container.querySelectorAll('.calendar-dialog-field dt')].map((node) => node.textContent)
    expect(labels).toEqual(['Data i hora', 'Competició', 'Grup'])
    expect(container.querySelector('.calendar-dialog-field dd')).toHaveTextContent('Sense data')
    expect(container.querySelector('.cal-source-tag')).toBeNull()
  })

  it('links to the match summary keeping the return to the calendar', () => {
    renderDialog()

    expect(screen.getByRole('link', { name: 'Obre el resum del partit' }))
      .toHaveAttribute('href', '/partits/m1?source=FCTT&season=2026-2027')
  })

  it('offers the overdue buttons only with write permission and only on scheduled matches', () => {
    const marked = { overdueMarkable: true, calendarState: 'AWAITING_RESULT' }
    const { unmount } = renderDialog(marked, { canWrite: false })
    expect(screen.queryByRole('button', { name: /endarrerit/ })).toBeNull()
    unmount()

    const played = renderDialog({ status: 'PLAYED', calendarState: 'PLAYED' }, { canWrite: true })
    expect(screen.queryByRole('button', { name: /endarrerit/ })).toBeNull()
    played.unmount()

    const notYet = renderDialog({ overdueMarkable: false }, { canWrite: true })
    expect(screen.queryByRole('button', { name: /endarrerit/ })).toBeNull()
    notYet.unmount()

    const { onToggleMark } = renderDialog(marked, { canWrite: true })
    fireEvent.click(screen.getByRole('button', { name: 'Marcar com a endarrerit Club A – Club B' }))
    expect(onToggleMark).toHaveBeenCalledWith(expect.objectContaining({ id: 'm1' }))
  })

  it('shows the clear action and who marked the match, and the busy state', () => {
    renderDialog({
      overdueMarked: true, calendarState: 'OVERDUE', overdueMarkedBy: 'admin', overdueMarkedAt: '2026-09-20T10:00:00+02:00',
    }, { canWrite: true, busyId: 'm1' })

    expect(screen.getByText(/Endarrerit \(manual\) marcat per admin/)).toBeInTheDocument()
    const button = screen.getByRole('button', { name: 'Treure la marca d’endarrerit Club A – Club B' })
    expect(button).toBeDisabled()
    expect(button).toHaveTextContent('Processant...')
  })

  it('moves focus into the dialog, keeps Tab inside and restores focus and scrolling on close', () => {
    document.body.style.overflow = 'auto'
    const opener = document.createElement('button')
    document.body.appendChild(opener)
    opener.focus()
    const { unmount } = renderDialog()

    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveFocus()
    expect(document.body.style.overflow).toBe('hidden')
    const close = screen.getByRole('button', { name: 'Tanca' })
    const link = screen.getByRole('link', { name: 'Obre el resum del partit' })

    // Inside the dialog the browser moves focus by itself, so Tab is only taken over at the edges.
    expect(fireEvent.keyDown(document, { key: 'Tab' })).toBe(true)
    link.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(close).toHaveFocus()
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(link).toHaveFocus()
    opener.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(close).toHaveFocus()

    opener.focus()
    unmount()
    expect(opener).toHaveFocus()
    expect(document.body.style.overflow).toBe('auto')
    opener.remove()
  })

  it('closes with Escape, the close button and a click on the backdrop, not on the dialog itself', () => {
    const { onClose, container } = renderDialog()

    fireEvent.mouseDown(screen.getByRole('dialog'))
    expect(onClose).not.toHaveBeenCalled()

    fireEvent.keyDown(document, { key: 'Escape' })
    fireEvent.click(screen.getByRole('button', { name: 'Tanca' }))
    fireEvent.mouseDown(container.querySelector('.calendar-dialog-overlay'))
    expect(onClose).toHaveBeenCalledTimes(3)
  })
})
