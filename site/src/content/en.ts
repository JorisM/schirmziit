import type { Site } from './strings'

export const en: Site = {
  htmlLang: 'en',
  swissLabel: 'Made in Switzerland',
  nav: { home: 'Overview', selfHost: 'Self-hosting', hosted: 'Hosted', privacy: 'Privacy' },

  alpha: {
    bannerTitle: 'Private alpha',
    bannerBody:
      'Schirmziit is under heavy development and not released yet. What you read here describes what already works — not something to set up for your family today.',
    bannerCta: 'Join the waiting list',
    title: 'Waiting list',
    lead:
      'Leave your email address and you will hear from us once Schirmziit is released. One mail, then nothing — no newsletter.',
    emailLabel: 'Email address',
    placeholder: 'you@example.ch',
    submit: 'Join',
    sending: 'Signing up …',
    done: 'You are on the list. We will write once it is out.',
    invalid: 'That does not look like an email address.',
    failed: 'That did not work. Try again later, or send us a mail.',
    stored:
      'We store the address and the language of this page, nothing else. No tracking, no sharing. Write to us and the entry is gone.',
    mailFallback:
      'The form needs JavaScript. Send us a short mail instead — that works just as well.',
    mailCta: 'Send a mail',
  },

  home: {
    kicker: 'Screen time for families',
    title: 'Screen time in view, to protect your child',
    lead:
      'Schirmziit shows how long and at what time of day your child’s phone is used, so you notice when it becomes too much and can talk about it. No content, no location, no remote control.',
    ctaSelfHost: 'Self-host it',
    ctaHosted: 'Hosted version',

    measuresTitle: 'What Schirmziit measures',
    measures: [
      'Which app was in the foreground and for how long — per hour.',
      'How often the phone was unlocked.',
      'What time of day the phone was used.',
    ],
    neverTitle: 'What Schirmziit never collects',
    never: [
      'No messages, chats, searches, photos or keystrokes.',
      'No location.',
      'No websites and no videos that were watched.',
      'No microphone or camera recordings.',
      'Nothing is blocked — Schirmziit never switches an app off.',
    ],

    howTitle: 'How it works',
    how: [
      'A small app runs on the child’s phone. It reads the usage statistics Android keeps anyway.',
      'About every 30 minutes it sends hourly figures to your server — with no internet the figures wait on the phone.',
      'You look at them in the browser, or in the iPhone app.',
    ],

    ribbonTitle: 'Not just how much, but when',
    ribbonBody:
      'An hour at 11pm means something different from an hour after lunch. So Schirmziit draws the day as a ribbon from midnight to midnight — you read the shape of the day at a glance.',

    childSeesTitle: 'The child sees the same numbers',
    childSeesBody:
      'Your dashboard shows the last 14 days at a glance; tap any one of them to see it hour by hour. The app on the child’s phone shows exactly the same — the same 14 days, the same day in detail, the same numbers. It stays a shared basis for a conversation rather than a check running in the background.',

    platformsTitle: 'Devices',
    platformsBody: 'What works today — and what does not.',
    androidLabel: 'Android',
    androidBody:
      'Complete: per-app time per hour, unlocks, the shape of the day. Android 8 and newer.',
    iosLabel: 'iPhone',
    iosBody:
      'Both roles work on iPhone now — a dashboard for parents, and now a view for the child too. Measuring screen time directly on an iPhone still needs an Apple entitlement we do not have — for now, only Android can be the phone being measured. We will say so here the moment that changes.',

    openTitle: 'Free and checkable',
    openBody:
      'Schirmziit is open source. You can read what gets sent, and host it yourself — including if we ever stop.',
  },

  choose: {
    title: 'Two ways',
    selfHostTitle: 'Self-hosting',
    selfHostFor: 'For you if you already run a server or a Raspberry Pi.',
    selfHostPoints: [
      'Two containers: Schirmziit and Postgres.',
      'The data stays on your hardware, in your database.',
      'Updates, backups and TLS are yours to handle.',
      'Free, with no account with us.',
    ],
    hostedTitle: 'Hosted (beta)',
    hostedFor: 'For you if you do not want to run a server.',
    hostedPoints: [
      'We run it — for now on our own homelab in Switzerland.',
      'Data sits in Switzerland, not at a hyperscaler.',
      'Free during the beta, with limited places.',
      'You can switch to self-hosting whenever you like; it is the same software.',
    ],
  },

  selfHost: {
    title: 'Self-hosting',
    lead:
      'Schirmziit is one program that serves its own dashboard, plus a Postgres database. No Redis, no message broker, no cloud service.',
    needTitle: 'What you need',
    need: [
      'A machine with Docker — a Raspberry Pi 4 is enough.',
      'An address the child’s phone can reach, with TLS in front (Caddy, Traefik or nginx).',
      'Roughly 200 MB of storage for the first year of data.',
    ],
    stepsTitle: 'Installation',
    proxyTitle: 'Reverse proxy and TLS',
    proxyBody:
      'Schirmziit listens on 127.0.0.1:8080 only. Put a reverse proxy in front to terminate TLS, and set PUBLIC_URL to exactly the address you type in the browser. That address is baked into the pairing QR code: get it wrong and the phone pairs once and then never reports again.',
    firstUserTitle: 'The first account',
    firstUserBody:
      'By default exactly one account may register, and then registration closes. Open the dashboard, create your account, then set ALLOW_REGISTRATION to “off”.',
    pairTitle: 'Connecting a phone',
    pairBody:
      'Add a child in the dashboard and generate a code. Install the Android app on the child’s phone, allow usage access, and scan the code. From then on the phone reports about every 30 minutes.',
    backupTitle: 'Backups',
    backupBody:
      'Everything that matters lives in Postgres. A nightly pg_dump of the volume is enough; the dashboard itself holds no state.',
    upgradeTitle: 'Updates',
    upgradeBody:
      'docker compose pull, then docker compose up -d. Database migrations run on startup. Downgrades are not supported, so take a backup first.',
    troubleTitle: 'When something does not work',
    trouble: [
      {
        problem: 'The phone pairs but never reports.',
        fix: 'PUBLIC_URL does not point at the address the phone can reach. Fix it, restart, and generate a new code.',
      },
      {
        problem: 'The dashboard says “not reporting”.',
        fix: 'Check on the phone that usage access is still granted, and allow background updates if the app asks.',
      },
      {
        problem: 'Postgres will not start after an update.',
        fix: 'From Postgres 18 the volume must be mounted at /var/lib/postgresql, not /var/lib/postgresql/data. Our compose file already does this.',
      },
      {
        problem: 'An app shows up as “com.something.app”.',
        fix: 'The phone could not resolve its name. After the next Android app update and one more report, the real name appears.',
      },
    ],
  },

  hosted: {
    title: 'Hosted version',
    lead: 'You do not want to run a server. We will — small, open and in Switzerland for now.',
    whereTitle: 'Where the data lives',
    whereBody:
      'On our own hardware in Switzerland, not at a large cloud provider. The same software you could host yourself, with the same limits: usage times yes, content no.',
    betaTitle: 'How honestly small this is',
    betaBody:
      'This currently runs on a homelab operated by one person. That is fine for a beta’s worth of families and is not dressed up as a company with an on-call rota. If enough people join, we build it out properly.',
    priceTitle: 'Price',
    priceBody:
      'Free during the beta. Later it will have to cost something to pay for itself — self-hosting stays free either way.',
    joinTitle: 'Joining',
    joinBody:
      'Send us a short mail saying which operating system the child’s phone runs. We will get back to you when a place opens up.',
    joinCta: 'Ask for a beta place',
  },

  privacy: {
    title: 'Privacy',
    lead: 'Short version: hourly usage times, nothing else. No content, no location, no sharing.',
    sections: [
      {
        title: 'What is stored',
        body: 'Per hour and app: foreground time and how often it was opened. Per hour and device: screen-on time and unlocks. Plus the app name the phone reports, and the name you give the child.',
      },
      {
        title: 'What is not stored',
        body: 'No messages, chats, searches, photos, keystrokes, websites, videos, microphone or camera data, and no location. Schirmziit does not even ask for those permissions.',
      },
      {
        title: 'For how long',
        body: 'Hourly figures for 13 months, then daily totals only. You can delete a child’s data at any time — it is gone, not archived.',
      },
      {
        title: 'Who can see it',
        body: 'Your account, and the child on their own phone. When you self-host, nobody else. On the hosted beta, technically also the operator who administers the database — and no one beyond that.',
      },
      {
        title: 'Third parties',
        body: 'None. No analytics on the child’s phone, no advertising SDKs, no crash reporters, no sharing.',
      },
      {
        title: 'Waiting list',
        body: 'If you join the waiting list we store your email address and the language you read the site in. For one purpose only: a mail when Schirmziit is released. No newsletter, no sharing. Write to us and we delete the entry.',
      },
    ],
    analyticsTitle: 'This website',
    analyticsBody:
      'This site counts visits with a self-hosted Umami instance: no cookies, no IP storage, nothing shared with third parties. We only want to know whether anyone is reading.',
  },

  resources: {
    title: 'Help and guidance',
    lead:
      'Schirmziit shows numbers, not advice. How much screen time makes sense, and what helps when it turns into an argument, is better explained by these Swiss organisations:',
    items: [
      {
        name: 'Jugend und Medien',
        note: 'The federal platform for media skills: age guidance, house rules, parent leaflets (DE/FR/IT).',
        href: 'https://www.jugendundmedien.ch/',
      },
      {
        name: 'Pro Juventute — screen time',
        note: 'Concrete guide values per age and tips for agreements within the family.',
        href: 'https://www.projuventute.ch/de/eltern/medien-internet/bildschirmzeit',
      },
      {
        name: 'Counselling 147',
        note: 'Free, round-the-clock counselling for children and teenagers by phone, chat or SMS.',
        href: 'https://www.147.ch/',
      },
      {
        name: 'Zischtig.ch',
        note: 'Swiss specialists in media skills: parents’ evenings, courses, one-to-one advice.',
        href: 'https://www.zischtig.ch/',
      },
    ],
  },

  footer: { madeIn: 'Made in Switzerland', source: 'Source code', contact: 'Contact' },
}
