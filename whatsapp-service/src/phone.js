'use strict'

/** Normaliza un número internacional y rechaza texto o extensiones inesperadas. */
function normalizePhoneNumber(value) {
  if (typeof value !== 'string' || value.trim() === '') {
    throw validationError('Indica el número de WhatsApp con código de país.')
  }

  const trimmed = value.trim()
  if (!/^[+\d\s().-]+$/.test(trimmed)) {
    throw validationError('El número de WhatsApp contiene caracteres no permitidos.')
  }

  const digits = trimmed.replace(/\D/g, '')
  if (digits.length < 8 || digits.length > 15) {
    throw validationError('Usa un número internacional de 8 a 15 dígitos, incluido el código de país.')
  }
  return digits
}

function validationError(message) {
  const error = new Error(message)
  error.status = 422
  error.code = 'INVALID_RECIPIENT'
  return error
}

module.exports = { normalizePhoneNumber }
