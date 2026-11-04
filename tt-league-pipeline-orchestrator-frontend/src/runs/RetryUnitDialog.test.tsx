import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../api/ApiError'
import { TestApiProvider } from '../test/TestApiProvider'
import { RetryUnitDialog } from './RetryUnitDialog'

function setup(retryUnit: ReturnType<typeof vi.fn>) {
  const onClose = vi.fn()
  const onRetried = vi.fn()
  render(
    <TestApiProvider api={{ runs: { retryUnit } as never }}>
      <RetryUnitDialog runId="r1" unit={{ id: 'u2', label: 'Tercera Group 1' }} onClose={onClose} onRetried={onRetried} />
    </TestApiProvider>,
  )
  return { onClose, onRetried, user: userEvent.setup() }
}

describe('RetryUnitDialog', () => {
  it('names the unit and explains that the run and the other units stay as they are', () => {
    setup(vi.fn())

    const dialog = screen.getByRole('dialog', { name: 'Retry unit' })
    expect(dialog).toHaveTextContent('Tercera Group 1')
    expect(dialog).toHaveTextContent('as a new run')
  })

  it('retries the unit and hands over the id of the new run', async () => {
    const retryUnit = vi.fn().mockResolvedValue({ runId: 'r9' })
    const { user, onRetried } = setup(retryUnit)

    await user.click(screen.getByRole('button', { name: 'Retry unit' }))

    expect(retryUnit).toHaveBeenCalledWith('r1', 'u2')
    expect(onRetried).toHaveBeenCalledWith('r9')
  })

  it.each([
    [409, 'Source RFETM already has an active run'],
    [422, 'The unit cannot be retried'],
    [404, 'Unit not found'],
  ])('shows the server message of a %i and stays open', async (status, message) => {
    const retryUnit = vi.fn().mockRejectedValue(new ApiError(status, { detail: message }, message))
    const { user, onRetried, onClose } = setup(retryUnit)

    await user.click(screen.getByRole('button', { name: 'Retry unit' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(onRetried).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Retry unit' })).toBeEnabled()
  })

  it('cancels without a request', async () => {
    const retryUnit = vi.fn()
    const { user, onClose } = setup(retryUnit)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onClose).toHaveBeenCalled()
    expect(retryUnit).not.toHaveBeenCalled()
  })
})
