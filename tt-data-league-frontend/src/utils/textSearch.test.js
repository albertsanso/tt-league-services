import { describe, expect, it } from 'vitest'
import { matchesQuery } from './textSearch.js'

describe('matchesQuery', () => {
  it('matches exact text', () => {
    expect(matchesQuery('Maria Player', 'Maria Player')).toBe(true)
  })

  it('is case-insensitive', () => {
    expect(matchesQuery('Maria Player', 'maria player')).toBe(true)
    expect(matchesQuery('Maria Player', 'MARIA')).toBe(true)
  })

  it('matches partial substrings', () => {
    expect(matchesQuery('Maria Player', 'ria pla')).toBe(true)
  })

  it('does not match unrelated text', () => {
    expect(matchesQuery('Maria Player', 'Joan')).toBe(false)
  })

  it('is accent-insensitive when the query has no accents', () => {
    expect(matchesQuery('Núria Pérez', 'nuria perez')).toBe(true)
    expect(matchesQuery('Núria Pérez', 'nuria')).toBe(true)
  })

  it('is accent-insensitive when the text has no accents but the query does', () => {
    expect(matchesQuery('Garcia', 'García')).toBe(true)
  })

  it('treats an empty query as matching everything', () => {
    expect(matchesQuery('Maria Player', '')).toBe(true)
    expect(matchesQuery('', '')).toBe(true)
  })

  it('treats a whitespace-only query as matching everything', () => {
    expect(matchesQuery('Maria Player', '   ')).toBe(true)
  })

  it('treats a null/undefined text as empty', () => {
    expect(matchesQuery(null, 'maria')).toBe(false)
    expect(matchesQuery(undefined, '')).toBe(true)
  })
})
