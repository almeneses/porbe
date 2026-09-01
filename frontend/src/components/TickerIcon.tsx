import { useEffect, useState } from 'react'
import { marketDataApi } from '../market/api'

/** Muestra la imagen editable del ticker y cae a iniciales cuando aún no existe. */
export function TickerIcon({ ticker, size = 38, className = '' }: { ticker: string; size?: number; className?: string }) {
  const [failed, setFailed] = useState(false)
  useEffect(() => setFailed(false), [ticker])
  const initials = ticker.replace('.CL', '').replace(/[^A-Z0-9]/gi, '').slice(0, 2).toUpperCase() || '—'
  return (
    <span className={`ticker-icon ${className}`} style={{ width: size, height: size }} aria-hidden="true">
      {!failed && <img src={marketDataApi.iconUrl(ticker)} alt="" onError={() => setFailed(true)} />}
      {failed && <span>{initials}</span>}
    </span>
  )
}
