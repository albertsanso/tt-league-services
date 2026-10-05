import { act, render, screen } from '@testing-library/react'
import type { Mock } from 'vitest'
import { AuthContext } from '../auth/authContext'
import type { AuthContextValue } from '../auth/authContext'
import { makeToken } from '../test/jwt'
import { RunEventsProvider } from './RunEventsProvider'
import { reconnectDelay } from './reconnectDelay'
import type { PipelineEvent } from './eventsContext'
import { useEventConnection, useRunEvents } from './useRunEvents'

const encoder = new TextEncoder()

interface Stream {
  push(text: string): void
  end(): void
}

interface Harness {
  streams: Stream[]
  fetchMock: Mock
}

function installStreams(status: (attempt: number) => number = () => 200): Harness {
  const streams: Stream[] = []
  const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
    const attempt = streams.length
    const code = status(attempt)
    let controller!: ReadableStreamDefaultController<Uint8Array>
    const body = new ReadableStream<Uint8Array>({
      start(c) {
        controller = c
      },
    })
    streams.push({
      push: (text) => controller.enqueue(encoder.encode(text)),
      end: () => controller.close(),
    })
    init?.signal?.addEventListener('abort', () => {
      try {
        controller.error(new DOMException('aborted', 'AbortError'))
      } catch {
        // already closed
      }
    })
    return code === 200 ? new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }) : new Response(null, { status: code })
  })
  vi.stubGlobal('fetch', fetchMock)
  return { streams, fetchMock: fetchMock as unknown as Mock }
}

function authValue(token: string, signOut: Mock = vi.fn() as unknown as Mock): AuthContextValue {
  return {
    status: 'signed-in',
    token,
    user: { username: 'u', roles: [], permissions: [], expiresAt: Date.now() + 3_600_000 },
    signIn: async () => undefined,
    signOut,
    getToken: () => token,
  }
}

function Status() {
  const connection = useEventConnection()
  return (
    <p>
      {connection.state}
      {connection.lastEventAt !== undefined ? ':seen' : ''}
    </p>
  )
}

function Listener({ onEvent }: { onEvent: (event: PipelineEvent) => void }) {
  useRunEvents(onEvent)
  return null
}

function setup(options: { token?: string; signOut?: Mock; listeners?: ((event: PipelineEvent) => void)[] } = {}) {
  const token = options.token ?? makeToken()
  const signOut = options.signOut ?? (vi.fn() as unknown as Mock)
  const listeners = options.listeners ?? []
  const view = render(
    <AuthContext.Provider value={authValue(token, signOut)}>
      <RunEventsProvider>
        <Status />
        {listeners.map((listener, index) => (
          <Listener key={index} onEvent={listener} />
        ))}
      </RunEventsProvider>
    </AuthContext.Provider>,
  )
  return { ...view, token, signOut }
}

const flush = () => act(async () => { await vi.advanceTimersByTimeAsync(0) })

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
  vi.spyOn(Math, 'random').mockReturnValue(0.5)
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('reconnectDelay', () => {
  it('uses at least 5 s, doubles per failure, caps at 60 s and applies jitter', () => {
    expect(reconnectDelay(0, 1, 0.5)).toBe(5_000)
    expect(reconnectDelay(2_000, 1, 0.5)).toBe(5_000)
    expect(reconnectDelay(8_000, 1, 0.5)).toBe(8_000)
    expect(reconnectDelay(5_000, 2, 0.5)).toBe(10_000)
    expect(reconnectDelay(5_000, 3, 0.5)).toBe(20_000)
    expect(reconnectDelay(5_000, 10, 0.5)).toBe(60_000)
    expect(reconnectDelay(5_000, 1, 0)).toBe(4_000)
    expect(reconnectDelay(5_000, 1, 1)).toBe(6_000)
  })
})

describe('RunEventsProvider', () => {
  it('sends the bearer header and event-stream accept, and opens on ready', async () => {
    const { streams, fetchMock } = installStreams()
    const { token } = setup()
    await flush()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/pipeline/events')
    expect((init as RequestInit).headers).toEqual({ Accept: 'text/event-stream', Authorization: `Bearer ${token}` })
    expect(screen.getByText(/^connecting/)).toBeInTheDocument()

    streams[0].push('retry: 5000\nevent: ready\ndata: {}\n\n')
    await flush()
    expect(screen.getByText(/^open/)).toBeInTheDocument()
  })

  it('delivers typed run, step and pending-trigger events and ignores unknown ones', async () => {
    const { streams } = installStreams()
    const received: PipelineEvent[] = []
    setup({ listeners: [(event) => received.push(event)] })
    await flush()

    streams[0].push('event: ready\ndata: {}\n\n')
    streams[0].push('event: run\ndata: {"id":"r1","status":"QUEUED"}\n\n')
    streams[0].push('event: step\ndata: {"runId":"r1","kind":"INGEST"}\n\n')
    streams[0].push('event: pending-trigger\ndata: {"source":"RFETM","state":"QUEUED","requestedBy":"u"}\n\n')
    streams[0].push('event: something-else\ndata: {}\n\n')
    await flush()

    expect(received.map((event) => event.type)).toEqual(['run', 'step', 'pending-trigger'])
    expect(received[0]).toMatchObject({ payload: { id: 'r1' } })
  })

  it('records keep-alive comments as activity without delivering events', async () => {
    const { streams } = installStreams()
    const received: PipelineEvent[] = []
    setup({ listeners: [(event) => received.push(event)] })
    await flush()
    expect(screen.queryByText(/:seen/)).not.toBeInTheDocument()

    streams[0].push(': keep-alive\n\n')
    await flush()

    expect(screen.getByText(/:seen/)).toBeInTheDocument()
    expect(received).toEqual([])
  })

  it('reconnects after the stream ends, with growing delay that resets after ready', async () => {
    const { streams, fetchMock } = installStreams((attempt) => (attempt === 1 || attempt === 2 ? 503 : 200))
    const received: PipelineEvent[] = []
    setup({ listeners: [(event) => received.push(event)] })
    await flush()
    streams[0].push('event: ready\ndata: {}\n\n')
    await flush()
    streams[0].end()
    await flush()
    expect(screen.getByText(/^reconnecting/)).toBeInTheDocument()

    await act(async () => { await vi.advanceTimersByTimeAsync(4_999) })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(fetchMock).toHaveBeenCalledTimes(2)

    // 503 → second consecutive failure: 10 s
    await flush()
    await act(async () => { await vi.advanceTimersByTimeAsync(9_999) })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(fetchMock).toHaveBeenCalledTimes(3)

    // third attempt answers 503 again: 20 s
    await flush()
    await act(async () => { await vi.advanceTimersByTimeAsync(19_999) })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(fetchMock).toHaveBeenCalledTimes(4)

    streams[3].push('event: ready\ndata: {}\n\n')
    await flush()
    expect(screen.getByText(/^open/)).toBeInTheDocument()
    expect(received.map((event) => event.type)).toEqual(['reconnected'])

    // ready reset the failure count: next delay is 5 s again
    streams[3].end()
    await flush()
    await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
    expect(fetchMock).toHaveBeenCalledTimes(5)
  })

  it('emits reconnected only from the second ready on', async () => {
    const { streams } = installStreams()
    const received: PipelineEvent[] = []
    setup({ listeners: [(event) => received.push(event)] })
    await flush()

    streams[0].push('event: ready\ndata: {}\n\n')
    await flush()
    expect(received).toEqual([])

    streams[0].end()
    await flush()
    await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
    streams[1].push('event: ready\ndata: {}\n\n')
    await flush()
    expect(received).toEqual([{ type: 'reconnected' }])
  })

  it('stops and signs out on 401', async () => {
    const { fetchMock } = installStreams(() => 401)
    const { signOut } = setup()
    await flush()

    expect(screen.getByText(/^stopped/)).toBeInTheDocument()
    expect(signOut).toHaveBeenCalledWith('expired')
    await act(async () => { await vi.advanceTimersByTimeAsync(120_000) })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('retries after 503', async () => {
    const { fetchMock } = installStreams((attempt) => (attempt === 0 ? 503 : 200))
    setup()
    await flush()
    expect(screen.getByText(/^reconnecting/)).toBeInTheDocument()

    await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('drops a malformed payload without breaking the stream', async () => {
    const { streams } = installStreams()
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const received: PipelineEvent[] = []
    setup({ listeners: [(event) => received.push(event)] })
    await flush()

    streams[0].push('event: run\ndata: {not json\n\nevent: run\ndata: {"id":"ok"}\n\n')
    await flush()

    expect(received).toHaveLength(1)
    expect(warn).toHaveBeenCalledWith('Dropped malformed "run" event')
    expect(JSON.stringify(warn.mock.calls)).not.toContain('not json')
  })

  it('isolates a failing listener', async () => {
    const { streams } = installStreams()
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const received: PipelineEvent[] = []
    setup({
      listeners: [
        () => {
          throw new Error('boom')
        },
        (event) => received.push(event),
      ],
    })
    await flush()

    streams[0].push('event: run\ndata: {"id":"r"}\n\n')
    await flush()

    expect(received).toHaveLength(1)
    expect(error).toHaveBeenCalled()
  })

  it('aborts on unmount and does not reconnect', async () => {
    const { streams, fetchMock } = installStreams()
    const { unmount } = setup()
    await flush()
    streams[0].push('event: ready\ndata: {}\n\n')
    await flush()

    const signal = (fetchMock.mock.calls[0][1] as RequestInit).signal as AbortSignal
    unmount()
    expect(signal.aborted).toBe(true)

    await act(async () => { await vi.advanceTimersByTimeAsync(120_000) })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('restarts the connection when the token changes', async () => {
    const { fetchMock } = installStreams()
    const first = makeToken({ sub: 'a' })
    const second = makeToken({ sub: 'b' })
    const view = setup({ token: first })
    await flush()

    view.rerender(
      <AuthContext.Provider value={authValue(second)}>
        <RunEventsProvider>
          <Status />
        </RunEventsProvider>
      </AuthContext.Provider>,
    )
    await flush()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(((fetchMock.mock.calls[0][1] as RequestInit).signal as AbortSignal).aborted).toBe(true)
    expect((fetchMock.mock.calls[1][1] as RequestInit).headers).toMatchObject({ Authorization: `Bearer ${second}` })
  })
})
