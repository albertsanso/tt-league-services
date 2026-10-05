import { createSseParser } from './parseSse'

describe('createSseParser', () => {
  it('parses a record with an explicit event name', () => {
    const items = createSseParser().feed('event: run\ndata: {"a":1}\n\n')
    expect(items).toEqual([{ type: 'event', event: 'run', data: '{"a":1}', id: undefined }])
  })

  it('defaults the event name to message', () => {
    expect(createSseParser().feed('data: hi\n\n')[0]).toMatchObject({ event: 'message', data: 'hi' })
  })

  it.each([
    ['LF', '\n'],
    ['CRLF', '\r\n'],
    ['CR', '\r'],
  ])('accepts %s line ends', (_name, eol) => {
    const items = createSseParser().feed(`event: step${eol}data: x${eol}${eol}e`)
    expect(items).toHaveLength(1)
    expect(items[0]).toMatchObject({ event: 'step', data: 'x' })
  })

  it('joins multi-line data', () => {
    expect(createSseParser().feed('data: a\ndata: b\n\n')[0]).toMatchObject({ data: 'a\nb' })
  })

  it('ignores comment lines and records without data', () => {
    expect(createSseParser().feed(': keep-alive\n\nevent: x\n\n')).toEqual([])
  })

  it('reassembles records split across chunks, including a split CRLF', () => {
    const parser = createSseParser()
    expect(parser.feed('event: ru')).toEqual([])
    expect(parser.feed('n\r')).toEqual([])
    expect(parser.feed('\ndata: {"id"')).toEqual([])
    expect(parser.feed(':1}\r\n\r')).toEqual([])
    const items = parser.feed('\n')
    expect(items).toEqual([{ type: 'event', event: 'run', data: '{"id":1}', id: undefined }])
  })

  it('reports retry values and ignores invalid ones', () => {
    const items = createSseParser().feed('retry: 5000\nretry: abc\n\n')
    expect(items).toEqual([{ type: 'retry', milliseconds: 5000 }])
  })

  it('strips a leading BOM and tracks the last event id', () => {
    const items = createSseParser().feed('﻿id: 7\nevent: ready\ndata: {}\n\n')
    expect(items).toEqual([{ type: 'event', event: 'ready', data: '{}', id: '7' }])
  })

  it('does not carry the event name over to the next record', () => {
    const items = createSseParser().feed('event: run\ndata: 1\n\ndata: 2\n\n')
    expect(items.map((item) => (item.type === 'event' ? item.event : ''))).toEqual(['run', 'message'])
  })
})
