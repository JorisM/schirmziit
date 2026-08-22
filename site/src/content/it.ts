import type { Site } from './strings'

export const it: Site = {
  htmlLang: 'it-CH',
  swissLabel: 'Sviluppato in Svizzera',
  nav: { home: 'Panoramica', selfHost: 'Self-hosting', hosted: 'Ospitato', privacy: 'Privacy' },

  home: {
    kicker: 'Tempo di schermo per famiglie',
    title: 'Vedere quanto e quando — senza farlo di nascosto',
    lead:
      'Nestling mostra quanto e in che momento della giornata viene usato il telefono di tuo figlio. Il bambino vede gli stessi numeri sul proprio telefono. Nessun contenuto, nessuna posizione, nessun telecomando.',
    ctaSelfHost: 'Self-hosting',
    ctaHosted: 'Versione ospitata',

    measuresTitle: 'Cosa misura Nestling',
    measures: [
      'Quale app era in primo piano e per quanto tempo — ora per ora.',
      'Quante volte il telefono è stato sbloccato.',
      'In che momento della giornata è stato usato il telefono.',
    ],
    neverTitle: 'Cosa Nestling non raccoglie mai',
    never: [
      'Né messaggi, né chat, né ricerche, né foto, né digitazioni.',
      'Nessuna posizione.',
      'Nessun sito web e nessun video guardato.',
      'Nessuna registrazione del microfono o della fotocamera.',
      'Non viene bloccato nulla — Nestling non spegne nessuna app.',
    ],

    howTitle: 'Come funziona',
    how: [
      'Sul telefono del bambino gira una piccola app. Legge le statistiche d’uso che Android tiene comunque.',
      'Circa ogni 30 minuti invia valori orari al tuo server — senza internet i valori restano sul telefono.',
      'Tu li guardi nel browser oppure nell’app per iPhone.',
    ],

    ribbonTitle: 'Non solo quanto, ma quando',
    ribbonBody:
      'Un’ora alle 23 significa qualcosa di diverso da un’ora dopo pranzo. Per questo Nestling disegna la giornata come un nastro da mezzanotte a mezzanotte: la forma della giornata si legge a colpo d’occhio.',

    honestTitle: 'Alla luce del sole, non di nascosto',
    honestBody:
      'L’app sul telefono del bambino è visibile, ha un’icona e mostra in permanenza un avviso che il tempo di schermo viene trasmesso. Il suo schermo spiega, con le stesse parole, cosa viene inviato e cosa no. Per controllare di nascosto, questo è lo strumento sbagliato.',

    platformsTitle: 'Dispositivi',
    platformsBody: 'Cosa funziona oggi — e cosa no.',
    androidLabel: 'Android',
    androidBody:
      'Completo: tempo per app e per ora, sblocchi, forma della giornata. Da Android 8 in poi.',
    iosLabel: 'iPhone',
    iosBody:
      'Per ora solo come vista per i genitori. Leggere il tempo di schermo su un iPhone richiede un’autorizzazione di Apple che non abbiamo ancora — lo scriveremo qui appena cambia.',

    openTitle: 'Libero e verificabile',
    openBody:
      'Nestling è open source. Puoi leggere cosa viene inviato e ospitarlo tu stesso — anche se un giorno smettessimo.',
  },

  choose: {
    title: 'Due strade',
    selfHostTitle: 'Self-hosting',
    selfHostFor: 'Per te se già gestisci un server o un Raspberry Pi.',
    selfHostPoints: [
      'Due container: Nestling e Postgres.',
      'I dati restano sul tuo hardware, nel tuo database.',
      'Aggiornamenti, backup e TLS sono a tuo carico.',
      'Gratuito, senza alcun account presso di noi.',
    ],
    hostedTitle: 'Ospitato (beta)',
    hostedFor: 'Per te se non vuoi gestire un server.',
    hostedPoints: [
      'Lo gestiamo noi — per ora sul nostro homelab in Svizzera.',
      'I dati stanno in Svizzera, non presso un grande fornitore cloud.',
      'Gratuito durante la beta, con posti limitati.',
      'Puoi passare al self-hosting quando vuoi: è lo stesso software.',
    ],
  },

  selfHost: {
    title: 'Self-hosting',
    lead:
      'Nestling è un unico programma che serve da sé la sua panoramica, più un database Postgres. Nessun Redis, nessun broker di messaggi, nessun servizio cloud.',
    needTitle: 'Cosa ti serve',
    need: [
      'Una macchina con Docker — basta un Raspberry Pi 4.',
      'Un indirizzo raggiungibile dal telefono del bambino, con TLS davanti (Caddy, Traefik o nginx).',
      'Circa 200 MB di spazio per il primo anno di dati.',
    ],
    stepsTitle: 'Installazione',
    proxyTitle: 'Reverse proxy e TLS',
    proxyBody:
      'Nestling ascolta solo su 127.0.0.1:8080. Metti davanti un reverse proxy che chiuda il TLS e imposta PUBLIC_URL esattamente all’indirizzo che digiti nel browser. Quell’indirizzo finisce nel codice QR di collegamento: se è sbagliato, il telefono si collega una volta e poi non invia più nulla.',
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
    betaTitle: 'Quanto è piccolo, onestamente',
    betaBody:
      'Oggi gira su un homelab gestito da una persona sola. Basta per le famiglie di una beta e non è camuffato da azienda con un servizio di picchetto. Se partecipano abbastanza persone, lo costruiremo come si deve.',
    priceTitle: 'Prezzo',
    priceBody:
      'Gratuito durante la beta. Più avanti dovrà costare qualcosa per sostenersi — il self-hosting resterà gratuito in ogni caso.',
    joinTitle: 'Partecipare',
    joinBody:
      'Scrivici una breve mail indicando il sistema operativo del telefono del bambino. Ti rispondiamo appena si libera un posto.',
    joinCta: 'Chiedere un posto in beta',
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
        body: 'Né messaggi, chat, ricerche, foto, digitazioni, siti web, video, dati del microfono o della fotocamera, né posizione. Nestling non chiede nemmeno quelle autorizzazioni.',
      },
      {
        title: 'Per quanto tempo',
        body: 'I valori orari 13 mesi, poi solo i totali giornalieri. Puoi cancellare i dati di un bambino in qualsiasi momento — spariscono, non vengono archiviati.',
      },
      {
        title: 'Chi li vede',
        body: 'Il tuo account e il bambino sul proprio telefono. Con il self-hosting nessun altro. Nella beta ospitata, tecnicamente anche chi gestisce il database — e nessun altro oltre.',
      },
      {
        title: 'Terze parti',
        body: 'Nessuna. Nessun analytics sul telefono del bambino, nessun SDK pubblicitario, nessun crash reporter, nessuna trasmissione.',
      },
    ],
    analyticsTitle: 'Questo sito',
    analyticsBody:
      'Questo sito conta le visite con un’istanza Umami self-hosted: nessun cookie, nessuna memorizzazione di IP, niente condiviso con terze parti. Vogliamo solo sapere se qualcuno legge.',
  },

  footer: { madeIn: 'Sviluppato in Svizzera', source: 'Codice sorgente', contact: 'Contatto' },
}
