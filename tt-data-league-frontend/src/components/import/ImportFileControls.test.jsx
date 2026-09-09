import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ImportFileControls from './ImportFileControls.jsx'

afterEach(cleanup)

describe('ImportFileControls', () => {
  it('keeps submission disabled until a file is selected', () => {
    render(<ImportFileControls file={null} onFileChange={vi.fn()} onLoad={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Carrega' })).toBeDisabled()
  })

  it('exposes progress and success state through semantic status controls', () => {
    render(
      <ImportFileControls
        file={new File(['data'], 'season.zip')}
        onFileChange={vi.fn()}
        onLoad={vi.fn()}
        uploadState={{ status: 'uploading', progress: 42 }}
      />,
    )

    expect(screen.getByRole('progressbar', { name: 'Progrés de la pujada' })).toHaveValue(42)
    expect(screen.getByRole('status')).toHaveTextContent('Pujant el fitxer')
  })

  it('offers retry after an upload failure', () => {
    const onLoad = vi.fn()
    render(
      <ImportFileControls
        file={new File(['data'], 'season.zip')}
        onFileChange={vi.fn()}
        onLoad={onLoad}
        uploadState={{ status: 'error', progress: 0, error: new Error('offline') }}
      />,
    )

    const retry = screen.getByRole('button', { name: 'Torna a provar la pujada' })
    expect(screen.getByRole('alert')).toHaveTextContent('No s’ha pogut pujar el fitxer')
    fireEvent.click(retry)
    expect(onLoad).toHaveBeenCalledOnce()
  })

  it('clears the native file input once the selected file is reset after a successful upload', () => {
    const { container, rerender } = render(
      <ImportFileControls
        file={new File(['data'], 'season.zip')}
        onFileChange={vi.fn()}
        onLoad={vi.fn()}
        uploadState={{ status: 'success', progress: 100 }}
      />,
    )
    const input = within(container).getByLabelText('Fitxer d’importació')
    const file = new File(['data'], 'season.zip')
    fireEvent.change(input, { target: { files: [file] } })
    expect(input.files).toHaveLength(1)

    rerender(
      <ImportFileControls
        file={null}
        onFileChange={vi.fn()}
        onLoad={vi.fn()}
        uploadState={{ status: 'idle', progress: 0 }}
      />,
    )

    const resetInput = within(container).getByLabelText('Fitxer d’importació')
    expect(resetInput.files).toHaveLength(0)
  })
})
