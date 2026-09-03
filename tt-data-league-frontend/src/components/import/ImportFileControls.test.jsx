import { cleanup, fireEvent, render, screen } from '@testing-library/react'
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
})
