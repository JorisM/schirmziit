import type { Site } from './strings'

/** Schweizer Hochdeutsch: kein ß, du-Form. */
export const de: Site = {
  htmlLang: 'de-CH',
  swissLabel: 'Entwickelt in der Schweiz',
  nav: { home: 'Übersicht', selfHost: 'Selbst hosten', hosted: 'Gehostet', privacy: 'Datenschutz' },

  home: {
    kicker: 'Bildschirmzeit für Familien',
    title: 'Sehen, wie lange und wann — ohne heimlich zu sein',
    lead:
      'Nestling zeigt dir, wie lange und zu welcher Tageszeit das Handy deines Kindes benutzt wird. Das Kind sieht dieselben Zahlen auf seinem Handy. Keine Inhalte, kein Standort, keine Fernsteuerung.',
    ctaSelfHost: 'Selbst hosten',
    ctaHosted: 'Gehostete Version',

    measuresTitle: 'Was Nestling misst',
    measures: [
      'Welche App im Vordergrund war und wie lange — pro Stunde.',
      'Wie oft das Handy entsperrt wurde.',
      'Wann am Tag das Handy benutzt wurde.',
    ],
    neverTitle: 'Was Nestling nie sammelt',
    never: [
      'Keine Nachrichten, Chats, Suchbegriffe, Fotos oder Tastatureingaben.',
      'Keinen Standort.',
      'Keine Webseiten und keine Videos, die angeschaut wurden.',
      'Keine Mikrofon- oder Kameraaufnahmen.',
      'Nichts wird blockiert — Nestling schaltet keine App ab.',
    ],

    howTitle: 'Wie es funktioniert',
    how: [
      'Auf dem Handy des Kindes läuft eine kleine App. Sie liest die Nutzungsstatistik, die Android ohnehin führt.',
      'Etwa alle 30 Minuten schickt sie Stundenwerte an deinen Server — ohne Internet warten die Werte auf dem Handy.',
      'Du schaust in der Übersicht im Browser oder in der iPhone-App nach.',
    ],

    ribbonTitle: 'Nicht nur wie viel, sondern wann',
    ribbonBody:
      'Eine Stunde um 23 Uhr bedeutet etwas anderes als eine Stunde nach dem Mittagessen. Darum zeigt Nestling den Tag als Band von Mitternacht bis Mitternacht — die Form des Tages siehst du auf einen Blick.',

    honestTitle: 'Offen statt heimlich',
    honestBody:
      'Die App auf dem Kinderhandy ist sichtbar, hat ein Icon und zeigt dauerhaft eine Meldung, dass Bildschirmzeit übermittelt wird. Auf ihrem Bildschirm steht in derselben Sprache, was gesendet wird und was nicht. Wer heimlich mitlesen will, ist hier falsch.',

    platformsTitle: 'Geräte',
    platformsBody: 'Was heute geht — und was nicht.',
    androidLabel: 'Android',
    androidBody:
      'Vollständig: Nutzungszeit pro App und Stunde, Entsperrungen, Tagesverlauf. Ab Android 8.',
    iosLabel: 'iPhone',
    iosBody:
      'Vorerst nur als Übersicht für Eltern. Bildschirmzeit auf einem iPhone auszulesen braucht eine Apple-Bewilligung, die wir noch nicht haben — wir sagen es hier, sobald sich das ändert.',

    openTitle: 'Frei und überprüfbar',
    openBody:
      'Nestling ist Open Source. Du kannst nachlesen, was gesendet wird, und es selbst hosten — auch wenn wir irgendwann aufhören.',
  },

  choose: {
    title: 'Zwei Wege',
    selfHostTitle: 'Selbst hosten',
    selfHostFor: 'Für dich, wenn du schon einen Server oder einen Raspberry Pi betreibst.',
    selfHostPoints: [
      'Zwei Container: Nestling und Postgres.',
      'Die Daten bleiben auf deiner Hardware, in deiner Datenbank.',
      'Du bist für Updates, Backups und TLS zuständig.',
      'Kostenlos, ohne Konto bei uns.',
    ],
    hostedTitle: 'Gehostet (Beta)',
    hostedFor: 'Für dich, wenn du keinen Server betreiben willst.',
    hostedPoints: [
      'Wir betreiben es — vorerst auf unserem eigenen Homelab in der Schweiz.',
      'Daten liegen in der Schweiz, nicht bei einem Hyperscaler.',
      'Während der Beta kostenlos und mit begrenzten Plätzen.',
      'Du kannst jederzeit auf Selbst-hosten wechseln, es ist dieselbe Software.',
    ],
  },

  selfHost: {
    title: 'Selbst hosten',
    lead:
      'Nestling ist ein einziges Programm, das die Übersicht gleich mitliefert, plus eine Postgres-Datenbank. Kein Redis, kein Message-Broker, kein Cloud-Dienst.',
    needTitle: 'Was du brauchst',
    need: [
      'Einen Rechner mit Docker — ein Raspberry Pi 4 genügt.',
      'Eine Adresse, die das Kinderhandy erreicht, und TLS davor (z. B. Caddy, Traefik oder nginx).',
      'Rund 200 MB Speicher für die Datenbank im ersten Jahr.',
    ],
    stepsTitle: 'Installation',
    proxyTitle: 'Reverse Proxy und TLS',
    proxyBody:
      'Nestling lauscht nur auf 127.0.0.1:8080. Stelle einen Reverse Proxy davor, der TLS beendet, und setze PUBLIC_URL genau auf die Adresse, die du im Browser eingibst. Diese Adresse landet im QR-Code fürs Verbinden: ist sie falsch, verbindet sich das Handy einmal und meldet dann nie wieder.',
    firstUserTitle: 'Erstes Konto',
    firstUserBody:
      'Standardmässig darf sich genau ein Konto registrieren, danach ist die Registrierung zu. Öffne die Übersicht, lege dein Konto an, und setze ALLOW_REGISTRATION anschliessend auf «off».',
    pairTitle: 'Handy verbinden',
    pairBody:
      'Lege in der Übersicht ein Kind an und erzeuge einen Code. Installiere die Android-App auf dem Kinderhandy, erlaube den Nutzungszugriff und scanne den Code. Danach meldet sich das Handy etwa alle 30 Minuten.',
    backupTitle: 'Backup',
    backupBody:
      'Alles Wichtige liegt in Postgres. Ein nächtliches pg_dump des Volumes genügt; die Übersicht selbst hat keinen Zustand.',
    upgradeTitle: 'Updates',
    upgradeBody:
      'docker compose pull und docker compose up -d. Die Datenbank-Migrationen laufen beim Start selbst; ein Downgrade ist nicht vorgesehen, also mach vorher ein Backup.',
    troubleTitle: 'Wenn etwas nicht läuft',
    trouble: [
      {
        problem: 'Das Handy verbindet sich, meldet aber nie etwas.',
        fix: 'PUBLIC_URL zeigt nicht auf die Adresse, die das Handy erreicht. Korrigieren, neu starten, neuen Code erzeugen.',
      },
      {
        problem: 'In der Übersicht steht «meldet sich nicht».',
        fix: 'Prüfe auf dem Handy, ob der Nutzungszugriff noch erlaubt ist, und erlaube Hintergrund-Updates, wenn die App danach fragt.',
      },
      {
        problem: 'Postgres startet nach einem Update nicht mehr.',
        fix: 'Ab Postgres 18 muss das Volume auf /var/lib/postgresql liegen, nicht auf /var/lib/postgresql/data. Unsere compose-Datei macht das schon richtig.',
      },
      {
        problem: 'Eine App heisst im Bericht «com.irgendwas.app».',
        fix: 'Das Handy konnte den Namen nicht auflösen. Nach dem nächsten Update der Android-App und einer neuen Meldung steht der richtige Name da.',
      },
    ],
  },

  hosted: {
    title: 'Gehostete Version',
    lead:
      'Du willst keinen Server betreiben. Wir machen das — vorerst klein, offen und in der Schweiz.',
    whereTitle: 'Wo die Daten liegen',
    whereBody:
      'Auf unserer eigenen Hardware in der Schweiz, nicht bei einem grossen Cloud-Anbieter. Dieselbe Software, die du auch selbst hosten könntest, mit denselben Grenzen: Nutzungszeiten ja, Inhalte nein.',
    betaTitle: 'Wie ehrlich klein das ist',
    betaBody:
      'Das läuft im Moment auf einem Homelab, betrieben von einer Person. Das reicht für die Familien einer Beta und ist nicht als Firma mit Bereitschaftsdienst verkleidet. Wenn genug Leute mitmachen, bauen wir es richtig aus.',
    priceTitle: 'Preis',
    priceBody:
      'Während der Beta gratis. Später wird es etwas kosten müssen, damit es sich selbst trägt — Selbst-hosten bleibt in jedem Fall kostenlos.',
    joinTitle: 'Mitmachen',
    joinBody:
      'Schreib uns eine kurze Mail mit dem Betriebssystem des Kinderhandys. Wir melden uns, sobald ein Platz frei ist.',
    joinCta: 'Für die Beta melden',
  },

  privacy: {
    title: 'Datenschutz',
    lead:
      'Kurz: Nutzungszeiten pro Stunde, sonst nichts. Keine Inhalte, kein Standort, keine Weitergabe.',
    sections: [
      {
        title: 'Was gespeichert wird',
        body: 'Pro Stunde und App: Vordergrundzeit und wie oft sie geöffnet wurde. Pro Stunde und Gerät: Bildschirmzeit und Entsperrungen. Dazu der App-Name, den das Handy liefert, und der Name, den du dem Kind gibst.',
      },
      {
        title: 'Was nicht gespeichert wird',
        body: 'Keine Nachrichten, Chats, Suchbegriffe, Fotos, Tastatureingaben, Webseiten, Videos, Mikrofon- oder Kameradaten und kein Standort. Nestling fragt diese Berechtigungen gar nicht an.',
      },
      {
        title: 'Wie lange',
        body: 'Stundenwerte 13 Monate, danach nur noch Tagessummen. Du kannst die Daten eines Kindes jederzeit löschen — dann sind sie weg, nicht archiviert.',
      },
      {
        title: 'Wer es sieht',
        body: 'Dein Konto und das Kind auf seinem eigenen Handy. Beim Selbst-hosten sonst niemand. In der gehosteten Beta technisch zusätzlich der Betreiber, der die Datenbank verwaltet — mehr Leute nicht.',
      },
      {
        title: 'Dritte',
        body: 'Keine. Kein Analytics im Kinderhandy, keine Werbe-SDKs, keine Crash-Reporter, keine Weitergabe.',
      },
    ],
    analyticsTitle: 'Diese Webseite',
    analyticsBody:
      'Diese Seite zählt Aufrufe mit einer selbst gehosteten Instanz von Umami: keine Cookies, keine IP-Speicherung, keine Weitergabe an Dritte. Wir wollen nur wissen, ob jemand liest.',
  },

  footer: {
    madeIn: 'Entwickelt in der Schweiz',
    source: 'Quellcode',
    contact: 'Kontakt',
  },
}
