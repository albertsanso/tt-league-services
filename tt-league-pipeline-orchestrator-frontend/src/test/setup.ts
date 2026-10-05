import '@testing-library/jest-dom/vitest'
import { configure } from '@testing-library/react'

// Lazy route chunks are transformed on first use, which can exceed the 1 s default under parallel load.
configure({ asyncUtilTimeout: 5000 })
