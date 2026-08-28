import type { Site } from './strings'

/** Schweizer Hochdeutsch: kein ß, du-Form. */
export const de: Site = {
  htmlLang: 'de-CH',

  ogLocale: 'de_CH',

  // The home page leads with the brand because it is the brand page; every
  // other title leads with what the page is, which is what someone scanning a
  // result list is looking for.
  meta: {
    home: {
      title: 'Schirmziit — Bildschirmzeit für Familien',
      description:
        'Sieh, wie lange und wann das Handy deines Kindes benutzt wird. Keine Inhalte, kein Standort, nichts wird blockiert. Offen und selbst gehostet.',
    },
    selfHost: {
      title: 'Selbst hosten — Schirmziit',
      description:
        'Ein Programm und eine Postgres-Datenbank auf deinem eigenen Server. Docker Compose, Reverse Proxy, Backup — Schritt für Schritt.',
    },
    hosted: {
      title: 'Gehostete Version — Schirmziit',
      description:
        'Du willst keinen Server betreiben. Wir hosten Schirmziit für dich — vorerst klein, offen und in der Schweiz.',
    },
    privacy: {
      title: 'Datenschutz — Schirmziit',
      description:
        'Nutzungszeiten pro Stunde, sonst nichts. Keine Inhalte, kein Standort, keine Weitergabe. Was gespeichert wird, wie lange, und wer es sieht.',
    },
  },
  swissLabel: 'Entwickelt in der Schweiz',
  nav: { home: 'Übersicht', selfHost: 'Selbst hosten', hosted: 'Gehostet', privacy: 'Datenschutz' },

  alpha: {
    bannerTitle: 'Private Alpha',
    bannerBody:
      'Schirmziit ist mitten in der Entwicklung und noch nicht veröffentlicht. Was hier steht, beschreibt, was schon läuft — nicht etwas, das du heute für deine Familie aufsetzen solltest.',
    bannerCta: 'Auf die Warteliste',
    title: 'Warteliste',
    lead:
      'Trag deine Mail-Adresse ein, und du hörst von uns, sobald Schirmziit veröffentlicht ist. Eine Mail, dann ist Schluss — kein Newsletter.',
    emailLabel: 'Mail-Adresse',
    placeholder: 'du@example.ch',
    submit: 'Eintragen',
    sending: 'Wird eingetragen …',
    done: 'Eingetragen. Wir melden uns, sobald es losgeht.',
    invalid: 'Das sieht nicht wie eine Mail-Adresse aus.',
    failed: 'Das hat nicht funktioniert. Versuch es später nochmals oder schreib uns eine Mail.',
    stored:
      'Gespeichert werden die Adresse und die Sprache dieser Seite, sonst nichts. Kein Tracking, keine Weitergabe. Schreib uns, und der Eintrag ist weg.',
    mailFallback:
      'Das Formular braucht JavaScript. Schreib uns stattdessen kurz eine Mail — das genügt.',
    mailCta: 'Mail schreiben',
  },

  home: {
    kicker: 'Bildschirmzeit für Familien',
    title: 'Bildschirmzeit im Blick, zum Schutz deines Kindes',
    lead:
      'Schirmziit zeigt dir, wie lange und zu welcher Tageszeit das Handy deines Kindes benutzt wird. Damit du merkst, wenn es zu viel wird, und darüber reden kannst. Keine Inhalte, kein Standort, keine Fernsteuerung.',
    ctaSelfHost: 'Selbst hosten',
    ctaHosted: 'Gehostete Version',

    measuresTitle: 'Was Schirmziit misst',
    measures: [
      'Welche App im Vordergrund war und wie lange — pro Stunde.',
      'Wie oft das Handy entsperrt wurde.',
      'Wann am Tag das Handy benutzt wurde.',
    ],
    neverTitle: 'Was Schirmziit nie sammelt',
    never: [
      'Keine Nachrichten, Chats, Suchbegriffe, Fotos oder Tastatureingaben.',
      'Keinen Standort.',
      'Keine Webseiten und keine Videos, die angeschaut wurden.',
      'Keine Mikrofon- oder Kameraaufnahmen.',
      'Nichts wird blockiert — Schirmziit schaltet keine App ab.',
    ],

    howTitle: 'Wie es funktioniert',
    how: [
      'Auf dem Handy des Kindes läuft eine kleine App. Sie liest die Nutzungsstatistik, die Android ohnehin führt.',
      'Etwa alle 30 Minuten schickt sie Stundenwerte an deinen Server — ohne Internet warten die Werte auf dem Handy.',
      'Du schaust in der Übersicht im Browser oder in der iPhone-App nach.',
    ],

    ribbonTitle: 'Nicht nur wie viel, sondern wann',
    ribbonBody:
      'Eine Stunde um 23 Uhr bedeutet etwas anderes als eine Stunde nach dem Mittagessen. Darum zeigt Schirmziit den Tag als Band von Mitternacht bis Mitternacht — die Form des Tages siehst du auf einen Blick.',

    childSeesTitle: 'Das Kind sieht dieselben Zahlen',
    childSeesBody:
      'Deine Übersicht zeigt die letzten 14 Tage auf einen Blick; du kannst jeden Tag antippen und siehst ihn Stunde für Stunde. Die App auf dem Kinderhandy zeigt genau dasselbe — dieselben 14 Tage, denselben Tag im Detail, dieselben Zahlen. So bleibt es eine gemeinsame Grundlage statt einer Kontrolle im Hintergrund.',

    platformsTitle: 'Geräte',
    platformsBody: 'Was heute geht — und was nicht.',
    androidLabel: 'Android',
    androidBody:
      'Vollständig: Nutzungszeit pro App und Stunde, Entsperrungen, Tagesverlauf. Ab Android 8. Heute als APK installiert, noch nicht im Play Store.',
    iosLabel: 'iPhone',
    iosBody:
      'Beide Rollen laufen auf dem iPhone: die Übersicht für Eltern, und neu auch eine Ansicht fürs Kind selbst. Gemessen wird auf dem iPhone ebenfalls — dafür braucht die App Apples Bildschirmzeit-Zugriff, den Apple pro App bewilligt. Für unsere eigenen Testgeräte gilt er, für die Verteilung über TestFlight oder den App Store steht die Bewilligung noch aus. Ab iOS 17.',
    matrix: {
      title: 'Was wo läuft',
      lead:
        'Der Stand von heute, Zeile für Zeile. «Noch nicht» heisst geplant oder blockiert — die Notiz sagt, woran es liegt.',
      featureHeader: 'Funktion',
      columns: { android: 'Android-Handy', ios: 'iPhone', web: 'Übersicht im Browser' },
      groups: { measure: 'Messen auf dem Kinderhandy', view: 'Anschauen' },
      legend: { yes: 'läuft', partial: 'teilweise', no: 'noch nicht' },
      notApplicable: 'nicht vorgesehen',
      rows: {
        appHours: { label: 'Nutzungszeit pro App und Stunde' },
        timeOfDay: { label: 'Wann am Tag das Handy benutzt wurde' },
        unlocks: {
          label: 'Entsperrungen',
          note: 'iOS zählt keine Entsperrungen. Gezählt wird, wie oft das Handy zur Hand genommen wurde — die nächstliegende ehrliche Entsprechung.',
        },
        background: {
          label: 'Ton im Hintergrund, bei dunklem Bildschirm',
          note: 'Auf Android nur, wenn du den Benachrichtigungszugriff erlaubst; iOS gibt diese Zeit nicht her. Sie wird nie zur Bildschirmzeit gezählt.',
        },
        appNames: {
          label: 'App-Namen statt Paketnamen',
          note: 'iOS gibt manche Namen nicht heraus, dann steht die Kennung der App da.',
        },
        offline: { label: 'Ohne Internet warten die Werte auf dem Handy' },
        install: {
          label: 'Ohne Entwickler-Rechner installierbar',
          note: 'Android: eine APK, signiert mit dem Release-Schlüssel des Projekts, aber noch kein Play Store — Updates kommen also nicht automatisch. iPhone: TestFlight und App Store brauchen Apples Bewilligung für die Verteilung, die noch aussteht.',
        },
        roles: {
          label: 'Eltern- und Kind-Rolle in einer App',
          note: 'Die Android-App ist nur für das Kinderhandy.',
        },
        overview14: { label: 'Übersicht über 14 Tage' },
        dayDetail: { label: 'Ein Tag, Stunde für Stunde' },
        weekComparison: {
          label: 'Letzte Woche im Vergleich zur Woche davor',
          note: 'Bildschirmzeit und die Abende ab 21:00, dazu die Apps, die sich verändert haben. Sieben abgeschlossene Tage: der laufende Tag wird nicht verglichen, weil er noch nicht vorbei ist.',
        },
        childOwnNumbers: { label: 'Das Kind sieht auf seinem Handy dieselben Zahlen' },
        manageChildren: { label: 'Kind anlegen und entfernen' },
        revokeDevice: { label: 'Ein Handy trennen' },
        pairingCode: {
          label: 'Verbindungs-Code für ein Kinderhandy erzeugen',
          note: 'Wird als QR-Code und als sechs Zeichen angezeigt — in der Übersicht und in beiden Handy-Apps. Das Kinderhandy scannt den Code, oder jemand tippt die sechs Zeichen und die Serveradresse ein.',
        },
        scanCode: {
          label: 'Verbinden durch Scannen des Codes',
          note: 'Beide Handy-Apps lesen das Quadrat selbst. Das Scannen füllt Adresse und Code aus; verbunden wird erst auf Knopfdruck, denn ein Code gilt nur einmal.',
        },
        deleteData: {
          label: 'Gespeicherte Zahlen eines Kindes löschen',
          note: 'Löscht Stundenwerte und Tagessummen und sagt, wie viele Zeilen weg sind. Das Kind bleibt verbunden und meldet weiter.',
        },
        helpLinks: {
          label: 'Hilfe und Schweizer Beratungsstellen',
        },
        languages: { label: 'Deutsch, Französisch, Italienisch, Englisch' },
      },
    },

    openTitle: 'Frei und überprüfbar',
    openBody:
      'Schirmziit ist Open Source. Du kannst nachlesen, was gesendet wird, und es selbst hosten — auch wenn wir irgendwann aufhören.',
  },

  choose: {
    title: 'Zwei Wege',
    selfHostTitle: 'Selbst hosten',
    selfHostFor: 'Für dich, wenn du schon einen Server oder einen Raspberry Pi betreibst.',
    selfHostPoints: [
      'Zwei Container: Schirmziit und Postgres.',
      'Die Daten bleiben auf deiner Hardware, in deiner Datenbank.',
      'Du bist für Updates, Backups und TLS zuständig.',
      'Kostenlos, ohne Konto bei uns.',
    ],
    hostedTitle: 'Gehostet (private Alpha)',
    hostedFor: 'Für dich, wenn du keinen Server betreiben willst.',
    hostedPoints: [
      'Wir betreiben es — vorerst auf unserem eigenen Homelab in der Schweiz.',
      'Daten liegen in der Schweiz, nicht bei einem Hyperscaler.',
      'Während der Alpha kostenlos und mit begrenzten Plätzen.',
      'Du kannst jederzeit auf Selbst-hosten wechseln, es ist dieselbe Software.',
    ],
  },

  selfHost: {
    title: 'Selbst hosten',
    lead:
      'Schirmziit ist ein einziges Programm, das die Übersicht gleich mitliefert, plus eine Postgres-Datenbank. Kein Redis, kein Message-Broker, kein Cloud-Dienst.',
    needTitle: 'Was du brauchst',
    need: [
      'Einen Rechner mit Docker — ein Raspberry Pi 4 genügt.',
      'Eine Adresse, die das Kinderhandy erreicht, und TLS davor (z. B. Caddy, Traefik oder nginx).',
      'Rund 200 MB Speicher für die Datenbank im ersten Jahr.',
    ],
    stepsTitle: 'Installation',
    proxyTitle: 'Reverse Proxy und TLS',
    proxyBody:
      'Schirmziit lauscht nur auf 127.0.0.1:8080. Stelle einen Reverse Proxy davor, der TLS beendet, und setze PUBLIC_URL genau auf die Adresse, die du im Browser eingibst. Diese Adresse landet im QR-Code fürs Verbinden: ist sie falsch, verbindet sich das Handy einmal und meldet dann nie wieder.',
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
    scaleTitle: 'Wie ehrlich klein das ist',
    scaleBody:
      'Das läuft im Moment auf einem Homelab, betrieben von einer Person. Das reicht für die Familien einer Alpha und ist nicht als Firma mit Bereitschaftsdienst verkleidet. Wenn genug Leute mitmachen, bauen wir es richtig aus.',
    priceTitle: 'Preis',
    priceBody:
      'Während der Alpha gratis. Später wird es etwas kosten müssen, damit es sich selbst trägt — Selbst-hosten bleibt in jedem Fall kostenlos.',
    joinTitle: 'Mitmachen',
    joinBody:
      'Schreib uns eine kurze Mail mit dem Betriebssystem des Kinderhandys. Wir melden uns, sobald ein Platz frei ist.',
    joinCta: 'Für die Alpha melden',
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
        body: 'Keine Nachrichten, Chats, Suchbegriffe, Fotos, Tastatureingaben, Webseiten, Videos, Mikrofon- oder Kameradaten und kein Standort. Schirmziit fragt diese Berechtigungen gar nicht an.',
      },
      {
        title: 'Wie lange',
        body: 'Stundenwerte 13 Monate, danach nur noch Tagessummen. Du kannst die Daten eines Kindes jederzeit löschen — dann sind sie weg, nicht archiviert.',
      },
      {
        title: 'Wer es sieht',
        body: 'Dein Konto und das Kind auf seinem eigenen Handy. Beim Selbst-hosten sonst niemand. In der gehosteten Alpha technisch zusätzlich der Betreiber, der die Datenbank verwaltet — mehr Leute nicht.',
      },
      {
        title: 'Dritte',
        body: 'Keine. Kein Analytics im Kinderhandy, keine Werbe-SDKs, keine Crash-Reporter, keine Weitergabe.',
      },
      {
        title: 'Warteliste',
        body: 'Trägst du dich für die Warteliste ein, speichern wir deine Mail-Adresse und die Sprache, in der du die Seite gelesen hast. Nur dafür: eine Mail, wenn Schirmziit veröffentlicht ist. Kein Newsletter, keine Weitergabe. Schreib uns, und wir löschen den Eintrag.',
      },
    ],
    analyticsTitle: 'Diese Webseite',
    analyticsBody:
      'Diese Seite zählt Aufrufe mit einer selbst gehosteten Instanz von Umami: keine Cookies, keine IP-Speicherung, keine Weitergabe an Dritte. Wir wollen nur wissen, ob jemand liest.',
  },

  resources: {
    title: 'Hilfe und Empfehlungen',
    lead:
      'Schirmziit zeigt Zahlen, keine Ratschläge. Wie viel Bildschirmzeit sinnvoll ist und was bei Streit hilft, erklären diese Schweizer Stellen besser als wir:',
    items: [
      {
        name: 'Jugend und Medien',
        note: 'Nationale Plattform des Bundes: Altersempfehlungen, Regeln, Merkblätter für Eltern.',
        href: 'https://www.jugendundmedien.ch/',
      },
      {
        name: 'Pro Juventute — Bildschirmzeit',
        note: 'Konkrete Richtwerte pro Alter und Tipps für Abmachungen in der Familie.',
        href: 'https://www.projuventute.ch/de/eltern/medien-internet/bildschirmzeit',
      },
      {
        name: 'Beratung 147',
        note: 'Kostenlose Beratung für Kinder und Jugendliche, rund um die Uhr — per Telefon, Chat oder SMS.',
        href: 'https://www.147.ch/',
      },
      {
        name: 'Zischtig.ch',
        note: 'Schweizer Fachstelle für Medienkompetenz: Elternabende, Kurse, Beratung.',
        href: 'https://www.zischtig.ch/',
      },
    ],
  },

  footer: {
    madeIn: 'Entwickelt in der Schweiz',
    source: 'Quellcode',
    contact: 'Kontakt',
  },
}
