import { defineConfig } from 'astro/config'

// German is the default and unprefixed: this is a Swiss product first. The other
// three get a prefix, and every page links to its siblings with hreflang.
export default defineConfig({
  site: 'https://docs.schirmziit.jorisda.ch',
  i18n: {
    defaultLocale: 'de',
    locales: ['de', 'fr', 'it', 'en'],
    routing: { prefixDefaultLocale: false },
  },
  build: { format: 'file' },
})
