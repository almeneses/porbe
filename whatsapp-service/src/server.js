'use strict'

const { createApp } = require('./app')
const { WhatsAppGateway } = require('./whatsapp-gateway')

const port = Number.parseInt(process.env.PORT || '3001', 10)
const gateway = new WhatsAppGateway({
  sessionPath: process.env.WHATSAPP_SESSION_PATH || '/app/data/auth',
  clientId: process.env.WHATSAPP_CLIENT_ID || 'porbe',
  executablePath: process.env.PUPPETEER_EXECUTABLE_PATH,
})
const app = createApp({ gateway, internalToken: process.env.WHATSAPP_INTERNAL_TOKEN })
const server = app.listen(port, '0.0.0.0', () => {
  console.log(`Servicio de WhatsApp disponible en el puerto interno ${port}.`)
  gateway.initialize().catch((error) => console.error(error.message))
})

/** Cierra Chromium y el servidor HTTP para conservar íntegra la sesión del volumen. */
async function shutdown() {
  server.close()
  try {
    await gateway.destroy()
  } finally {
    process.exit(0)
  }
}

process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)
