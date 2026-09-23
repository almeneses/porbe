'use strict'

const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { test } = require('node:test')
const { runInNewContext } = require('node:vm')
const { patchMediaMessage } = require('../scripts/patch-whatsapp')

test('el parche preserva el ID del mensaje y los datos de envío de imágenes', () => {
  const source = readFileSync(require.resolve('whatsapp-web.js/src/util/Injected/Utils.js'), 'utf8')
    .replace(/^[ \t]*delete message\.__x_id;\r?\n/gm, '')
  const patched = patchMediaMessage(source)
  const context = Object.fromEntries([
    'options', 'ephemeralFields', 'quotedMsgOptions', 'locationOptions', 'pollOptions',
    'eventOptions', 'vcardOptions', 'buttonOptions', 'listOptions', 'botOptions', 'extraOptions',
  ].map((key) => [key, {}]))
  Object.assign(context, {
    newMsgKey: { id: 'message-id' }, content: '', from: 'sender', chat: { id: 'recipient' },
    mediaOptions: { __x_id: undefined, mediaHandle: 'upload', toJSON: () => ({ mimetype: 'image/png' }) },
  })
  const constructMessage = (code) => runInNewContext(
    code.slice(code.indexOf('const message = {'), code.indexOf("// Bot's won't reply")) + '\nmessage',
    { ...context },
  )
  assert.equal(Object.hasOwn(constructMessage(source), '__x_id'), true)
  const message = constructMessage(patched)
  assert.equal(Object.hasOwn(message, '__x_id'), false)
  assert.equal(message.id.id, 'message-id')
  assert.equal(message.mediaHandle, 'upload')
  assert.equal(message.mimetype, 'image/png')
  assert.equal(patchMediaMessage(patched), patched)
  assert.throws(() => patchMediaMessage('unexpected source'), /Cambió el código/)
})
