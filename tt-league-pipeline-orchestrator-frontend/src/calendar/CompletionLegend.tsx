import Box from '@mui/material/Box'
import { COMPLETION_LABELS, COMPLETION_ORDER, completionColor } from './completion'

/** What the entry colours mean; each swatch carries its text. */
export function CompletionLegend() {
  return (
    <Box component="ul" aria-label="Completion legend" sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', listStyle: 'none', p: 0, m: 0, mb: 2 }}>
      {COMPLETION_ORDER.map((completion) => {
        const color = completionColor(completion)
        return (
          <Box component="li" key={completion} sx={{ display: 'flex', alignItems: 'center', gap: 0.75, fontSize: 13 }}>
            <Box
              aria-hidden
              sx={(theme) => ({
                width: 14,
                height: 14,
                borderRadius: 0.5,
                backgroundColor: color === 'default' ? theme.palette.text.secondary : theme.palette[color].main,
              })}
            />
            {COMPLETION_LABELS[completion]}
          </Box>
        )
      })}
    </Box>
  )
}
