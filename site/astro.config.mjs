import { defineConfig } from 'astro/config'
import sitemap from '@astrojs/sitemap'

// German is the default and unprefixed: this is a Swiss product first. The other
// three get a prefix, and every page links to its siblings with hreflang.
export default defineConfig({
  site: 'https://www.schirmziit.ch',
  i18n: {
    defaultLocale: 'de',
    locales: ['de', 'fr', 'it', 'en'],
    routing: { prefixDefaultLocale: false },
  },
  build: { format: 'file' },
  // The head of every page already carries the hreflang cluster; the sitemap
  // repeats it because that is the copy a crawler reads before it has fetched
  // all sixteen pages. Keys are the URL prefixes, values the hreflang codes.
  integrations: [
    sitemap({
      i18n: {
        defaultLocale: 'de',
        locales: { de: 'de-CH', fr: 'fr-CH', it: 'it-CH', en: 'en' },
      },
    }),
  ],
})
