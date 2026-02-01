import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import PortfolioDashboard from './PortfolioDashboard.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <PortfolioDashboard />
  </StrictMode>,
)
