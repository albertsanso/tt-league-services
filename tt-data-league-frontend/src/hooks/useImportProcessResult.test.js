import { describe, expect, it } from 'vitest'
import { normalizeImportProcess } from './useImportProcessResult.js'

describe('normalizeImportProcess', () => {
  it('unwraps the command response and normalizes process fields', () => {
    expect(normalizeImportProcess({
      response: {
        status: 'SUCCESS',
        findings: [{ message: 'notice' }],
        processingErrors: null,
        filesSeen: '2',
        itemsPersisted: undefined,
        skipped: null,
      },
    })).toMatchObject({
      status: 'success',
      findings: [{ message: 'notice' }],
      processingErrors: [],
      filesSeen: 2,
      itemsPersisted: 0,
      skipped: 0,
    })
  })
})
