import type { Strings } from './types'

export const en: Strings = {
  meta: { localeName: 'English', htmlLang: 'en' },

  app: {
    name: 'Schirmziit',
    tagline: 'Screen time in view, to protect your child',
    help: 'How does this work?',
    signOut: 'Sign out',
    language: 'Language',
  },

  login: {
    heading: 'Sign in',
    intro: 'Schirmziit runs on your own server. There is no account with a company.',
    email: 'Email',
    password: 'Password',
    submit: 'Sign in',
    working: 'One moment…',
    wrongCredentials: 'That email or password is not right.',
    unexpected: 'That did not work. Try again.',
  },

  children: {
    heading: 'Children',
    empty: 'No child yet.',
    emptyHint: 'Add a child, then connect their phone to it.',
    add: 'Add a child',
    addPlaceholder: 'Name, e.g. Lena',
    todayTotal: 'today',
    openChild: 'See details',
  },

  child: {
    totalToday: 'Screen time today',
    unlocks: 'times unlocked',
    firstActivity: 'First used',
    lastActivity: 'Last used',
    noDataToday: 'Nothing reported today.',
    noDataDay: 'Nothing reported for this day.',
    noDataHint:
      'That can mean the phone was not used — or that it has not reported yet. Below you can see when it last did.',
    ribbonTitle: 'The shape of the day',
    ribbonHelp:
      'Each cell is one hour, midnight to midnight. Darker means the screen was on longer. So you see not just how much, but when.',
    ribbonQuiet: 'quiet',
    ribbonBusy: 'busy',
    ribbonNight: 'night',
    backgroundTitle: 'Background listening',
    backgroundHelp:
      'Music, podcasts or audiobooks that played while the screen was off. This is counted on its own — it is not screen time and is never added to it.',
    backgroundTotal: 'Listened in the background',
    backgroundNotMeasured:
      'Background listening cannot be measured on this child’s phones. iPhones do not report it, and on Android it needs a setting that has not been turned on.',
    backgroundEmpty: 'Nothing played with the screen off on this day.',
    backgroundHour: 'listened in the background',
    appsTitle: 'Apps',
    appsHelp: 'How long each app was in the foreground, added up across all of this child’s devices.',
    appColumn: 'App',
    timeColumn: 'Time',
    openCountColumn: 'Times opened',
    otherApps: 'Other apps',
    briefApps: 'Apps under a minute',
    tableView: 'As a table',
    historyTitle: 'The last 14 days',
    historyHelp: 'Each bar is one day. Tap a bar to look at that day.',
    historyError: 'Could not load the last 14 days. Try again in a moment.',
    today: 'Today',
    selectedHeading: 'Selected day',
  },

  devices: {
    title: 'Devices',
    fresh: 'reporting',
    stale: 'not reporting',
    staleHelp:
      'Nothing for over 90 minutes. Until it reports again the numbers above are incomplete — not necessarily low.',
    neverReported: 'has never reported',
    lastSeen: 'Last reported',
    revoke: 'Disconnect',
    revoked: 'disconnected',
    addDevice: 'Connect a phone',
    pairTitle: 'Connect a phone',
    pairStep1: 'Open Schirmziit on your child’s phone.',
    pairStep2: 'Scan this code — or type the eight characters.',
    pairStep3: 'Done. The phone then reports about every 30 minutes.',
    codeExpires: 'Valid until',
    codeLabel: 'Code',
  },

  help: {
    title: 'How Schirmziit works',
    intro:
      'Schirmziit shows you how long and when your child’s phone is used. None of it is secret: the child sees the same numbers on their own phone.',
    measuresTitle: 'What Schirmziit measures',
    measures: [
      'Which app was in the foreground and for how long — per hour.',
      'How often the phone was unlocked.',
      'What time of day the phone was used.',
    ],
    notCollectedTitle: 'What Schirmziit does not collect',
    notCollected: [
      'No content: no messages, chats, searches, photos or keystrokes.',
      'No location.',
      'No websites and no videos that were watched.',
      'No microphone or camera recordings.',
      'Nothing that blocks the phone — Schirmziit never switches an app off.',
    ],
    howTitle: 'How it works technically',
    howSteps: [
      'A small app runs on the child’s phone. It reads the usage statistics Android keeps anyway.',
      'About every 30 minutes it turns those into hourly figures and sends them to your server.',
      'With no internet nothing is thrown away: the figures wait on the phone and go out later.',
      'Your server adds nothing of its own — it stores what arrives and shows it here.',
    ],
    whereTitle: 'Where the data lives',
    where:
      'On your own server, in your own database. There is no company in between, no account with a provider, and nothing is passed to third parties.',
    retentionTitle: 'For how long',
    retention:
      'Hourly figures stay 13 months, then only daily totals. You can delete all of a child’s data at any time — it is gone, not archived.',
    childSeesTitle: 'What the child sees',
    childSees:
      'The app is visible, has an icon, and shows a permanent notice that screen time is being reported. Its own screen says, in the same words, what is sent and what is not. Your child can check at any time what was sent.',
    stopTitle: 'Stopping',
    stop:
      'Disconnect the device here and the server stops accepting data from it. Or uninstall the app on the phone. Either takes effect immediately.',
    notAControlTitle: 'What Schirmziit is not',
    notAControl:
      'Schirmziit blocks nothing and filters nothing. It is a basis for a conversation, not a remote control. Time limits and blocking are deliberately a later, separate step.',
    resourcesTitle: 'Help and guidance',
    resourcesLead:
      'Schirmziit shows numbers, not advice. How much screen time makes sense, and what helps when it turns into an argument, is better explained by these Swiss organisations:',
    resources: [
      {
        name: 'Jugend und Medien',
        note: 'The federal platform: age guidance, rules, leaflets for parents.',
        href: 'https://www.jugendundmedien.ch/',
      },
      {
        name: 'Pro Juventute — screen time',
        note: 'Concrete guide values per age and tips for family agreements.',
        href: 'https://www.projuventute.ch/de/eltern/medien-internet/bildschirmzeit',
      },
      {
        name: 'Counselling 147',
        note: 'Free counselling for children and teenagers, around the clock — phone, chat or SMS.',
        href: 'https://www.147.ch/',
      },
      {
        name: 'Zischtig.ch',
        note: 'Swiss centre for media literacy: parent evenings, courses, counselling.',
        href: 'https://www.zischtig.ch/',
      },
    ],
  },

  errorPanel: {
    retry: 'Try again',
    details: 'Details',
    copy: 'Copy details',
    copied: 'Copied',
    reference: 'Error code and reference',
  },

  errors: {
    generic: 'Something went wrong.',
    notFound: 'Not found.',
    offline: 'No connection to the server.',
  },

  units: { hoursShort: 'h', minutesShort: 'min', secondsShort: 's' },
}
