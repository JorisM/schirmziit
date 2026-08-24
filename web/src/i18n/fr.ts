import type { Strings } from './types'

export const fr: Strings = {
  meta: { localeName: 'Français', htmlLang: 'fr-CH' },

  app: {
    name: 'Schirmziit',
    tagline: 'Le temps d’écran sous les yeux, pour protéger ton enfant',
    help: 'Comment ça marche ?',
    signOut: 'Se déconnecter',
    language: 'Langue',
  },

  login: {
    heading: 'Se connecter',
    intro: 'Schirmziit tourne sur ton propre serveur. Il n’y a pas de compte chez une entreprise.',
    email: 'E-mail',
    password: 'Mot de passe',
    submit: 'Se connecter',
    working: 'Un instant…',
    wrongCredentials: 'Cet e-mail ou ce mot de passe n’est pas correct.',
    unexpected: 'Ça n’a pas fonctionné. Réessaie.',
  },

  children: {
    heading: 'Enfants',
    empty: 'Aucun enfant pour l’instant.',
    emptyHint: 'Ajoute un enfant, puis connecte son téléphone.',
    add: 'Ajouter un enfant',
    addPlaceholder: 'Prénom, p. ex. Lena',
    todayTotal: 'aujourd’hui',
    openChild: 'Voir les détails',
  },

  child: {
    todayHeading: 'Aujourd’hui',
    totalToday: 'Temps d’écran aujourd’hui',
    unlocks: 'déverrouillages',
    firstActivity: 'Première utilisation',
    lastActivity: 'Dernière utilisation',
    noDataToday: 'Rien de signalé aujourd’hui.',
    noDataHint:
      'Cela peut vouloir dire que le téléphone n’a pas été utilisé — ou qu’il n’a pas encore envoyé ses données. Ci-dessous, tu vois quand il l’a fait pour la dernière fois.',
    ribbonTitle: 'La forme de la journée',
    ribbonHelp:
      'Chaque case représente une heure, de minuit à minuit. Plus c’est foncé, plus l’écran est resté allumé. Tu vois donc non seulement combien, mais quand.',
    ribbonQuiet: 'calme',
    ribbonBusy: 'chargé',
    ribbonNight: 'nuit',
    appsTitle: 'Applications',
    appsHelp:
      'Combien de temps chaque application est restée au premier plan, additionné sur tous les appareils de l’enfant.',
    appColumn: 'Application',
    timeColumn: 'Temps',
    openCountColumn: 'Ouvertures',
    otherApps: 'Autres applications',
    tableView: 'En tableau',
    historyTitle: 'Les 14 derniers jours',
    historyHelp: 'Chaque barre est un jour. Touche une barre pour voir ce jour en détail.',
    today: "Aujourd'hui",
    selectedHeading: 'Jour sélectionné',
  },

  devices: {
    title: 'Appareils',
    fresh: 'envoie ses données',
    stale: 'n’envoie plus rien',
    staleHelp:
      'Plus rien depuis plus de 90 minutes. Tant que c’est le cas, les chiffres ci-dessus sont incomplets — pas forcément bas.',
    neverReported: 'n’a jamais rien envoyé',
    lastSeen: 'Dernier envoi',
    revoke: 'Déconnecter',
    revoked: 'déconnecté',
    addDevice: 'Connecter un téléphone',
    pairTitle: 'Connecter un téléphone',
    pairStep1: 'Ouvre Schirmziit sur le téléphone de l’enfant.',
    pairStep2: 'Scanne ce code — ou saisis les huit caractères.',
    pairStep3: 'C’est fait. Le téléphone envoie ensuite ses données environ toutes les 30 minutes.',
    codeExpires: 'Valable jusqu’à',
    codeLabel: 'Code',
  },

  help: {
    title: 'Comment fonctionne Schirmziit',
    intro:
      'Schirmziit te montre combien de temps et à quel moment le téléphone de ton enfant est utilisé. Rien n’est caché : l’enfant voit les mêmes chiffres sur son téléphone.',
    measuresTitle: 'Ce que Schirmziit mesure',
    measures: [
      'Quelle application était au premier plan et pendant combien de temps — par heure.',
      'Combien de fois le téléphone a été déverrouillé.',
      'À quel moment de la journée le téléphone a été utilisé.',
    ],
    notCollectedTitle: 'Ce que Schirmziit ne collecte pas',
    notCollected: [
      'Aucun contenu : ni messages, ni discussions, ni recherches, ni photos, ni frappes au clavier.',
      'Aucune position.',
      'Aucun site web ni aucune vidéo regardée.',
      'Aucun enregistrement du micro ou de la caméra.',
      'Rien qui bloque le téléphone — Schirmziit ne coupe aucune application.',
    ],
    howTitle: 'Comment ça marche techniquement',
    howSteps: [
      'Une petite application tourne sur le téléphone de l’enfant. Elle lit les statistiques d’utilisation qu’Android tient de toute façon.',
      'Environ toutes les 30 minutes, elle en calcule des valeurs horaires et les envoie à ton serveur.',
      'Sans internet, rien n’est perdu : les valeurs attendent sur le téléphone et partent plus tard.',
      'Ton serveur n’ajoute rien — il enregistre ce qui arrive et l’affiche ici.',
    ],
    whereTitle: 'Où sont les données',
    where:
      'Sur ton propre serveur, dans ta propre base de données. Aucune entreprise entre les deux, aucun compte chez un prestataire, aucune transmission à des tiers.',
    retentionTitle: 'Pendant combien de temps',
    retention:
      'Les valeurs horaires restent 13 mois, ensuite seuls les totaux journaliers. Tu peux supprimer toutes les données d’un enfant à tout moment — elles disparaissent, elles ne sont pas archivées.',
    childSeesTitle: 'Ce que voit l’enfant',
    childSees:
      'L’application est visible, a une icône et affiche en permanence un avis indiquant que le temps d’écran est transmis. Son écran explique, dans les mêmes mots, ce qui est envoyé et ce qui ne l’est pas. Ton enfant peut vérifier à tout moment ce qui a été envoyé.',
    stopTitle: 'Arrêter',
    stop:
      'Déconnecte l’appareil ici et le serveur n’accepte plus ses données. Ou désinstalle l’application sur le téléphone. Les deux prennent effet immédiatement.',
    notAControlTitle: 'Ce que Schirmziit n’est pas',
    notAControl:
      'Schirmziit ne bloque rien et ne filtre rien. C’est une base de discussion, pas une télécommande. Les limites de temps et les blocages sont volontairement une étape ultérieure et séparée.',
    resourcesTitle: 'Aide et recommandations',
    resourcesLead:
      'Schirmziit montre des chiffres, pas des conseils. Ce qui est raisonnable et ce qui aide en cas de conflit, ces organismes suisses l’expliquent mieux que nous :',
    resources: [
      {
        name: 'Jeunes et médias',
        note: 'La plateforme de la Confédération : repères par âge, règles, fiches pour les parents.',
        href: 'https://www.jeunesetmedias.ch/',
      },
      {
        name: 'Pro Juventute — temps d’écran',
        note: 'Des repères concrets par âge et des idées d’accords en famille.',
        href: 'https://www.projuventute.ch/fr/parents/medias-internet',
      },
      {
        name: 'Conseil 147',
        note: 'Conseil gratuit pour enfants et adolescents, 24h/24 — téléphone, chat ou SMS.',
        href: 'https://www.147.ch/',
      },
      {
        name: 'Zischtig.ch',
        note: 'Centre suisse de compétences médiatiques : soirées de parents, cours, conseil.',
        href: 'https://www.zischtig.ch/',
      },
    ],
  },

  errors: {
    generic: 'Quelque chose s’est mal passé.',
    notFound: 'Introuvable.',
    offline: 'Pas de connexion au serveur.',
  },

  units: { hoursShort: 'h', minutesShort: 'min' },
}
