import type { Strings } from './types'

export const it: Strings = {
  meta: { localeName: 'Italiano', htmlLang: 'it-CH' },

  app: {
    name: 'Schirmziit',
    tagline: 'Il tempo di schermo sotto controllo, per proteggere tuo figlio',
    help: 'Come funziona?',
    signOut: 'Esci',
    language: 'Lingua',
  },

  login: {
    heading: 'Accedi',
    intro: 'Schirmziit gira sul tuo server. Non c’è nessun account presso un’azienda.',
    email: 'E-mail',
    password: 'Password',
    submit: 'Accedi',
    working: 'Un momento…',
    wrongCredentials: 'Questa e-mail o password non è corretta.',
    unexpected: 'Non ha funzionato. Riprova.',
  },

  children: {
    heading: 'Bambini',
    empty: 'Ancora nessun bambino.',
    emptyHint: 'Aggiungi un bambino, poi collega il suo telefono.',
    add: 'Aggiungi un bambino',
    addPlaceholder: 'Nome, p. es. Lena',
    todayTotal: 'oggi',
    openChild: 'Vedi i dettagli',
  },

  child: {
    todayHeading: 'Oggi',
    totalToday: 'Tempo di schermo oggi',
    unlocks: 'sblocci',
    firstActivity: 'Primo utilizzo',
    lastActivity: 'Ultimo utilizzo',
    noDataToday: 'Oggi non è stato segnalato nulla.',
    noDataHint:
      'Può significare che il telefono non è stato usato — oppure che non ha ancora inviato i dati. Sotto vedi quando l’ha fatto l’ultima volta.',
    ribbonTitle: 'La forma della giornata',
    ribbonHelp:
      'Ogni casella è un’ora, da mezzanotte a mezzanotte. Più è scura, più lo schermo è rimasto acceso. Così vedi non solo quanto, ma quando.',
    ribbonQuiet: 'tranquillo',
    ribbonBusy: 'intenso',
    ribbonNight: 'notte',
    appsTitle: 'App',
    appsHelp:
      'Quanto tempo ogni app è rimasta in primo piano, sommato su tutti i dispositivi del bambino.',
    appColumn: 'App',
    timeColumn: 'Tempo',
    openCountColumn: 'Aperture',
    otherApps: 'Altre app',
    tableView: 'Come tabella',
    historyTitle: 'Gli ultimi 14 giorni',
    historyHelp: 'Ogni barra è un giorno. Tocca una barra per vedere quel giorno nel dettaglio.',
    today: 'Oggi',
    selectedHeading: 'Giorno selezionato',
  },

  devices: {
    title: 'Dispositivi',
    fresh: 'invia i dati',
    stale: 'non invia più nulla',
    staleHelp:
      'Nulla da più di 90 minuti. Finché è così, i numeri sopra sono incompleti — non necessariamente bassi.',
    neverReported: 'non ha mai inviato nulla',
    lastSeen: 'Ultimo invio',
    revoke: 'Disconnetti',
    revoked: 'disconnesso',
    addDevice: 'Collega un telefono',
    pairTitle: 'Collega un telefono',
    pairStep1: 'Apri Schirmziit sul telefono del bambino.',
    pairStep2: 'Scansiona questo codice — oppure digita gli otto caratteri.',
    pairStep3: 'Fatto. Il telefono invia poi i dati circa ogni 30 minuti.',
    codeExpires: 'Valido fino a',
    codeLabel: 'Codice',
  },

  help: {
    title: 'Come funziona Schirmziit',
    intro:
      'Schirmziit ti mostra quanto e quando viene usato il telefono di tuo figlio. Niente di tutto questo è segreto: il bambino vede gli stessi numeri sul proprio telefono.',
    measuresTitle: 'Cosa misura Schirmziit',
    measures: [
      'Quale app era in primo piano e per quanto tempo — ora per ora.',
      'Quante volte il telefono è stato sbloccato.',
      'In che momento della giornata è stato usato il telefono.',
    ],
    notCollectedTitle: 'Cosa Schirmziit non raccoglie',
    notCollected: [
      'Nessun contenuto: né messaggi, né chat, né ricerche, né foto, né digitazioni.',
      'Nessuna posizione.',
      'Nessun sito web e nessun video guardato.',
      'Nessuna registrazione del microfono o della fotocamera.',
      'Nulla che blocchi il telefono — Schirmziit non spegne nessuna app.',
    ],
    howTitle: 'Come funziona tecnicamente',
    howSteps: [
      'Sul telefono del bambino gira una piccola app. Legge le statistiche d’uso che Android tiene comunque.',
      'Circa ogni 30 minuti ne calcola valori orari e li invia al tuo server.',
      'Senza internet non si perde nulla: i valori restano sul telefono e partono più tardi.',
      'Il tuo server non aggiunge niente di suo — salva ciò che arriva e lo mostra qui.',
    ],
    whereTitle: 'Dove stanno i dati',
    where:
      'Sul tuo server, nel tuo database. Nessuna azienda in mezzo, nessun account presso un fornitore, nessuna trasmissione a terzi.',
    retentionTitle: 'Per quanto tempo',
    retention:
      'I valori orari restano 13 mesi, poi solo i totali giornalieri. Puoi cancellare in qualsiasi momento tutti i dati di un bambino — spariscono, non vengono archiviati.',
    childSeesTitle: 'Cosa vede il bambino',
    childSees:
      'L’app è visibile, ha un’icona e mostra in permanenza un avviso che il tempo di schermo viene trasmesso. Il suo schermo spiega, con le stesse parole, cosa viene inviato e cosa no. Tuo figlio può controllare in qualsiasi momento cosa è stato inviato.',
    stopTitle: 'Smettere',
    stop:
      'Disconnetti il dispositivo qui e il server non accetta più i suoi dati. Oppure disinstalla l’app dal telefono. Entrambe le cose hanno effetto subito.',
    notAControlTitle: 'Cosa Schirmziit non è',
    notAControl:
      'Schirmziit non blocca e non filtra nulla. È una base per parlarne, non un telecomando. Limiti di tempo e blocchi sono volutamente un passo successivo e separato.',
    resourcesTitle: 'Aiuto e raccomandazioni',
    resourcesLead:
      'Schirmziit mostra numeri, non consigli. Quanto tempo di schermo abbia senso, e cosa aiuta quando si litiga, lo spiegano meglio questi enti svizzeri:',
    resources: [
      {
        name: 'Giovani e media',
        note: 'La piattaforma della Confederazione: indicazioni per età, regole, schede per i genitori.',
        href: 'https://www.giovaniemedia.ch/',
      },
      {
        name: 'Pro Juventute — tempo di schermo',
        note: 'Valori indicativi per età e idee per accordi in famiglia.',
        href: 'https://www.projuventute.ch/it/genitori/media-internet',
      },
      {
        name: 'Consulenza 147',
        note: 'Consulenza gratuita per bambini e ragazzi, 24 ore su 24 — telefono, chat o SMS.',
        href: 'https://www.147.ch/',
      },
      {
        name: 'Zischtig.ch',
        note: 'Centro svizzero per la competenza mediale: serate per genitori, corsi, consulenza.',
        href: 'https://www.zischtig.ch/',
      },
    ],
  },

  errors: {
    generic: 'Qualcosa è andato storto.',
    notFound: 'Non trovato.',
    offline: 'Nessuna connessione al server.',
  },

  units: { hoursShort: 'h', minutesShort: 'min' },
}
