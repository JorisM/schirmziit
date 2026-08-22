import { localeOrder, locales, useI18n } from '../i18n'

/** Four languages, spelled in their own language. */
export function LocaleSwitcher() {
  const { locale, setLocale, t } = useI18n()
  return (
    <label className="flex items-center gap-2 text-sm" style={{ color: 'var(--ink-muted)' }}>
      <span className="sr-only">{t.app.language}</span>
      <select
        value={locale}
        onChange={(event) => setLocale(event.target.value as typeof locale)}
        className="rounded-[10px] border px-2 py-1"
        style={{ borderColor: 'var(--hairline)', background: 'var(--card)', color: 'var(--ink)' }}
      >
        {localeOrder.map((code) => (
          <option key={code} value={code}>
            {locales[code].meta.localeName}
          </option>
        ))}
      </select>
    </label>
  )
}
