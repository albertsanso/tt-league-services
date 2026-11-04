import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'

/** A labelled identifier with a copy button. */
export function CopyValue({ label, value }: { label: string; value: string }) {
  return (
    <Box component="span" sx={{ mr: 2, whiteSpace: 'nowrap' }}>
      {label}: <code>{value}</code>
      <Tooltip title="Copy">
        <IconButton size="small" aria-label={`Copy ${label}`} onClick={() => void navigator.clipboard?.writeText(value)}>
          <ContentCopyIcon fontSize="inherit" />
        </IconButton>
      </Tooltip>
    </Box>
  )
}
