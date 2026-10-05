import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../api/ApiError'
import type { Api } from '../api/bindApi'
import type { MatchDayDetail } from '../api/types'
import { makeMatch, makeMatchDayDetail } from '../test/matchDayFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import { MatchDayActionDialog, MAX_NOTE_LENGTH } from './MatchDayActionDialog'
import type { MatchDayAction } from './MatchDayActionDialog'

type Mock = ReturnType<typeof vi.fn>

function setup(action: MatchDayAction, matchDays: Record<string, Mock> = {}) {
  const onDone = vi.fn()
  const onConflict = vi.fn()
  const onClose = vi.fn()
  render(
    <TestApiProvider api={{ matchDays } as unknown as Partial<Api>}>
      <MatchDayActionDialog
        matchDayId="day-1"
        action={action}
        matches={makeMatchDayDetail().matches}
        onDone={onDone}
        onConflict={onConflict}
        onClose={onClose}
      />
    </TestApiProvider>,
  )
  return { onDone, onConflict, onClose, user: userEvent.setup() }
}

function problem(status: number, message: string, field?: string) {
  return new ApiError(status, { status, detail: message, field }, message)
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => {
    resolve = done
    reject = fail
  })
  return { promise, resolve, reject }
}

const detail: MatchDayDetail = makeMatchDayDetail()

describe('MatchDayActionDialog', () => {
  it('needs the text of a note and sends nothing without it', async () => {
    const addMatchDayNote = vi.fn()
    const { user, onDone } = setup({ kind: 'note', matchId: null }, { addMatchDayNote })

    await user.click(screen.getByRole('button', { name: 'Add note' }))
    expect(screen.getByText('A note needs text')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: /^Note/ }), '   ')
    await user.click(screen.getByRole('button', { name: 'Add note' }))

    expect(addMatchDayNote).not.toHaveBeenCalled()
    expect(onDone).not.toHaveBeenCalled()
  })

  it('rejects a note longer than the server accepts', async () => {
    const addMatchDayNote = vi.fn()
    const { user } = setup({ kind: 'note', matchId: null }, { addMatchDayNote })

    fireEvent.change(screen.getByRole('textbox', { name: /^Note/ }), { target: { value: 'x'.repeat(MAX_NOTE_LENGTH + 1) } })
    await user.click(screen.getByRole('button', { name: 'Add note' }))

    expect(screen.getByText(`At most ${MAX_NOTE_LENGTH} characters`)).toBeInTheDocument()
    expect(addMatchDayNote).not.toHaveBeenCalled()
  })

  it('trims the note, hands the detail to the page and closes', async () => {
    const closeMatchDay = vi.fn().mockResolvedValue(detail)
    const { user, onDone, onClose } = setup({ kind: 'close' }, { closeMatchDay })

    await user.type(screen.getByRole('textbox', { name: 'Note (optional)' }), '  league decision ')
    await user.click(screen.getByRole('button', { name: 'Close match day' }))

    expect(closeMatchDay).toHaveBeenCalledWith('day-1', 'league decision')
    expect(onDone).toHaveBeenCalledWith(detail)
    expect(onClose).toHaveBeenCalled()
  })

  it('disables the form while the request is pending and sends it only once', async () => {
    const pending = deferred<MatchDayDetail>()
    const reopenMatchDay = vi.fn().mockReturnValue(pending.promise)
    const { user, onDone } = setup({ kind: 'reopen' }, { reopenMatchDay })

    await user.click(screen.getByRole('button', { name: 'Reopen match day' }))

    expect(screen.getByRole('button', { name: 'Reopen match day' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    expect(screen.getByRole('textbox')).toBeDisabled()
    expect(reopenMatchDay).toHaveBeenCalledTimes(1)
    pending.resolve(detail)
    await vi.waitFor(() => expect(onDone).toHaveBeenCalledWith(detail))
    expect(reopenMatchDay).toHaveBeenCalledTimes(1)
  })

  it('sends the match of an ignore and of a stop-ignoring', async () => {
    const ignoreMatch = vi.fn().mockResolvedValue(detail)
    const match = makeMatch('m1', 'OVERDUE')
    const { user } = setup({ kind: 'ignore', match }, { ignoreMatch })

    expect(screen.getByText('Home m1 – Away m1')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ignore match' }))

    expect(ignoreMatch).toHaveBeenCalledWith('day-1', 'm1', undefined)
  })

  it('shows the server message of a conflict, asks the page to reload and keeps the dialog open', async () => {
    const closeMatchDay = vi.fn().mockRejectedValue(problem(409, 'Match day is already closed'))
    const { user, onConflict, onClose, onDone } = setup({ kind: 'close' }, { closeMatchDay })

    await user.click(screen.getByRole('button', { name: 'Close match day' }))

    expect(await screen.findByText('Match day is already closed')).toBeInTheDocument()
    expect(onConflict).toHaveBeenCalledTimes(1)
    expect(onClose).not.toHaveBeenCalled()
    expect(onDone).not.toHaveBeenCalled()
    expect(closeMatchDay).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Close match day' })).toBeEnabled()
  })

  it('maps a 400 about the text to the note field', async () => {
    const addMatchDayNote = vi.fn().mockRejectedValue(problem(400, 'text: must not be blank', 'text'))
    const { user } = setup({ kind: 'note', matchId: null }, { addMatchDayNote })

    await user.type(screen.getByRole('textbox', { name: /^Note/ }), 'hello')
    await user.click(screen.getByRole('button', { name: 'Add note' }))

    expect(await screen.findByText('text: must not be blank')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /^Note/ })).toBeInvalid()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it.each([
    [403, 'Forbidden'],
    [404, 'Match day not found'],
    [0, 'Cannot reach the server'],
  ])('shows an alert for a %i answer and does not retry', async (status, message) => {
    const closeMatchDay = vi.fn().mockRejectedValue(problem(status, message))
    const { user, onConflict, onClose } = setup({ kind: 'close' }, { closeMatchDay })

    await user.click(screen.getByRole('button', { name: 'Close match day' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(closeMatchDay).toHaveBeenCalledTimes(1)
    expect(onConflict).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows the message of an unexpected failure', async () => {
    const closeMatchDay = vi.fn().mockRejectedValue(new Error('boom'))
    const { user } = setup({ kind: 'close' }, { closeMatchDay })

    await user.click(screen.getByRole('button', { name: 'Close match day' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
  })

  it('preselects the match a note applies to', () => {
    setup({ kind: 'note', matchId: 'm-postponed' })

    expect(screen.getByRole('combobox', { name: 'Applies to' })).toHaveTextContent('Home m-postponed – Away m-postponed')
  })
})
