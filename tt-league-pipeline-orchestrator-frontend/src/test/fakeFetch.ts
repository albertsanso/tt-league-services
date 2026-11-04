export type FakeHandler = (request: { url: string; method: string; headers: Record<string, string>; body?: string }) =>
  | Response
  | Promise<Response>

export function json(body: unknown, status = 200, type = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': type } })
}

export interface FakeFetch {
  readonly mock: ReturnType<typeof vi.fn>
  calls(): { url: string; method: string; headers: Record<string, string>; body?: string }[]
  callsTo(pathFragment: string): { url: string; method: string; headers: Record<string, string>; body?: string }[]
}

/** Replaces global fetch with a handler that routes by request; unrouted requests fail the test loudly. */
export function stubFetch(handler: FakeHandler): FakeFetch {
  const recorded: { url: string; method: string; headers: Record<string, string>; body?: string }[] = []
  const mock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = {
      url: String(input),
      method: init?.method ?? 'GET',
      headers: { ...((init?.headers as Record<string, string> | undefined) ?? {}) },
      body: typeof init?.body === 'string' ? init.body : undefined,
    }
    recorded.push(request)
    return handler(request)
  })
  vi.stubGlobal('fetch', mock)
  return {
    mock,
    calls: () => recorded,
    callsTo: (fragment) => recorded.filter((call) => call.url.includes(fragment)),
  }
}
