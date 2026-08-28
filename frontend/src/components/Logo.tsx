import { ChartNoAxesCombined } from 'lucide-react'

/** Identidad visual reutilizable en sus variantes completa y compacta. */
export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className="brand" aria-label="Porbe">
      <span className="brand__mark"><ChartNoAxesCombined size={21} strokeWidth={1.8} /></span>
      {!compact && <span className="brand__name">Porbe</span>}
    </div>
  )
}
