import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useAuth } from '../auth/useAuth'
import { apiConfig } from '../config'
import { RunEventsContext } from './eventsContext'
import type { EventConnection, PipelineEvent, PipelineEventListener } from './eventsContext'
import { createSseParser } from './parseSse'
import { reconnectDelay } from './reconnectDelay'

const EVENTS_PATH = '/api/pipeline/events'

function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

/** Owns the single server-sent-event connection of a signed-in session. */
export function RunEventsProvider({ children }: { children: ReactNode }) {
  const { token, signOut } = useAuth()
  const [connection, setConnection] = useState<EventConnection>({ state: 'connecting' })
  const listeners = useRef(new Set<PipelineEventListener>())

  const subscribe = useCallback((listener: PipelineEventListener) => {
    listeners.current.add(listener)
    return () => {
      listeners.current.delete(listener)
    }
  }, [])

  useEffect(() => {
    if (!token) {
      return
    }
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    let failures = 0
    let serverRetryMs = 0
    let readyCount = 0

    const publish = (event: PipelineEvent) => {
      for (const listener of [...listeners.current]) {
        try {
          listener(event)
        } catch (error) {
          console.error('Run event listener failed', error)
        }
      }
    }

    const handleRecord = (name: string, data: string) => {
      if (name === 'ready') {
        failures = 0
        readyCount += 1
        setConnection((previous) => ({ ...previous, state: 'open' }))
        if (readyCount > 1) {
          publish({ type: 'reconnected' })
        }
        return
      }
      if (name !== 'run' && name !== 'step' && name !== 'unit' && name !== 'pending-trigger' && name !== 'match-days') {
        return
      }
      let payload: unknown
      try {
        payload = JSON.parse(data)
      } catch {
        console.warn(`Dropped malformed "${name}" event`)
        return
      }
      publish({ type: name, payload } as PipelineEvent)
    }

    const scheduleReconnect = () => {
      failures += 1
      setConnection((previous) => ({ ...previous, state: 'reconnecting' }))
      timer = setTimeout(() => {
        void connect()
      }, reconnectDelay(serverRetryMs, failures, Math.random()))
    }

    const connect = async () => {
      let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
      try {
        const response = await fetch(`${apiConfig.orchestratorBaseUrl}${EVENTS_PATH}`, {
          headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
          signal: controller.signal,
        })
        if (response.status === 401) {
          setConnection((previous) => ({ ...previous, state: 'stopped' }))
          signOut('expired')
          return
        }
        if (!response.ok || response.body === null) {
          throw new Error(`Event stream rejected (${response.status})`)
        }
        reader = response.body.getReader()
        const activeReader = reader
        controller.signal.addEventListener('abort', () => {
          activeReader.cancel().catch(() => undefined)
        })
        const decoder = new TextDecoder()
        const parser = createSseParser()
        for (;;) {
          const { done, value } = await activeReader.read()
          if (done) {
            break
          }
          setConnection((previous) => ({ ...previous, lastEventAt: Date.now() }))
          for (const item of parser.feed(decoder.decode(value, { stream: true }))) {
            if (item.type === 'retry') {
              serverRetryMs = item.milliseconds
            } else {
              handleRecord(item.event, item.data)
            }
          }
        }
      } catch (error) {
        if (isAbort(error) || controller.signal.aborted) {
          return
        }
      }
      if (!controller.signal.aborted) {
        scheduleReconnect()
      }
    }

    void connect()
    return () => {
      controller.abort()
      clearTimeout(timer)
    }
  }, [token, signOut])

  const value = useMemo(() => ({ connection, subscribe }), [connection, subscribe])
  return <RunEventsContext.Provider value={value}>{children}</RunEventsContext.Provider>
}
