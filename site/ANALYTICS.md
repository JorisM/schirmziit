# Analytics for the site

Self-hosted [Umami](https://umami.is) at `https://umami.jorisda.ch`, counting
`www.schirmziit.ch`. No cookies, no cross-site identifiers, no third party — the
whole reason for self-hosting it rather than pasting someone else's script into a
site whose selling point is that it does not spy on children.

The tracker is injected only when `PUBLIC_UMAMI_HOST` **and** `PUBLIC_UMAMI_ID`
are set at build time (`site/Dockerfile` args, passed from `bin/deploy-k8s.nu` in
the home-network repo). A fork of this repo builds with neither and ships no
analytics at all. Every custom event goes through `window.szTrack`, which only
exists when the tracker was injected, and every call site uses `?.` — so the
guard holds without a second flag.

Website id: `9e689352-32b0-44f4-a5ac-0225760b899b`.

## Events

Page views are automatic. These are the custom events, all defined in
`src/layouts/Base.astro` (site-wide, delegated from one click listener) and
`src/components/Waitlist.astro` (the form).

| Event | Fires when | Data |
| --- | --- | --- |
| `site-view` | every page load | `page`, `locale` |
| `waitlist-view` | the waiting-list section is 50% visible, once per load | `locale` |
| `waitlist-submit` | the form is submitted, **before** the request | `locale` |
| `waitlist-joined` | the server accepted the address | `locale` |
| `waitlist-error` | it did not | `locale`, `reason` (`invalid` \| `rejected` \| `unreachable`) |
| `banner-cta` | the private-alpha banner's call to action is clicked | `page`, `locale` |
| `lang-switch` | a *different* language is picked in the switcher | `from`, `to` |
| `source-click` | the footer's GitHub link | `page`, `locale` |
| `contact-click` | the footer's mail link | `page`, `locale` |

Two of these look redundant and are not:

- **`site-view` next to the automatic page view.** A funnel needs a first step
  that is the same string for `/`, `/en/`, `/fr/` and `/it/`; a URL step is not.
  It also carries `page` and `locale` as event data, which a raw path does not.
- **`waitlist-view` next to `site-view`.** The form sits below the fold on every
  page, so a page view says nothing about whether anyone reached it. The gap
  between the two is the honest measure of how far the page carries a reader.

`waitlist-submit` fires before the request on purpose: `submit` minus `joined` is
then the failure rate, including the failures the server never saw.

## Saved reports

Thirteen, all on the site's website id, all verified to run. Each stores a
rolling 30-day range that the UI can override per view.

**Funnels**

- *Waiting list — full funnel* — `site-view` → `waitlist-view` → `waitlist-submit` → `waitlist-joined`, 30-minute window.
- *Waiting list — form only* — the last three steps. Isolates form friction from reach: a wide gap here is the form's fault, not the page's.
- *Alpha banner → signup* — whether the banner actually sends people to the form.
- *Self-host interest* — `site-view` → `source-click`. The other outcome an alpha reader can have.

**Goals** — *Private-alpha targets*: absolute counts, not rates (at this volume a
percentage moves on single visits and reads as noise). 50 signups, 75 submits,
400 form views, 1500 page loads, 50 repo clicks, 200 views of `/hosted`.

**Retention** — *Returning readers*. Pre-launch this measures interest, not
product stickiness: someone who comes back to a page about a product they cannot
use yet is a real signal. Its parameters carry `timezone: Europe/Zurich`, which
the retention endpoint requires and the others do not.

**Journeys** — *Path to a signup* (five steps ending at `waitlist-joined`) and
*First five steps* (unfiltered — the paths the funnel never asked about).

**Attribution** — *Signups — first click* and *Signups — last click*, both on
`waitlist-joined`. Read together: a large gap means the middle of the funnel is
doing the work.

**Insights** — *Where readers come from* (referrer × country) and *Which pages
get read* (URL × language; the four locales are separate pages here, which is the
only way to see whether the translations earn their keep).

**UTM** — *Campaign traffic*. Empty until links carry tags. See below.

## UTM convention

Umami parses `utm_*` off the query string into its own columns automatically —
nothing to configure, but nothing to see either until outbound links carry them.

| Parameter | Means | Examples |
| --- | --- | --- |
| `utm_source` | the specific place | `hackernews`, `reddit-r-de`, `linkedin`, `elternforum`, `newsletter` |
| `utm_medium` | the kind of place | `social`, `email`, `referral`, `qr`, `paid` |
| `utm_campaign` | the push | `private-alpha` |
| `utm_content` | which variant, when there is more than one | `comment`, `bio`, `poster-a` |
| `utm_term` | paid search keyword only | rarely used here |

Rules that keep the reports readable:

- Lowercase, hyphens, never spaces. `Reddit` and `reddit` are two rows.
- Tag **outbound** links only. Putting UTMs on a link between two pages of this
  site restarts attribution and the campaign gets credit for a visit it did not
  cause.
- Land on a canonical URL with the locale prefix — `/en/`, `/fr/`, `/it/`, or `/`
  for German. A tagged link to a redirect loses the tags on some clients.
- Keep `utm_campaign` stable for the whole push. Renaming it mid-flight splits
  one campaign into two rows that cannot be added back together.

Ready to paste:

```
https://www.schirmziit.ch/?utm_source=hackernews&utm_medium=social&utm_campaign=private-alpha
https://www.schirmziit.ch/en/?utm_source=reddit-r-parenting&utm_medium=social&utm_campaign=private-alpha&utm_content=comment
https://www.schirmziit.ch/fr/?utm_source=linkedin&utm_medium=social&utm_campaign=private-alpha&utm_content=bio
https://www.schirmziit.ch/?utm_source=flyer-schule&utm_medium=qr&utm_campaign=private-alpha
```

Click ids (`gclid`, `fbclid`, `li_fat_id`, …) are captured too, so a paid click
is attributable even when the UTMs get stripped.

## What is not measured

No revenue report: there is nothing to sell yet. Add one when the hosted plan
takes money, and pass `currency` in its parameters.

The dashboard website (`app.schirmziit.ch`,
`38201809-ac57-4ecd-9aad-00bf308c3682`) exists in Umami but the SPA carries no
tracker, so it counts nothing. That is deliberate for now — the dashboard shows a
family's own data and instrumenting it is a privacy decision, not a technical
one.
