import Typography from '@mui/material/Typography'
import { useParams } from 'react-router-dom'

export default function RunDetailPage() {
  const { runId } = useParams()
  return (
    <>
      <Typography variant="h5" component="h2" gutterBottom>
        Run details
      </Typography>
      <Typography color="text.secondary">Details for run {runId} are not available yet.</Typography>
    </>
  )
}
