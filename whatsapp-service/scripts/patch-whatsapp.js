'use strict'

const { readFileSync, writeFileSync } = require('node:fs')

// Retirar al actualizar a una versión que incluya wwebjs/whatsapp-web.js#201923.
function patchMediaMessage(source) {
  const anchor = '            ...botOptions,\n            ...extraOptions,\n        };\n'
  if (source.split(anchor).length !== 2) {
    throw new Error('Cambió el código de whatsapp-web.js; revisar el parche de imágenes.')
  }
  if (source.includes('delete message.__x_id;')) return source
  return source.replace(anchor, anchor + '\n        delete message.__x_id;\n')
}

if (require.main === module) {
  if (require('whatsapp-web.js/package.json').version !== '1.34.7') {
    throw new Error('Revisar el parche de imágenes al actualizar whatsapp-web.js.')
  }
  const file = require.resolve('whatsapp-web.js/src/util/Injected/Utils.js')
  const source = readFileSync(file, 'utf8')
  const patched = patchMediaMessage(source)
  if (patched !== source) writeFileSync(file, patched)
  console.log('Parche de imágenes de WhatsApp verificado.')
}

module.exports = { patchMediaMessage }
