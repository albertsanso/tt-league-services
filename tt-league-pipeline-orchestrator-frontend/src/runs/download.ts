/** Hands a downloaded body to the browser as a file save; the object URL lives only for the click. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

/** The file name the server suggested, or one built from the unit when it sent none. */
export function packageFilename(suggested: string | null, runId: string, unitId: string): string {
  return suggested ?? `package-${runId}-${unitId}.zip`
}
