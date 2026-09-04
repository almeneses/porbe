'use strict'

const crypto = require('node:crypto')
const express = require('express')

const MAX_IMAGE_BYTES = 5 * 1024 * 1024

/** Crea la API interna; el gateway se inyecta para probar HTTP sin abrir WhatsApp ni Chromium. */
function createApp({ gateway, internalToken }) {
  if (!internalToken) {
    throw new Error('WHATSAPP_INTERNAL_TOKEN es obligatorio.')
  }

  const app = express()
  app.disable('x-powered-by')
  app.use(express.json({ limit: '8mb' }))

  app.get('/health', (_request, response) => {
    response.json({ status: 'UP' })
  })

  app.use('/api', authorize(internalToken))
  app.get('/api/status', (_request, response) => {
    response.json(gateway.currentStatus())
  })

  app.post('/api/messages/report', async (request, response, next) => {
    try {
      validateReportRequest(request.body)
      response.status(201).json(await gateway.sendReport(request.body))
    } catch (error) {
      next(error)
    }
  })

  app.use((error, _request, response, _next) => {
    const status = Number.isInteger(error.status) ? error.status : 500
    response.status(status).json({
      code: error.code || 'WHATSAPP_SERVICE_ERROR',
      message: status >= 500 ? 'No fue posible completar la operación con WhatsApp.' : error.message,
    })
  })
  return app
}

function authorize(expectedToken) {
  return (request, response, next) => {
    const suppliedToken = request.get('X-Porbe-Internal-Token') || ''
    const supplied = Buffer.from(suppliedToken)
    const expected = Buffer.from(expectedToken)
    if (supplied.length !== expected.length || !crypto.timingSafeEqual(supplied, expected)) {
      response.status(401).json({ code: 'UNAUTHORIZED', message: 'Acceso interno no autorizado.' })
      return
    }
    next()
  }
}

/** Limita el contenido aceptado para evitar usar este servicio como un relé genérico. */
function validateReportRequest(body) {
  if (!body || typeof body !== 'object') {
    throw requestError('INVALID_REQUEST', 'La solicitud está vacía.')
  }
  if (typeof body.caption !== 'string' || body.caption.trim() === '' || body.caption.length > 500) {
    throw requestError('INVALID_CAPTION', 'El texto del informe debe tener entre 1 y 500 caracteres.')
  }
  if (typeof body.filename !== 'string' || !/^[a-zA-Z0-9_.-]+\.png$/.test(body.filename)) {
    throw requestError('INVALID_FILENAME', 'El nombre de archivo PNG no es válido.')
  }
  if (typeof body.mediaBase64 !== 'string' || body.mediaBase64.trim() === '') {
    throw requestError('INVALID_IMAGE', 'La imagen del informe es obligatoria.')
  }
  const decoded = Buffer.from(body.mediaBase64, 'base64')
  if (decoded.length === 0 || decoded.length > MAX_IMAGE_BYTES || decoded.toString('base64') !== body.mediaBase64) {
    throw requestError('INVALID_IMAGE', 'La imagen no es un PNG válido o supera 5 MB.')
  }
  if (!body.mediaBase64.startsWith('iVBORw0KGgo')) {
    throw requestError('INVALID_IMAGE', 'Solo se aceptan imágenes PNG.')
  }
}

function requestError(code, message) {
  const error = new Error(message)
  error.status = 422
  error.code = code
  return error
}

module.exports = { createApp }
