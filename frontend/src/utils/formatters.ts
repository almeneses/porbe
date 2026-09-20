/** Formateadores compartidos para conservar la convención colombiana en la UI. */
const copFormatter = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

const percentageFormatter = new Intl.NumberFormat('es-CO', {
  style: 'percent',
  minimumFractionDigits: 1,
  maximumFractionDigits: 2,
})

const amountFormatter = new Intl.NumberFormat('es-CO', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
})

const quantityFormatter = new Intl.NumberFormat('es-CO', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 8,
})

const dateFormatter = new Intl.DateTimeFormat('es-CO', {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  timeZone: 'UTC',
})

const timeFormatter = new Intl.DateTimeFormat('es-CO', {
  hour: 'numeric',
  minute: '2-digit',
  timeZone: 'UTC',
})

export function formatCop(value: number) {
  return copFormatter.format(value)
}

export function formatPercentage(value: number | null | undefined) {
  return value == null ? '—' : percentageFormatter.format(value)
}

export function formatAmount(value: number) {
  return amountFormatter.format(value)
}

export function formatQuantity(value: number) {
  return quantityFormatter.format(value)
}

/** Trata las fechas de negocio sin hora como valores de calendario, no locales. */
export function formatDate(value: string) {
  return dateFormatter.format(new Date(`${value}T00:00:00Z`))
}

export function formatDateTime(value: string, timeZone = 'America/Bogota') {
  return new Intl.DateTimeFormat('es-CO', {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    timeZone,
  }).format(new Date(value))
}

export function formatTime(value: string) {
  return timeFormatter.format(new Date(`1970-01-01T${value}Z`))
}

export function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency,
    maximumFractionDigits: currency === 'COP' ? 0 : 2,
  }).format(value)
}
