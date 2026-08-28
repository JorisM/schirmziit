import type { Site } from './strings'

export const it: Site = {
  htmlLang: 'it-CH',

  ogLocale: 'it_CH',

  // The home page leads with the brand because it is the brand page; every
  // other title leads with what the page is, which is what someone scanning a
  // result list is looking for.
  meta: {
    home: {
      title: 'Schirmziit — Tempo di schermo per famiglie',
      description:
        'Vedi quanto e quando viene usato il telefono di tuo figlio. Nessun contenuto, nessuna posizione, niente di bloccato. Aperto e self-hosted.',
    },
    selfHost: {
      title: 'Self-hosting — Schirmziit',
      description:
        'Un solo programma e un database Postgres sul tuo server. Docker Compose, reverse proxy, backup — passo per passo.',
    },
    hosted: {
      title: 'Versione ospitata — Schirmziit',
      description:
        'Non vuoi gestire un server. Ospitiamo Schirmziit per te — piccolo, aperto e per ora in Svizzera.',
    },
    privacy: {
      title: 'Privacy — Schirmziit',
      description:
        'Tempi di utilizzo per ora, nient’altro. Nessun contenuto, nessuna posizione, nessuna trasmissione. Cosa viene salvato e chi lo vede.',
    },
  },
  swissLabel: 'Sviluppato in Svizzera',
  nav: { home: 'Panoramica', selfHost: 'Self-hosting', hosted: 'Ospitato', privacy: 'Privacy' },

  alpha: {
    bannerTitle: 'Alpha privata',
    bannerBody:
      'Schirmziit è in pieno sviluppo e non è ancora pubblicato. Quello che leggi qui descrive ciò che già funziona — non qualcosa da installare oggi per la tua famiglia.',
    bannerCta: "Iscriviti alla lista d'attesa",
    title: "Lista d'attesa",
    lead:
      'Lascia il tuo indirizzo e-mail e ti scriviamo appena Schirmziit viene pubblicato. Un messaggio e basta — nessuna newsletter.',
    emailLabel: 'Indirizzo e-mail',
    placeholder: 'tu@example.ch',
    submit: 'Iscrivimi',
    sending: 'Iscrizione in corso …',
    done: 'Fatto. Ti scriviamo appena si parte.',
    invalid: 'Questo non sembra un indirizzo e-mail.',
    failed: 'Non ha funzionato. Riprova più tardi o scrivici una e-mail.',
    stored:
      "Salviamo l'indirizzo e la lingua di questa pagina, nient'altro. Nessun tracciamento, nessuna condivisione. Scrivici e cancelliamo la voce.",
    mailFallback:
      'Il modulo richiede JavaScript. Scrivici invece una breve e-mail — è sufficiente.',
    mailCta: 'Scrivi una e-mail',
  },

  home: {
    kicker: 'Tempo di schermo per famiglie',
    title: 'Il tempo di schermo sotto controllo, per proteggere tuo figlio',
    lead:
      'Schirmziit mostra quanto e in che momento della giornata viene usato il telefono di tuo figlio, così noti quando diventa troppo e puoi parlarne. Nessun contenuto, nessuna posizione, nessun telecomando.',
    ctaSelfHost: 'Self-hosting',
    ctaHosted: 'Versione ospitata',

    measuresTitle: 'Cosa misura Schirmziit',
    measures: [
      'Quale app era in primo piano e per quanto tempo — ora per ora.',
      'Quante volte il telefono è stato sbloccato.',
      'In che momento della giornata è stato usato il telefono.',
    ],
    neverTitle: 'Cosa Schirmziit non raccoglie mai',
    never: [
      'Né messaggi, né chat, né ricerche, né foto, né digitazioni.',
      'Nessuna posizione.',
      'Nessun sito web e nessun video guardato.',
      'Nessuna registrazione del microfono o della fotocamera.',
      'Non viene bloccato nulla — Schirmziit non spegne nessuna app.',
    ],

    howTitle: 'Come funziona',
    how: [
      'Sul telefono del bambino gira una piccola app. Legge le statistiche d’uso che Android tiene comunque.',
      'Circa ogni 30 minuti invia valori orari al tuo server — senza internet i valori restano sul telefono.',
      'Tu li guardi nel browser oppure nell’app per iPhone.',
    ],

    ribbonTitle: 'Non solo quanto, ma quando',
    ribbonBody:
      'Un’ora alle 23 significa qualcosa di diverso da un’ora dopo pranzo. Per questo Schirmziit disegna la giornata come un nastro da mezzanotte a mezzanotte: la forma della giornata si legge a colpo d’occhio.',

    childSeesTitle: 'Il bambino vede gli stessi numeri',
    childSeesBody:
      'La tua panoramica mostra gli ultimi 14 giorni in un colpo d’occhio; tocca un giorno per vederlo ora per ora. L’app sul telefono del bambino mostra esattamente lo stesso — gli stessi 14 giorni, lo stesso giorno nel dettaglio, gli stessi numeri. Resta una base comune per parlarne, non un controllo che gira in sottofondo.',

    platformsTitle: 'Dispositivi',
    platformsBody: 'Cosa funziona oggi — e cosa no.',
    androidLabel: 'Android',
    androidBody:
      'Completo: tempo per app e per ora, sblocchi, forma della giornata. Da Android 8 in poi. Oggi si installa come APK, non ancora dal Play Store.',
    iosLabel: 'iPhone',
    iosBody:
      'Entrambi i ruoli funzionano su iPhone: la panoramica per i genitori e ora anche una vista per il bambino stesso. Anche un iPhone viene misurato — per questo l’app ha bisogno dell’accesso al tempo di schermo di Apple, che Apple concede app per app. Vale per i nostri dispositivi di test; per la distribuzione via TestFlight o App Store l’autorizzazione manca ancora. Da iOS 17 in poi.',
    matrix: {
      title: 'Cosa funziona dove',
      lead:
        'Lo stato di oggi, riga per riga. «Non ancora» significa previsto o bloccato — la nota dice quale dei due.',
      featureHeader: 'Funzione',
      columns: { android: 'Telefono Android', ios: 'iPhone', web: 'Panoramica nel browser' },
      groups: { measure: 'Misurare sul telefono del bambino', view: 'Guardare' },
      legend: { yes: 'funziona', partial: 'in parte', no: 'non ancora' },
      notApplicable: 'non previsto qui',
      rows: {
        appHours: { label: 'Tempo per app e per ora' },
        timeOfDay: { label: 'In quale ora del giorno è stato usato il telefono' },
        unlocks: {
          label: 'Sblocchi',
          note: 'iOS non conta gli sblocchi. Viene contato quante volte il telefono è stato preso in mano — l’equivalente onesto più vicino.',
        },
        background: {
          label: 'Audio in secondo piano, schermo spento',
          note: 'Su Android solo se permetti l’accesso alle notifiche; iOS non lo fornisce. Questo tempo non viene mai contato come tempo di schermo.',
        },
        appNames: {
          label: 'Nomi delle app invece dei nomi dei pacchetti',
          note: 'iOS trattiene alcuni nomi e allora compare l’identificatore dell’app.',
        },
        offline: { label: 'Senza internet i valori aspettano sul telefono' },
        install: {
          label: 'Installabile senza un computer di sviluppo',
          note: 'Android: oggi un APK, non ancora il Play Store. iPhone: TestFlight e App Store richiedono l’autorizzazione di Apple per la distribuzione, che manca ancora.',
        },
        roles: {
          label: 'Ruolo genitore e ruolo bambino in una sola app',
          note: 'L’app Android è solo per il telefono del bambino.',
        },
        overview14: { label: 'Gli ultimi quattordici giorni a colpo d’occhio' },
        dayDetail: { label: 'Un giorno, ora per ora' },
        weekComparison: {
          label: 'La settimana scorsa rispetto a quella precedente',
          note: 'Il tempo sullo schermo e le sere dalle 21:00, con le app che sono cambiate. Sette giorni conclusi: il giorno in corso non viene confrontato, perché non è ancora finito.',
        },
        childOwnNumbers: { label: 'Il bambino vede gli stessi numeri sul suo telefono' },
        manageChildren: { label: 'Aggiungere e rimuovere un bambino' },
        revokeDevice: { label: 'Scollegare un telefono' },
        pairingCode: {
          label: 'Creare un codice di collegamento per il telefono di un bambino',
          note: 'Mostrato come codice QR e come sei caratteri, nella panoramica e in entrambe le app. Il telefono del bambino scansiona il codice, oppure qualcuno digita i sei caratteri e l’indirizzo del server.',
        },
        deleteData: {
          label: 'Cancellare i numeri salvati di un bambino',
          note: 'Cancella i valori orari e i totali giornalieri e dice quante righe sono sparite. Il bambino resta collegato e continua a inviare.',
        },
        helpLinks: {
          label: 'Aiuto e servizi di consulenza svizzeri',
        },
        languages: { label: 'Tedesco, francese, italiano, inglese' },
      },
    },

    openTitle: 'Libero e verificabile',
    openBody:
      'Schirmziit è open source. Puoi leggere cosa viene inviato e ospitarlo tu stesso — anche se un giorno smettessimo.',
  },

  choose: {
    title: 'Due strade',
    selfHostTitle: 'Self-hosting',
    selfHostFor: 'Per te se già gestisci un server o un Raspberry Pi.',
    selfHostPoints: [
      'Due container: Schirmziit e Postgres.',
      'I dati restano sul tuo hardware, nel tuo database.',
      'Aggiornamenti, backup e TLS sono a tuo carico.',
      'Gratuito, senza alcun account presso di noi.',
    ],
    hostedTitle: 'Ospitato (alpha privata)',
    hostedFor: 'Per te se non vuoi gestire un server.',
    hostedPoints: [
      'Lo gestiamo noi — per ora sul nostro homelab in Svizzera.',
      'I dati stanno in Svizzera, non presso un grande fornitore cloud.',
      'Gratuito durante l’alpha, con posti limitati.',
      'Puoi passare al self-hosting quando vuoi: è lo stesso software.',
    ],
  },

  selfHost: {
    title: 'Self-hosting',
    lead:
      'Schirmziit è un unico programma che serve da sé la sua panoramica, più un database Postgres. Nessun Redis, nessun broker di messaggi, nessun servizio cloud.',
    needTitle: 'Cosa ti serve',
    need: [
      'Una macchina con Docker — basta un Raspberry Pi 4.',
      'Un indirizzo raggiungibile dal telefono del bambino, con TLS davanti (Caddy, Traefik o nginx).',
      'Circa 200 MB di spazio per il primo anno di dati.',
    ],
    stepsTitle: 'Installazione',
    proxyTitle: 'Reverse proxy e TLS',
    proxyBody:
      'Schirmziit ascolta solo su 127.0.0.1:8080. Metti davanti un reverse proxy che chiuda il TLS e imposta PUBLIC_URL esattamente all’indirizzo che digiti nel browser. Quell’indirizzo finisce nel codice QR di collegamento: se è sbagliato, il telefono si collega una volta e poi non invia più nulla.',
    firstUserTitle: 'Il primo account',
    firstUserBody:
      'Per impostazione predefinita può registrarsi un solo account, poi la registrazione si chiude. Apri la panoramica, crea il tuo account e poi imposta ALLOW_REGISTRATION su «off».',
    pairTitle: 'Collegare un telefono',
    pairBody:
      'Aggiungi un bambino nella panoramica e genera un codice. Installa l’app Android sul telefono del bambino, consenti l’accesso ai dati di utilizzo e scansiona il codice. Da quel momento il telefono invia i dati circa ogni 30 minuti.',
    backupTitle: 'Backup',
    backupBody:
      'Tutto ciò che conta sta in Postgres. Un pg_dump notturno del volume è sufficiente; la panoramica in sé non ha stato.',
    upgradeTitle: 'Aggiornamenti',
    upgradeBody:
      'docker compose pull e poi docker compose up -d. Le migrazioni del database partono all’avvio. Il downgrade non è previsto: fai prima un backup.',
    troubleTitle: 'Quando qualcosa non funziona',
    trouble: [
      {
        problem: 'Il telefono si collega ma non invia mai nulla.',
        fix: 'PUBLIC_URL non punta all’indirizzo raggiungibile dal telefono. Correggilo, riavvia e genera un nuovo codice.',
      },
      {
        problem: 'La panoramica dice «non invia più nulla».',
        fix: 'Controlla sul telefono che l’accesso ai dati di utilizzo sia ancora consentito e consenti gli aggiornamenti in background se l’app lo chiede.',
      },
      {
        problem: 'Postgres non parte più dopo un aggiornamento.',
        fix: 'Da Postgres 18 il volume va montato su /var/lib/postgresql e non su /var/lib/postgresql/data. Il nostro file compose lo fa già.',
      },
      {
        problem: 'Un’app compare come «com.qualcosa.app».',
        fix: 'Il telefono non ha potuto risolvere il nome. Dopo il prossimo aggiornamento dell’app Android e un nuovo invio comparirà il nome corretto.',
      },
    ],
  },

  hosted: {
    title: 'Versione ospitata',
    lead: 'Non vuoi gestire un server. Lo facciamo noi — piccolo, aperto e per ora in Svizzera.',
    whereTitle: 'Dove stanno i dati',
    whereBody:
      'Sul nostro hardware in Svizzera, non presso un grande fornitore cloud. Lo stesso software che potresti ospitare tu, con gli stessi limiti: i tempi di utilizzo sì, i contenuti no.',
    scaleTitle: 'Quanto è piccolo, onestamente',
    scaleBody:
      'Oggi gira su un homelab gestito da una persona sola. Basta per le famiglie di un’alpha e non è camuffato da azienda con un servizio di picchetto. Se partecipano abbastanza persone, lo costruiremo come si deve.',
    priceTitle: 'Prezzo',
    priceBody:
      'Gratuito durante l’alpha. Più avanti dovrà costare qualcosa per sostenersi — il self-hosting resterà gratuito in ogni caso.',
    joinTitle: 'Partecipare',
    joinBody:
      'Scrivici una breve mail indicando il sistema operativo del telefono del bambino. Ti rispondiamo appena si libera un posto.',
    joinCta: 'Chiedere un posto in alpha',
  },

  privacy: {
    title: 'Privacy',
    lead:
      'In breve: tempi di utilizzo per ora, nient’altro. Nessun contenuto, nessuna posizione, nessuna trasmissione.',
    sections: [
      {
        title: 'Cosa viene salvato',
        body: 'Per ora e per app: tempo in primo piano e quante volte è stata aperta. Per ora e per dispositivo: tempo a schermo acceso e sblocchi. Più il nome dell’app che il telefono fornisce e il nome che dai al bambino.',
      },
      {
        title: 'Cosa non viene salvato',
        body: 'Né messaggi, chat, ricerche, foto, digitazioni, siti web, video, dati del microfono o della fotocamera, né posizione. Schirmziit non chiede nemmeno quelle autorizzazioni.',
      },
      {
        title: 'Per quanto tempo',
        body: 'I valori orari 13 mesi, poi solo i totali giornalieri. Puoi cancellare i dati di un bambino in qualsiasi momento — spariscono, non vengono archiviati.',
      },
      {
        title: 'Chi li vede',
        body: 'Il tuo account e il bambino sul proprio telefono. Con il self-hosting nessun altro. Nell’alpha ospitata, tecnicamente anche chi gestisce il database — e nessun altro oltre.',
      },
      {
        title: 'Terze parti',
        body: 'Nessuna. Nessun analytics sul telefono del bambino, nessun SDK pubblicitario, nessun crash reporter, nessuna trasmissione.',
      },
      {
        title: "Lista d'attesa",
        body: "Se ti iscrivi alla lista d'attesa salviamo il tuo indirizzo e-mail e la lingua in cui hai letto il sito. Solo per questo: una e-mail quando Schirmziit viene pubblicato. Nessuna newsletter, nessuna condivisione. Scrivici e cancelliamo la voce.",
      },
    ],
    analyticsTitle: 'Questo sito',
    analyticsBody:
      'Questo sito conta le visite con un’istanza Umami self-hosted: nessun cookie, nessuna memorizzazione di IP, niente condiviso con terze parti. Vogliamo solo sapere se qualcuno legge.',
  },

  resources: {
    title: 'Aiuto e raccomandazioni',
    lead:
      'Schirmziit mostra numeri, non consigli. Quanto tempo di schermo abbia senso, e cosa aiuta quando finisce in litigio, lo spiegano meglio questi enti svizzeri:',
    items: [
      {
        name: 'Giovani e media',
        note: 'La piattaforma nazionale della Confederazione: indicazioni per età, regole, schede per i genitori.',
        href: 'https://www.jugendundmedien.ch/it',
      },
      {
        name: 'Pro Juventute',
        note: 'Valori indicativi per età e consigli per accordi in famiglia.',
        href: 'https://www.projuventute.ch/it',
      },
      {
        name: 'Consulenza 147',
        note: 'Consulenza gratuita per bambini e giovani, 24 ore su 24, per telefono, chat o SMS.',
        href: 'https://www.147.ch/',
      },
      {
        name: 'Dipendenze Svizzera',
        note: 'Informazioni e aiuto sull’uso problematico degli schermi.',
        href: 'https://www.suchtschweiz.ch/',
      },
    ],
  },

  footer: { madeIn: 'Sviluppato in Svizzera', source: 'Codice sorgente', contact: 'Contatto' },
}
