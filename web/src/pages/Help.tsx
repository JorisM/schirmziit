import { useI18n } from '../i18n'

/**
 * One page that answers "what does this thing actually do to my child's phone".
 * The two lists are deliberately adjacent: what it measures next to what it
 * cannot see, because a promise is only credible beside its limits.
 */
export function Help() {
  const { t } = useI18n()

  return (
    <article className="flex flex-col gap-8">
      <header>
        <h1 className="text-3xl">{t.help.title}</h1>
        <p className="mt-2 max-w-prose text-lg" style={{ color: 'var(--ink-muted)' }}>
          {t.help.intro}
        </p>
      </header>

      <div className="grid gap-4 sm:grid-cols-2">
        <section className="card p-5">
          <h2 className="text-lg" style={{ color: 'var(--ok)' }}>
            {t.help.measuresTitle}
          </h2>
          <ul className="mt-3 flex flex-col gap-2">
            {t.help.measures.map((line) => (
              <li key={line} className="flex gap-2">
                <span aria-hidden="true" style={{ color: 'var(--ok)' }}>
                  ✓
                </span>
                <span>{line}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="card p-5">
          <h2 className="text-lg" style={{ color: 'var(--urgent)' }}>
            {t.help.notCollectedTitle}
          </h2>
          <ul className="mt-3 flex flex-col gap-2">
            {t.help.notCollected.map((line) => (
              <li key={line} className="flex gap-2">
                <span aria-hidden="true" style={{ color: 'var(--urgent)' }}>
                  ✕
                </span>
                <span>{line}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section>
        <h2 className="text-xl">{t.help.howTitle}</h2>
        <ol className="mt-3 flex flex-col gap-3">
          {t.help.howSteps.map((step, index) => (
            <li key={step} className="flex gap-3">
              {/* Numbered because these really are sequential: phone, then
                  network, then server. */}
              <span
                aria-hidden="true"
                className="tabular font-mono text-sm"
                style={{ color: 'var(--ink-faint)' }}
              >
                {String(index + 1).padStart(2, '0')}
              </span>
              <span className="max-w-prose">{step}</span>
            </li>
          ))}
        </ol>
      </section>

      {(
        [
          [t.help.whereTitle, t.help.where],
          [t.help.retentionTitle, t.help.retention],
          [t.help.childSeesTitle, t.help.childSees],
          [t.help.stopTitle, t.help.stop],
          [t.help.notAControlTitle, t.help.notAControl],
        ] as const
      ).map(([title, body]) => (
        <section key={title}>
          <h2 className="text-xl">{title}</h2>
          <p className="mt-2 max-w-prose" style={{ color: 'var(--ink-muted)' }}>
            {body}
          </p>
        </section>
      ))}

      {/* Numbers do not tell a parent what to do about them. These four are the
          same Swiss sources the public site links, so both agree. */}
      <section>
        <h2 className="text-xl">{t.help.resourcesTitle}</h2>
        <p className="mt-2 max-w-prose" style={{ color: 'var(--ink-muted)' }}>
          {t.help.resourcesLead}
        </p>
        <ul className="mt-4 space-y-3">
          {t.help.resources.map((resource) => (
            <li key={resource.href}>
              <a
                className="underline underline-offset-2"
                href={resource.href}
                target="_blank"
                rel="noreferrer noopener"
                style={{ color: 'var(--accent)' }}
              >
                {resource.name}
              </a>
              <p className="max-w-prose text-sm" style={{ color: 'var(--ink-muted)' }}>
                {resource.note}
              </p>
            </li>
          ))}
        </ul>
      </section>
    </article>
  )
}
