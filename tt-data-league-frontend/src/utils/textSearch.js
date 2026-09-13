function normalize(value) {
  return (value ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
}

export function matchesQuery(text, query) {
  const normalizedQuery = normalize(query).trim()
  if (!normalizedQuery) return true
  return normalize(text).includes(normalizedQuery)
}
