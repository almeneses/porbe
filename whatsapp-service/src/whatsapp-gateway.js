'use strict'

const QRCode = require('qrcode')
const { rm } = require('node:fs/promises')
const path = require('node:path')
const { Client, LocalAuth, MessageMedia } = require('whatsapp-web.js')
const { normalizePhoneNumber } = require('./phone')

/** Mantiene el navegador de WhatsApp Web, su sesión persistente y el estado mostrado por la UI. */
class WhatsAppGateway {
  constructor(options = {}) {
    this.state = status('STARTING', false, null, 'Iniciando WhatsApp Web…')
    this.resetting = false
    this.initializing = false
    this.sending = 0
    this.generation = 0
    this.cachePath = path.resolve(options.cachePath ?? '.wwebjs_cache')
    this.createClient = options.createClient ?? (() => new Client({
      authStrategy: new LocalAuth({
        clientId: options.clientId ?? 'porbe',
        dataPath: options.sessionPath ?? '/app/data/auth',
      }),
      webVersionCache: { type: 'local', path: this.cachePath },
      puppeteer: {
        headless: true,
        executablePath: options.executablePath || undefined,
        args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
      },
    }))
    this.client = this.createClient()
    this.bindEvents()
  }

  bindEvents() {
    const generation = this.generation
    const update = (state) => {
      if (generation === this.generation) this.state = state
    }
    this.client.on('qr', async (qr) => {
      try {
        const qrDataUrl = await QRCode.toDataURL(qr, { margin: 2, width: 320 })
        update(status('QR_REQUIRED', false, qrDataUrl, 'Escanea el código QR para vincular WhatsApp.'))
      } catch (error) {
        update(status('FAILED', false, null, `No fue posible preparar el código QR: ${error.message}`))
      }
    })
    this.client.on('authenticated', () => {
      update(status('AUTHENTICATING', false, null, 'WhatsApp está terminando de vincular la sesión…'))
    })
    this.client.on('ready', () => {
      update({
        ...status('READY', true, null, 'WhatsApp está conectado y listo para enviar.'),
        accountLabel: maskAccount(this.client.info?.wid?.user),
      })
    })
    this.client.on('auth_failure', (message) => {
      update(status('AUTH_FAILURE', false, null, message || 'WhatsApp rechazó la sesión guardada.'))
    })
    this.client.on('disconnected', (reason) => {
      update(status('DISCONNECTED', false, null, `WhatsApp se desconectó: ${reason || 'sin detalle'}.`))
    })
  }

  async initialize() {
    this.initializing = true
    try {
      await this.client.initialize()
    } catch (error) {
      this.state = status('FAILED', false, null, `No fue posible iniciar WhatsApp Web: ${error.message}`)
      throw error
    } finally {
      this.initializing = false
    }
  }

  /** Borra el perfil local incluso si la página de WhatsApp ya no responde. */
  async resetSession() {
    if (this.resetting || this.initializing || this.sending) {
      throw serviceError(409, 'WHATSAPP_BUSY', 'Espera a que termine la operación de WhatsApp e intenta nuevamente.')
    }
    this.resetting = true
    this.generation++
    this.state = status('STARTING', false, null, 'Borrando la vinculación de WhatsApp…')
    try {
      this.client.removeAllListeners()
      await this.client.destroy()
      await this.client.authStrategy.logout()
      await rm(this.cachePath, { recursive: true, force: true, maxRetries: 4 })
      this.client = this.createClient()
      this.bindEvents()
      this.state = status('STARTING', false, null, 'Vinculación borrada. Preparando un nuevo código QR…')
      void this.initialize().catch((error) => console.error(error.message))
      return this.currentStatus()
    } catch (error) {
      this.state = status('FAILED', false, null, 'No fue posible borrar la vinculación de WhatsApp. Intenta nuevamente.')
      throw error
    } finally {
      this.resetting = false
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
    this.sending++
    try {
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
    } finally {
      this.sending--
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
