import Typography from '@mui/material/Typography'

export default function NotFoundPage() {
  return (
    <>
      <Typography variant="h5" component="h2" gutterBottom>
        Page not found
      </Typography>
      <Typography color="text.secondary">The page you asked for does not exist.</Typography>
    </>
  )
}
