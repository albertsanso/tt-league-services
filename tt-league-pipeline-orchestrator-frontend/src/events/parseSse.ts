export interface SseEvent {
  readonly type: 'event'
  readonly event: string
  readonly data: string
  readonly id?: string
}

export interface SseRetry {
  readonly type: 'retry'
  readonly milliseconds: number
}

export type SseItem = SseEvent | SseRetry

export interface SseParser {
  /** Feeds a decoded text chunk and returns the records completed by it. */
  feed(chunk: string): SseItem[]
}

/** Incremental parser for the text/event-stream format (WHATWG HTML, section 9.2). */
export function createSseParser(): SseParser {
  let buffer = ''
  let atStart = true
  let eventName = ''
  let dataLines: string[] = []
  let lastId: string | undefined

  function processLine(line: string, out: SseItem[]): void {
    if (line === '') {
      if (dataLines.length > 0) {
        out.push({ type: 'event', event: eventName === '' ? 'message' : eventName, data: dataLines.join('\n'), id: lastId })
      }
      eventName = ''
      dataLines = []
      return
    }
    if (line.startsWith(':')) {
      return
    }
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    switch (field) {
      case 'event':
        eventName = value
        break
      case 'data':
        dataLines.push(value)
        break
      case 'id':
        if (!value.includes('\0')) {
          lastId = value
        }
        break
      case 'retry':
        if (/^\d+$/.test(value)) {
          out.push({ type: 'retry', milliseconds: Number(value) })
        }
        break
      default:
        break
    }
  }

  return {
    feed(chunk: string): SseItem[] {
      buffer += chunk
      if (atStart && buffer.length > 0) {
        if (buffer.startsWith('﻿')) {
          buffer = buffer.slice(1)
        }
        atStart = false
      }
      const out: SseItem[] = []
      for (;;) {
        const index = buffer.search(/[\r\n]/)
        if (index === -1) {
          break
        }
        if (buffer[index] === '\r' && index === buffer.length - 1) {
          break
        }
        const line = buffer.slice(0, index)
        const terminatorLength = buffer[index] === '\r' && buffer[index + 1] === '\n' ? 2 : 1
        buffer = buffer.slice(index + terminatorLength)
        processLine(line, out)
      }
      return out
    },
  }
}
