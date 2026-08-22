import type { Strings } from './types'

export const en: Strings = {
  meta: { localeName: 'English', htmlLang: 'en' },

  app: {
    name: 'Schirmziit',
    tagline: 'See screen time without being sneaky',
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
    todayHeading: 'Today',
    totalToday: 'Screen time today',
    unlocks: 'times unlocked',
    firstActivity: 'First used',
    lastActivity: 'Last used',
    noDataToday: 'Nothing reported today.',
    noDataHint:
      'That can mean the phone was not used — or that it has not reported yet. Below you can see when it last did.',
    ribbonTitle: 'The shape of the day',
    ribbonHelp:
      'Each cell is one hour, midnight to midnight. Darker means the screen was on longer. So you see not just how much, but when.',
    ribbonQuiet: 'quiet',
    ribbonBusy: 'busy',
    ribbonNight: 'night',
    appsTitle: 'Apps',
    appsHelp: 'How long each app was in the foreground, added up across all of this child’s devices.',
    appColumn: 'App',
    timeColumn: 'Time',
    openCountColumn: 'Times opened',
    otherApps: 'Other apps',
    tableView: 'As a table',
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
      'The app is visible, has an icon, and shows a permanent notice that screen time is being reported. Its own screen says, in the same words, what is sent and what is not. If you want to read along in secret, this is the wrong tool.',
    stopTitle: 'Stopping',
    stop:
      'Disconnect the device here and the server stops accepting data from it. Or uninstall the app on the phone. Either takes effect immediately.',
    notAControlTitle: 'What Schirmziit is not',
    notAControl:
      'Schirmziit blocks nothing and filters nothing. It is a basis for a conversation, not a remote control. Time limits and blocking are deliberately a later, separate step.',
  },

  errors: {
    generic: 'Something went wrong.',
    notFound: 'Not found.',
    offline: 'No connection to the server.',
  },

  units: { hoursShort: 'h', minutesShort: 'min' },
}
