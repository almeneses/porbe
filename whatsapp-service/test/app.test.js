'use strict'

const assert = require('node:assert/strict')
const { afterEach, beforeEach, test } = require('node:test')
const { createApp } = require('../src/app')

const TOKEN = 'test-internal-token'
const PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB'
let server
let baseUrl
let sentRequest

beforeEach(async () => {
  sentRequest = null
  const gateway = {
    currentStatus: () => ({ state: 'READY', ready: true }),
    sendReport: async (request) => {
      sentRequest = request
      return { status: 'SENT', messageId: 'message-1' }
    },
  }
  server = createApp({ gateway, internalToken: TOKEN }).listen(0, '127.0.0.1')
  await new Promise((resolve) => server.once('listening', resolve))
  baseUrl = `http://127.0.0.1:${server.address().port}`
})

afterEach(async () => {
  server.closeAllConnections()
  await new Promise((resolve) => server.close(resolve))
})

test('expone salud sin revelar el estado de la sesión', async () => {
  const response = await fetch(`${baseUrl}/health`)
  assert.equal(response.status, 200)
  assert.deepEqual(await response.json(), { status: 'UP' })
})

test('protege los endpoints internos con token', async () => {
  const response = await fetch(`${baseUrl}/api/status`)
  assert.equal(response.status, 401)
})

test('envía un informe PNG cuando la solicitud es válida', async () => {
  const request = { to: '+57 300 123 4567', caption: 'Informe de prueba', filename: 'informe.png', mediaBase64: PNG }
  const response = await fetch(`${baseUrl}/api/messages/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Porbe-Internal-Token': TOKEN },
    body: JSON.stringify(request),
  })

  assert.equal(response.status, 201)
  assert.equal((await response.json()).status, 'SENT')
  assert.deepEqual(sentRequest, request)
})

test('rechaza archivos que no sean PNG', async () => {
  const response = await fetch(`${baseUrl}/api/messages/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Porbe-Internal-Token': TOKEN },
    body: JSON.stringify({ to: '573001234567', caption: 'Informe', filename: 'informe.png', mediaBase64: 'SG9sYQ==' }),
  })

  assert.equal(response.status, 422)
  assert.equal((await response.json()).code, 'INVALID_IMAGE')
})
