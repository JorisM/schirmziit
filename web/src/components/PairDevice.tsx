import { useState } from 'react'
import { api, AppError } from '../api/client'
import { unexpected } from '../api/errors'
import type { components } from '../api/schema'
import { ErrorPanel } from './ErrorPanel'
import { QrCode } from './QrCode'
import { useI18n } from '../i18n'

type Enrollment = components['schemas']['EnrollmentResponse']

/**
 * Mints the one-shot code a child's phone is enrolled with.
 *
 * Deliberately not minted on render: a code lives fifteen minutes and can be
 * claimed once, so a page that mints on arrival hands out a code nobody asked
 * for and burns it. The parent presses, then reads six characters aloud.
 *
 * The deep link (`schirmziit://enroll?url=…&code=…`) carries the server address
 * as well as the code, which is why the address is shown next to it: a phone
 * enrolled against the wrong host enrols exactly once and then never reports,
 * the failure this dashboard exists to make visible.
 */
/**
 * The address out of the deep link a parent has to type. Parsing it out beats
 * showing the whole scheme, which is meant for a camera, not for a person — and
 * an unparseable payload falls back to the raw string rather than throwing a
 * render away.
 */
function serverFrom(payload: string): string {
  try {
    return new URL(payload.replace('schirmziit://', 'https://')).searchParams.get('url') ?? payload
  } catch {
    return payload
  }
}

export function PairDevice({ childId }: { childId: string }) {
  const { t, locale } = useI18n()
  const [code, setCode] = useState<Enrollment | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<AppError | null>(null)

  const endpoint = `/v1/children/${childId}/enrollments`

  async function mint() {
    setBusy(true)
    setError(null)
    try {
      setCode(await api.post<Enrollment>(endpoint))
    } catch (caught) {
      // The panel always gets a code and a reference: "creating a code failed"
      // with nothing to quote is a support mail nobody can answer.
      setError(caught instanceof AppError ? caught : unexpected(caught, { endpoint }))
    } finally {
      setBusy(false)
    }
  }

  const expired = code ? Date.parse(code.expires_at) <= Date.now() : false
  const server = code ? serverFrom(code.qr_payload) : ''

  return (
    <section aria-labelledby="pair-heading" className="flex flex-col gap-3">
      <h3 id="pair-heading" className="text-lg">
        {t.devices.pairTitle}
      </h3>

      {error && <ErrorPanel error={error} onRetry={() => void mint()} />}

      {code && (
        <div
          className="animate-[rise-in_var(--motion-base)_var(--ease-out)] flex flex-col gap-3 rounded-[12px] p-4"
          style={{ background: 'var(--sunken)' }}
        >
          <ol className="flex flex-col gap-1 text-sm" style={{ color: 'var(--ink-muted)' }}>
            <li>{t.devices.pairStep1}</li>
            <li>{t.devices.pairStep2}</li>
            <li>{t.devices.pairStep3}</li>
          </ol>

          {/* Only when the server drew one. The code and the address below are
              the whole pairing on their own — they were, before this square
              existed — so a matrix the server could not build costs a scan and
              nothing else. */}
          {code.qr && <QrCode matrix={code.qr} label={t.devices.pairQrAlt} />}

          <div>
            <p className="text-sm" style={{ color: 'var(--ink-faint)' }}>
              {t.devices.codeLabel}
            </p>
            {/* Tracked wide and monospaced: these six characters get read out
                loud and typed on a phone, one character at a time. */}
            <p className="font-display tabular text-4xl leading-none tracking-[0.12em]">{code.code}</p>
          </div>

          <div>
            <p className="text-sm" style={{ color: 'var(--ink-faint)' }}>
              {t.devices.pairServerLabel}
            </p>
            <p className="break-all text-base">{server}</p>
            <p className="mt-1 max-w-prose text-sm" style={{ color: 'var(--ink-muted)' }}>
              {t.devices.pairServerHint}
            </p>
          </div>

          {expired ? (
            // Not a styling variant of the same line: an expired code shown as
            // usable sends a parent to a phone that will refuse it.
            <p role="alert" className="text-sm font-medium" style={{ color: 'var(--urgent)' }}>
              {t.devices.pairExpired}
            </p>
          ) : (
            <p className="text-sm" style={{ color: 'var(--ink-muted)' }}>
              {t.devices.codeExpires}: {new Date(code.expires_at).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })}
            </p>
          )}
        </div>
      )}

      <div className="flex">
        <button
          type="button"
          onClick={() => void mint()}
          disabled={busy}
          className="rounded-[12px] px-4 py-2 font-medium transition-transform duration-[var(--motion-fast)] ease-[var(--ease-out)] active:scale-95 disabled:opacity-50"
          style={{ background: 'var(--accent)', color: 'var(--card)' }}
        >
          {busy ? t.devices.pairWorking : code ? t.devices.pairNewCode : t.devices.pairCreateCode}
        </button>
      </div>
    </section>
  )
}
