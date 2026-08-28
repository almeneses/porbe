/** Configuración de i18n preparada para incorporar otros idiomas más adelante. */
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import common from './locales/es/common.json'

void i18n.use(initReactI18next).init({
  resources: {
    es: { common },
  },
  lng: 'es',
  fallbackLng: 'es',
  defaultNS: 'common',
  interpolation: {
    escapeValue: false,
  },
})

export default i18n
