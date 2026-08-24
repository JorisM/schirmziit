/**
 * Every string the dashboard shows. Each locale file must satisfy this type, so
 * a missing or misspelled key is a compile error rather than an English word
 * appearing in the middle of a German sentence.
 */
export type Strings = {
  meta: { localeName: string; htmlLang: string }

  app: {
    name: string
    tagline: string
    help: string
    signOut: string
    language: string
  }

  login: {
    heading: string
    intro: string
    email: string
    password: string
    submit: string
    working: string
    wrongCredentials: string
    unexpected: string
  }

  children: {
    heading: string
    empty: string
    emptyHint: string
    add: string
    addPlaceholder: string
    todayTotal: string
    openChild: string
  }

  child: {
    todayHeading: string
    totalToday: string
    unlocks: string
    firstActivity: string
    lastActivity: string
    noDataToday: string
    noDataHint: string
    ribbonTitle: string
    ribbonHelp: string
    ribbonQuiet: string
    ribbonBusy: string
    ribbonNight: string
    appsTitle: string
    appsHelp: string
    appColumn: string
    timeColumn: string
    openCountColumn: string
    otherApps: string
    tableView: string
    historyTitle: string
    historyHelp: string
    today: string
    selectedHeading: string
  }

  devices: {
    title: string
    fresh: string
    stale: string
    staleHelp: string
    neverReported: string
    lastSeen: string
    revoke: string
    revoked: string
    addDevice: string
    pairTitle: string
    pairStep1: string
    pairStep2: string
    pairStep3: string
    codeExpires: string
    codeLabel: string
  }

  help: {
    title: string
    intro: string
    measuresTitle: string
    measures: string[]
    notCollectedTitle: string
    notCollected: string[]
    howTitle: string
    howSteps: string[]
    whereTitle: string
    where: string
    retentionTitle: string
    retention: string
    childSeesTitle: string
    childSees: string
    stopTitle: string
    stop: string
    notAControlTitle: string
    notAControl: string
    resourcesTitle: string
    resourcesLead: string
    resources: { name: string; note: string; href: string }[]
  }

  errors: { generic: string; notFound: string; offline: string }

  units: { hoursShort: string; minutesShort: string }
}
