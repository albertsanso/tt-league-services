const PROCESS_STATUSES = new Set(['loading', 'success', 'empty-result', 'failure'])

export function normalizeImportProcess(payload) {
  const process = payload?.response ?? payload
  if (!process || typeof process !== 'object') {
    throw new Error('La resposta de la importació no és vàlida.')
  }

  const status = String(process.status ?? 'failure').toLowerCase().replaceAll('_', '-')
  return {
    importResourceId: process.importResourceId ?? process.id ?? null,
    source: process.source ?? null,
    season: process.season ?? null,
    resourceType: process.resourceType ?? null,
    status: PROCESS_STATUSES.has(status) ? status : 'failure',
    findings: Array.isArray(process.findings) ? process.findings : [],
    processingErrors: Array.isArray(process.processingErrors) ? process.processingErrors : [],
    filesSeen: Number(process.filesSeen ?? 0),
    itemsPersisted: Number(process.itemsPersisted ?? 0),
    skipped: Number(process.skipped ?? 0),
    processorFailures: Number(process.processorFailures ?? 0),
  }
}
