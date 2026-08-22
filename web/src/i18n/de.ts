import type { Strings } from './types'

/** Schweizer Hochdeutsch: kein ß, du-Form. */
export const de: Strings = {
  meta: { localeName: 'Deutsch', htmlLang: 'de-CH' },

  app: {
    name: 'Schirmziit',
    tagline: 'Bildschirmzeit sehen, ohne heimlich zu sein',
    help: 'Wie funktioniert das?',
    signOut: 'Abmelden',
    language: 'Sprache',
  },

  login: {
    heading: 'Anmelden',
    intro: 'Schirmziit läuft auf deinem eigenen Server. Es gibt kein Konto bei einer Firma.',
    email: 'E-Mail',
    password: 'Passwort',
    submit: 'Anmelden',
    working: 'Einen Moment…',
    wrongCredentials: 'E-Mail oder Passwort stimmt nicht.',
    unexpected: 'Das hat nicht funktioniert. Versuch es nochmals.',
  },

  children: {
    heading: 'Kinder',
    empty: 'Noch kein Kind angelegt.',
    emptyHint: 'Lege ein Kind an, dann verbindest du dessen Handy damit.',
    add: 'Kind hinzufügen',
    addPlaceholder: 'Name, z. B. Lena',
    todayTotal: 'heute',
    openChild: 'Details ansehen',
  },

  child: {
    todayHeading: 'Heute',
    totalToday: 'Bildschirmzeit heute',
    unlocks: 'Mal entsperrt',
    firstActivity: 'Zuerst benutzt',
    lastActivity: 'Zuletzt benutzt',
    noDataToday: 'Heute noch nichts gemeldet.',
    noDataHint:
      'Das kann heissen: das Handy wurde nicht benutzt — oder es hat noch nicht gemeldet. Unten steht, wann es sich zuletzt gemeldet hat.',
    ribbonTitle: 'Der Tag im Verlauf',
    ribbonHelp:
      'Jedes Feld ist eine Stunde, von Mitternacht bis Mitternacht. Je dunkler, desto länger war der Bildschirm an. So siehst du nicht nur wie viel, sondern wann.',
    ribbonQuiet: 'ruhig',
    ribbonBusy: 'viel',
    ribbonNight: 'Nacht',
    appsTitle: 'Apps',
    appsHelp: 'Wie lange jede App im Vordergrund war. Zusammengezählt über alle Geräte des Kindes.',
    appColumn: 'App',
    timeColumn: 'Zeit',
    openCountColumn: 'Mal geöffnet',
    otherApps: 'Weitere Apps',
    tableView: 'Als Tabelle',
  },

  devices: {
    title: 'Geräte',
    fresh: 'meldet sich',
    stale: 'meldet sich nicht',
    staleHelp:
      'Seit über 90 Minuten keine Meldung. Solange das so ist, sind die Zahlen oben unvollständig — nicht unbedingt tief.',
    neverReported: 'hat sich noch nie gemeldet',
    lastSeen: 'Zuletzt gemeldet',
    revoke: 'Verbindung trennen',
    revoked: 'getrennt',
    addDevice: 'Handy verbinden',
    pairTitle: 'Handy verbinden',
    pairStep1: 'Öffne Schirmziit auf dem Handy des Kindes.',
    pairStep2: 'Scanne diesen Code — oder tippe die acht Zeichen ein.',
    pairStep3: 'Fertig. Das Handy meldet sich danach etwa alle 30 Minuten.',
    codeExpires: 'Gültig bis',
    codeLabel: 'Code',
  },

  help: {
    title: 'Wie Schirmziit funktioniert',
    intro:
      'Schirmziit zeigt dir, wie lange und wann das Handy deines Kindes benutzt wird. Nichts davon ist geheim: das Kind sieht dieselben Zahlen auf seinem Handy.',
    measuresTitle: 'Was Schirmziit misst',
    measures: [
      'Welche App im Vordergrund war und wie lange — pro Stunde.',
      'Wie oft das Handy entsperrt wurde.',
      'Wann am Tag das Handy benutzt wurde.',
    ],
    notCollectedTitle: 'Was Schirmziit nicht sammelt',
    notCollected: [
      'Keine Inhalte: keine Nachrichten, Chats, Suchbegriffe, Fotos oder Tastatureingaben.',
      'Keinen Standort.',
      'Keine Webseiten und keine Videos, die angeschaut wurden.',
      'Keine Mikrofon- oder Kameraaufnahmen.',
      'Nichts, was das Handy blockiert — Schirmziit schaltet keine App ab.',
    ],
    howTitle: 'Wie es technisch läuft',
    howSteps: [
      'Auf dem Handy des Kindes läuft eine kleine App. Diese App liest die Nutzungsstatistik, die Android ohnehin führt.',
      'Etwa alle 30 Minuten rechnet sie daraus Stundenwerte und schickt sie an deinen Server.',
      'Ohne Internet wird nichts verworfen: die Werte warten auf dem Handy und gehen später raus.',
      'Dein Server rechnet nichts dazu — er speichert, was ankommt, und zeigt es hier.',
    ],
    whereTitle: 'Wo die Daten liegen',
    where:
      'Auf deinem eigenen Server, in deiner eigenen Datenbank. Es gibt keine Firma dazwischen, kein Konto bei einem Anbieter, keine Weitergabe an Dritte.',
    retentionTitle: 'Wie lange',
    retention:
      'Stundenwerte bleiben 13 Monate, danach nur noch Tagessummen. Du kannst alle Daten eines Kindes jederzeit löschen — dann sind sie weg, nicht archiviert.',
    childSeesTitle: 'Was das Kind sieht',
    childSees:
      'Die App ist sichtbar, hat ein Icon und zeigt dauerhaft eine Meldung, dass Bildschirmzeit übermittelt wird. Auf ihrem Bildschirm steht in derselben Sprache, was übermittelt wird und was nicht. Wer heimlich mitlesen will, ist hier falsch.',
    stopTitle: 'Aufhören',
    stop:
      'Trenne das Gerät hier in der Übersicht — dann nimmt der Server keine Daten mehr davon an. Oder deinstalliere die App auf dem Handy. Beides wirkt sofort.',
    notAControlTitle: 'Was Schirmziit nicht ist',
    notAControl:
      'Schirmziit sperrt nichts und filtert nichts. Es ist eine Grundlage für ein Gespräch, keine Fernsteuerung. Zeitlimiten und Sperren sind bewusst ein späterer, getrennter Schritt.',
  },

  errors: {
    generic: 'Da ist etwas schiefgelaufen.',
    notFound: 'Nicht gefunden.',
    offline: 'Keine Verbindung zum Server.',
  },

  units: { hoursShort: 'h', minutesShort: 'min' },
}
