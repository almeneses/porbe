'use strict'

const QRCode = require('qrcode')
const { Client, LocalAuth, MessageMedia } = require('whatsapp-web.js')
const { normalizePhoneNumber } = require('./phone')

/** Mantiene el navegador de WhatsApp Web, su sesión persistente y el estado mostrado por la UI. */
class WhatsAppGateway {
  constructor(options = {}) {
    this.state = status('STARTING', false, null, 'Iniciando WhatsApp Web…')
    this.client = options.client ?? new Client({
      authStrategy: new LocalAuth({
        clientId: options.clientId ?? 'porbe',
        dataPath: options.sessionPath ?? '/app/data/auth',
      }),
      puppeteer: {
        headless: true,
        executablePath: options.executablePath || undefined,
        args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
      },
    })
    this.bindEvents()
  }

  bindEvents() {
    this.client.on('qr', async (qr) => {
      try {
        const qrDataUrl = await QRCode.toDataURL(qr, { margin: 2, width: 320 })
        this.state = status('QR_REQUIRED', false, qrDataUrl, 'Escanea el código QR para vincular WhatsApp.')
      } catch (error) {
        this.state = status('FAILED', false, null, `No fue posible preparar el código QR: ${error.message}`)
      }
    })
    this.client.on('authenticated', () => {
      this.state = status('AUTHENTICATING', false, null, 'WhatsApp está terminando de vincular la sesión…')
    })
    this.client.on('ready', () => {
      this.state = {
        ...status('READY', true, null, 'WhatsApp está conectado y listo para enviar.'),
        accountLabel: maskAccount(this.client.info?.wid?.user),
      }
    })
    this.client.on('auth_failure', (message) => {
      this.state = status('AUTH_FAILURE', false, null, message || 'WhatsApp rechazó la sesión guardada.')
    })
    this.client.on('disconnected', (reason) => {
      this.state = status('DISCONNECTED', false, null, `WhatsApp se desconectó: ${reason || 'sin detalle'}.`)
    })
  }

  async initialize() {
    try {
      await this.client.initialize()
    } catch (error) {
      this.state = status('FAILED', false, null, `No fue posible iniciar WhatsApp Web: ${error.message}`)
      throw error
    }
  }

  currentStatus() {
    return this.state
  }

  /** Comprueba el destinatario antes de enviar el PNG como una imagen con texto. */
  async sendReport(request) {
    if (!this.state.ready) {
      throw serviceError(409, 'WHATSAPP_NOT_READY', 'WhatsApp todavía no está conectado.')
    }

    const recipient = normalizePhoneNumber(request.to)
    const numberId = await this.client.getNumberId(recipient)
    if (!numberId) {
      throw serviceError(422, 'NUMBER_NOT_REGISTERED', 'El número indicado no está registrado en WhatsApp.')
    }

    const media = new MessageMedia('image/png', request.mediaBase64, request.filename)
    const message = await this.client.sendMessage(numberId._serialized, media, {
      caption: request.caption,
      waitUntilMsgSent: true,
    })
    return {
      status: 'SENT',
      messageId: message.id?._serialized ?? null,
      sentAt: new Date().toISOString(),
    }
  }

  async destroy() {
    await this.client.destroy()
  }
}

function status(state, ready, qrDataUrl, message) {
  return { state, ready, qrDataUrl, accountLabel: null, message, updatedAt: new Date().toISOString() }
}

function maskAccount(value) {
  if (!value) return null
  return `•••• ${String(value).slice(-4)}`
}

function serviceError(statusCode, code, message) {
  const error = new Error(message)
  error.status = statusCode
  error.code = code
  return error
}

module.exports = { WhatsAppGateway }
