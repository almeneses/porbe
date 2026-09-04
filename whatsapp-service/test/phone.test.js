'use strict'

const assert = require('node:assert/strict')
const test = require('node:test')
const { normalizePhoneNumber } = require('../src/phone')

test('normaliza un número internacional legible', () => {
  assert.equal(normalizePhoneNumber('+57 300 123 4567'), '573001234567')
})

test('rechaza letras y números incompletos', () => {
  assert.throws(() => normalizePhoneNumber('57 ABC 123'), /caracteres no permitidos/)
  assert.throws(() => normalizePhoneNumber('1234'), /8 a 15 dígitos/)
})
