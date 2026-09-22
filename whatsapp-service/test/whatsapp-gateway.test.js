'use strict'

const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const { mkdtemp, mkdir, writeFile, access, rm } = require('node:fs/promises')
const { tmpdir } = require('node:os')
const path = require('node:path')
const { test } = require('node:test')
const { LocalAuth } = require('whatsapp-web.js')
const { WhatsAppGateway } = require('../src/whatsapp-gateway')

async function setup(t) {
  const root = await mkdtemp(path.join(tmpdir(), 'porbe-whatsapp-test-'))
  t.after(() => rm(root, { recursive: true, force: true }))
  const clients = []
  const cachePath = path.join(root, 'cache')
  const gateway = new WhatsAppGateway({
    cachePath,
    createClient: () => {
      const client = new EventEmitter()
      client.options = { puppeteer: {} }
      client.authStrategy = new LocalAuth({ clientId: 'test', dataPath: root })
      client.authStrategy.setup(client)
      client.initialize = async () => {
        await client.authStrategy.beforeBrowserInitialized()
      }
      client.destroy = async () => { client.closed = true }
      client.info = { wid: { user: '573001234567' } }
      clients.push(client)
      return client
    },
  })
  await gateway.initialize()
  gateway.client.emit('ready')
  await mkdir(cachePath)
  await writeFile(path.join(cachePath, 'version.html'), 'old cache')
  await writeFile(path.join(root, 'session-test', 'credentials'), 'old session')
  await writeFile(path.join(root, 'unrelated'), 'keep')
  return { gateway, clients, root, cachePath }
}

test('borra la sesión y caché locales, descarta eventos antiguos y permite vincular y enviar de nuevo', async (t) => {
  const { gateway, clients, root, cachePath } = await setup(t)
  const oldReady = clients[0].listeners('ready')[0]
  clients[0].emit('qr', 'old QR')
  const result = await gateway.resetSession()
  assert.equal(result.ready, false)
  assert.equal(result.accountLabel, null)
  assert.equal(result.qrDataUrl, null)
  assert.equal(clients[0].closed, true)
  assert.equal(clients.length, 2)
  await assert.rejects(access(path.join(root, 'session-test', 'credentials')))
  await assert.rejects(access(cachePath))
  await access(path.join(root, 'unrelated'))
  oldReady()
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(gateway.currentStatus().state, 'STARTING')
  await clients[1].listeners('qr')[0]('new QR')
  assert.equal(gateway.currentStatus().state, 'QR_REQUIRED')
  assert.match(gateway.currentStatus().qrDataUrl, /^data:image\/png;base64,/)
  await assert.rejects(gateway.sendReport({}), { code: 'WHATSAPP_NOT_READY' })
  clients[1].emit('ready')
  clients[1].getNumberId = async () => ({ _serialized: '573001234567@c.us' })
  clients[1].sendMessage = async () => ({ id: { _serialized: 'new-message' } })
  const sent = await gateway.sendReport({ to: '573001234567', mediaBase64: 'abc', filename: 'test.png', caption: 'Test' })
  assert.equal(sent.messageId, 'new-message')
})

test('no borra credenciales si no puede cerrar el navegador y permite reintentar', async (t) => {
  const { gateway, clients, root } = await setup(t)
  clients[0].destroy = async () => { throw new Error('close failed') }
  await assert.rejects(gateway.resetSession(), /close failed/)
  assert.equal(gateway.currentStatus().state, 'FAILED')
  assert.equal(gateway.currentStatus().ready, false)
  await access(path.join(root, 'session-test', 'credentials'))
  clients[0].destroy = async () => {}
  await gateway.resetSession()
  assert.equal(clients.length, 2)
})

test('impide borrar durante un envío y bloquea envíos y borrados simultáneos', async (t) => {
  const { gateway, clients } = await setup(t)
  let finishSend
  clients[0].getNumberId = () => new Promise((resolve) => { finishSend = resolve })
  const send = gateway.sendReport({ to: '573001234567' })
  await assert.rejects(gateway.resetSession(), { code: 'WHATSAPP_BUSY' })
  finishSend(null)
  await assert.rejects(send, { code: 'NUMBER_NOT_REGISTERED' })
  let finishClose
  clients[0].destroy = () => new Promise((resolve) => { finishClose = resolve })
  const reset = gateway.resetSession()
  await assert.rejects(gateway.resetSession(), { code: 'WHATSAPP_BUSY' })
  await assert.rejects(gateway.sendReport({}), { code: 'WHATSAPP_NOT_READY' })
  finishClose()
  await reset
})

test('informa un fallo al reiniciar y libera el bloqueo para reintentar', async (t) => {
  const { gateway } = await setup(t)
  const createClient = gateway.createClient
  gateway.createClient = () => {
    const client = createClient()
    client.initialize = async () => { throw new Error('startup failed') }
    return client
  }
  await gateway.resetSession()
  assert.equal(gateway.currentStatus().state, 'FAILED')
  assert.equal(gateway.initializing, false)
  gateway.createClient = createClient
  await gateway.resetSession()
  assert.equal(gateway.currentStatus().state, 'STARTING')
})
