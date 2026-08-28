import type { Site } from './strings'

export const fr: Site = {
  htmlLang: 'fr-CH',

  ogLocale: 'fr_CH',

  // The home page leads with the brand because it is the brand page; every
  // other title leads with what the page is, which is what someone scanning a
  // result list is looking for.
  meta: {
    home: {
      title: 'Schirmziit â Temps d’Ã©cran pour les familles',
      description:
        'Vois combien de temps et à quel moment le téléphone de ton enfant est utilisé. Aucun contenu, aucune position, rien de bloqué. Libre et auto-hébergeable.',
    },
    selfHost: {
      title: 'Auto-hébergement — Schirmziit',
      description:
        'Un programme et une base Postgres sur ton propre serveur. Docker Compose, reverse proxy, sauvegardes — étape par étape.',
    },
    hosted: {
      title: 'Version hébergée — Schirmziit',
      description:
        'Tu ne veux pas gÃ©rer de serveur. Nous hÃ©bergeons Schirmziit pour toi â petit, ouvert et en Suisse pour l’instant.',
    },
    privacy: {
      title: 'Confidentialité — Schirmziit',
      description:
        'Des durÃ©es d’utilisation par heure, rien d’autre. Aucun contenu, aucune position, aucune transmission. Ce qui est stockÃ© et qui le voit.',
    },
  },
  swissLabel: 'Développé en Suisse',
  nav: { home: 'Aperçu', selfHost: 'Auto-hébergement', hosted: 'Hébergé', privacy: 'Confidentialité' },

  alpha: {
    bannerTitle: 'Alpha privée',
    bannerBody:
      "Schirmziit est en plein développement et n'est pas encore publié. Ce que tu lis ici décrit ce qui fonctionne déjà — pas quelque chose à installer aujourd'hui pour ta famille.",
    bannerCta: "S'inscrire à la liste d'attente",
    title: "Liste d'attente",
    lead:
      'Laisse ton adresse e-mail et tu auras des nouvelles dès que Schirmziit sera publié. Un seul message, puis plus rien — pas de newsletter.',
    emailLabel: 'Adresse e-mail',
    placeholder: 'toi@example.ch',
    submit: "S'inscrire",
    sending: 'Inscription …',
    done: 'C\'est noté. On te fait signe dès que ça démarre.',
    invalid: "Cela ne ressemble pas à une adresse e-mail.",
    failed: "Ça n'a pas marché. Réessaie plus tard ou écris-nous un e-mail.",
    stored:
      "Nous enregistrons l'adresse et la langue de cette page, rien d'autre. Pas de pistage, pas de transmission. Écris-nous et l'entrée disparaît.",
    mailFallback:
      "Le formulaire a besoin de JavaScript. Écris-nous plutôt un court e-mail — cela suffit.",
    mailCta: 'Écrire un e-mail',
  },

  home: {
    kicker: 'Temps d’écran pour les familles',
    title: 'Le temps d’écran sous les yeux, pour protéger ton enfant',
    lead:
      'Schirmziit montre combien de temps et à quel moment de la journée le téléphone de ton enfant est utilisé, pour que tu remarques quand cela devient trop et que tu puisses en parler. Aucun contenu, aucune position, aucune télécommande.',
    ctaSelfHost: 'Auto-héberger',
    ctaHosted: 'Version hébergée',

    measuresTitle: 'Ce que Schirmziit mesure',
    measures: [
      'Quelle application était au premier plan et pendant combien de temps — par heure.',
      'Combien de fois le téléphone a été déverrouillé.',
      'À quel moment de la journée le téléphone a été utilisé.',
    ],
    neverTitle: 'Ce que Schirmziit ne collecte jamais',
    never: [
      'Ni messages, ni discussions, ni recherches, ni photos, ni frappes au clavier.',
      'Aucune position.',
      'Aucun site web ni aucune vidéo regardée.',
      'Aucun enregistrement du micro ou de la caméra.',
      'Rien n’est bloqué — Schirmziit ne coupe aucune application.',
    ],

    howTitle: 'Comment ça marche',
    how: [
      'Une petite application tourne sur le téléphone de l’enfant. Elle lit les statistiques d’utilisation qu’Android tient de toute façon.',
      'Environ toutes les 30 minutes, elle envoie des valeurs horaires à ton serveur — sans internet, les valeurs attendent sur le téléphone.',
      'Tu les consultes dans le navigateur ou dans l’application iPhone.',
    ],

    ribbonTitle: 'Pas seulement combien, mais quand',
    ribbonBody:
      'Une heure à 23 h ne veut pas dire la même chose qu’une heure après le repas de midi. Schirmziit dessine donc la journée comme un ruban de minuit à minuit — la forme de la journée se lit d’un coup d’œil.',

    childSeesTitle: 'L’enfant voit les mêmes chiffres',
    childSeesBody:
      'Ton tableau de bord montre les 14 derniers jours d’un coup d’œil ; touche un jour pour le voir heure par heure. L’application sur le téléphone de l’enfant montre exactement la même chose — les mêmes 14 jours, le même jour en détail, les mêmes chiffres. Cela reste une base commune de discussion, pas un contrôle qui tourne en arrière-plan.',

    platformsTitle: 'Appareils',
    platformsBody: 'Ce qui fonctionne aujourd’hui — et ce qui ne fonctionne pas.',
    androidLabel: 'Android',
    androidBody:
      'Complet : temps par application et par heure, déverrouillages, forme de la journée. À partir d’Android 8. Installé aujourd’hui en APK, pas encore dans le Play Store.',
    iosLabel: 'iPhone',
    iosBody:
      'Les deux rôles fonctionnent sur iPhone : le tableau de bord pour les parents, et désormais une vue pour l’enfant aussi. Un iPhone est mesuré également — pour cela l’app a besoin de l’accès au temps d’écran d’Apple, qu’Apple autorise app par app. Il vaut pour nos propres appareils de test ; pour la distribution via TestFlight ou l’App Store, l’autorisation manque encore. À partir d’iOS 17.',
    matrix: {
      title: 'Ce qui fonctionne où',
      lead:
        'L’état d’aujourd’hui, ligne par ligne. « Pas encore » veut dire prévu ou bloqué — la note précise lequel.',
      featureHeader: 'Fonction',
      columns: { android: 'Téléphone Android', ios: 'iPhone', web: 'Tableau de bord dans le navigateur' },
      groups: { measure: 'Mesurer sur le téléphone de l’enfant', view: 'Consulter' },
      legend: { yes: 'fonctionne', partial: 'en partie', no: 'pas encore' },
      notApplicable: 'non prévu ici',
      rows: {
        appHours: { label: 'Temps par application et par heure' },
        timeOfDay: { label: 'À quelle heure de la journée le téléphone a été utilisé' },
        unlocks: {
          label: 'Déverrouillages',
          note: 'iOS ne compte pas les déverrouillages. Ce qui est compté, c’est le nombre de fois où le téléphone a été pris en main — l’équivalent honnête le plus proche.',
        },
        background: {
          label: 'Son en arrière-plan, écran éteint',
          note: 'Sur Android seulement si tu autorises l’accès aux notifications ; iOS ne le fournit pas. Ce temps n’est jamais compté comme temps d’écran.',
        },
        appNames: {
          label: 'Noms des applications au lieu des noms de paquets',
          note: 'iOS retient certains noms, et l’identifiant de l’application s’affiche alors.',
        },
        offline: { label: 'Sans internet, les valeurs attendent sur le téléphone' },
        install: {
          label: 'Installable sans machine de développement',
          note: 'Android : un APK aujourd’hui, pas encore de Play Store. iPhone : TestFlight et l’App Store exigent l’autorisation d’Apple pour la distribution, qui manque encore.',
        },
        roles: {
          label: 'Rôle parent et rôle enfant dans une seule app',
          note: 'L’app Android est réservée au téléphone de l’enfant.',
        },
        overview14: { label: 'Les quatorze derniers jours d’un coup d’œil' },
        dayDetail: { label: 'Une journée, heure par heure' },
        weekComparison: {
          label: 'La semaine dernière comparée à la précédente',
          note: 'Le temps d’écran et les soirées dès 21:00, avec les applications qui ont changé. Sept jours terminés : la journée en cours n’est pas comparée, elle n’est pas finie.',
        },
        childOwnNumbers: { label: 'L’enfant voit les mêmes chiffres sur son téléphone' },
        manageChildren: { label: 'Ajouter et retirer un enfant' },
        revokeDevice: { label: 'Déconnecter un téléphone' },
        pairingCode: {
          label: 'Créer un code de connexion pour le téléphone d’un enfant',
          note: 'Affiché comme QR code et comme six caractères, dans le tableau de bord et dans les deux applications. Le téléphone de l’enfant scanne le code, ou quelqu’un saisit les six caractères et l’adresse du serveur.',
        },
        scanCode: {
          label: 'Se connecter en scannant le code',
          note: 'Les deux applications lisent le carré elles-mêmes. Le scan remplit l’adresse et le code ; la connexion se fait ensuite par un appui, car un code ne sert qu’une fois.',
        },
        deleteData: {
          label: 'Supprimer les chiffres enregistrés d’un enfant',
          note: 'Supprime les chiffres horaires et les totaux journaliers et indique combien de lignes ont disparu. L’enfant reste connecté et continue d’envoyer.',
        },
        helpLinks: {
          label: 'Aide et services de conseil suisses',
        },
        languages: { label: 'Allemand, français, italien, anglais' },
      },
    },

    openTitle: 'Libre et vérifiable',
    openBody:
      'Schirmziit est open source. Tu peux lire ce qui est envoyé et l’héberger toi-même — même si un jour nous arrêtons.',
  },

  choose: {
    title: 'Deux voies',
    selfHostTitle: 'Auto-hébergement',
    selfHostFor: 'Pour toi si tu fais déjà tourner un serveur ou un Raspberry Pi.',
    selfHostPoints: [
      'Deux conteneurs : Schirmziit et Postgres.',
      'Les données restent sur ton matériel, dans ta base de données.',
      'Mises à jour, sauvegardes et TLS sont à ta charge.',
      'Gratuit, sans compte chez nous.',
    ],
    hostedTitle: 'Hébergé (alpha privée)',
    hostedFor: 'Pour toi si tu ne veux pas gérer de serveur.',
    hostedPoints: [
      'Nous le faisons tourner — pour l’instant sur notre propre homelab en Suisse.',
      'Les données sont en Suisse, pas chez un géant du cloud.',
      'Gratuit pendant l’alpha, avec un nombre de places limité.',
      'Tu peux passer à l’auto-hébergement quand tu veux : c’est le même logiciel.',
    ],
  },

  selfHost: {
    title: 'Auto-hébergement',
    lead:
      'Schirmziit est un seul programme qui sert lui-même son tableau de bord, plus une base Postgres. Pas de Redis, pas de broker de messages, aucun service cloud.',
    needTitle: 'Ce qu’il te faut',
    need: [
      'Une machine avec Docker — un Raspberry Pi 4 suffit.',
      'Une adresse que le téléphone de l’enfant peut atteindre, avec TLS devant (Caddy, Traefik ou nginx).',
      'Environ 200 Mo de stockage pour la première année de données.',
    ],
    stepsTitle: 'Installation',
    proxyTitle: 'Reverse proxy et TLS',
    proxyBody:
      'Schirmziit n’écoute que sur 127.0.0.1:8080. Place un reverse proxy devant pour terminer TLS et donne à PUBLIC_URL exactement l’adresse que tu saisis dans le navigateur. Cette adresse est intégrée au code QR d’appairage : si elle est fausse, le téléphone s’appaire une fois puis n’envoie plus rien.',
    firstUserTitle: 'Le premier compte',
    firstUserBody:
      'Par défaut, un seul compte peut s’enregistrer, puis l’inscription se ferme. Ouvre le tableau de bord, crée ton compte, puis mets ALLOW_REGISTRATION sur « off ».',
    pairTitle: 'Connecter un téléphone',
    pairBody:
      'Ajoute un enfant dans le tableau de bord et génère un code. Installe l’application Android sur le téléphone de l’enfant, autorise l’accès aux données d’utilisation et scanne le code. Ensuite, le téléphone envoie ses données environ toutes les 30 minutes.',
    backupTitle: 'Sauvegardes',
    backupBody:
      'Tout ce qui compte est dans Postgres. Un pg_dump nocturne du volume suffit ; le tableau de bord lui-même n’a pas d’état.',
    upgradeTitle: 'Mises à jour',
    upgradeBody:
      'docker compose pull puis docker compose up -d. Les migrations de base de données s’exécutent au démarrage. Le retour à une version antérieure n’est pas prévu : fais une sauvegarde d’abord.',
    troubleTitle: 'Quand quelque chose ne marche pas',
    trouble: [
      {
        problem: 'Le téléphone s’appaire mais n’envoie jamais rien.',
        fix: 'PUBLIC_URL ne pointe pas sur l’adresse que le téléphone peut atteindre. Corrige, redémarre et génère un nouveau code.',
      },
      {
        problem: 'Le tableau de bord affiche « n’envoie plus rien ».',
        fix: 'Vérifie sur le téléphone que l’accès aux données d’utilisation est toujours autorisé, et autorise les mises à jour en arrière-plan si l’application le demande.',
      },
      {
        problem: 'Postgres ne démarre plus après une mise à jour.',
        fix: 'Depuis Postgres 18, le volume doit être monté sur /var/lib/postgresql et non /var/lib/postgresql/data. Notre fichier compose le fait déjà.',
      },
      {
        problem: 'Une application apparaît comme « com.quelquechose.app ».',
        fix: 'Le téléphone n’a pas pu résoudre son nom. Après la prochaine mise à jour de l’application Android et un nouvel envoi, le vrai nom apparaît.',
      },
    ],
  },

  hosted: {
    title: 'Version hébergée',
    lead:
      'Tu ne veux pas gérer de serveur. Nous le faisons — petit, ouvert et en Suisse pour l’instant.',
    whereTitle: 'Où sont les données',
    whereBody:
      'Sur notre propre matériel en Suisse, pas chez un grand fournisseur cloud. Le même logiciel que tu pourrais héberger toi-même, avec les mêmes limites : les durées d’utilisation oui, les contenus non.',
    scaleTitle: 'À quel point c’est petit, honnêtement',
    scaleBody:
      'Cela tourne aujourd’hui sur un homelab géré par une seule personne. C’est suffisant pour les familles d’une alpha, et ce n’est pas déguisé en entreprise avec une équipe de garde. Si assez de gens participent, nous construirons cela correctement.',
    priceTitle: 'Prix',
    priceBody:
      'Gratuit pendant l’alpha. Plus tard, il faudra bien que cela coûte quelque chose pour s’autofinancer — l’auto-hébergement restera gratuit dans tous les cas.',
    joinTitle: 'Participer',
    joinBody:
      'Envoie-nous un court e-mail en indiquant le système d’exploitation du téléphone de l’enfant. Nous te répondrons dès qu’une place se libère.',
    joinCta: 'Demander une place en alpha',
  },

  privacy: {
    title: 'Confidentialité',
    lead:
      'En bref : des durées d’utilisation par heure, rien d’autre. Aucun contenu, aucune position, aucune transmission.',
    sections: [
      {
        title: 'Ce qui est enregistré',
        body: 'Par heure et par application : le temps au premier plan et le nombre d’ouvertures. Par heure et par appareil : le temps d’écran allumé et les déverrouillages. Plus le nom d’application fourni par le téléphone, et le prénom que tu donnes à l’enfant.',
      },
      {
        title: 'Ce qui n’est pas enregistré',
        body: 'Ni messages, discussions, recherches, photos, frappes au clavier, sites web, vidéos, données de micro ou de caméra, ni position. Schirmziit ne demande même pas ces autorisations.',
      },
      {
        title: 'Pendant combien de temps',
        body: 'Les valeurs horaires 13 mois, ensuite seuls les totaux journaliers. Tu peux supprimer les données d’un enfant à tout moment — elles disparaissent, elles ne sont pas archivées.',
      },
      {
        title: 'Qui peut les voir',
        body: 'Ton compte, et l’enfant sur son propre téléphone. En auto-hébergement, personne d’autre. Sur l’alpha hébergée, techniquement aussi l’exploitant qui administre la base de données — et personne au-delà.',
      },
      {
        title: 'Tiers',
        body: 'Aucun. Pas d’analytics sur le téléphone de l’enfant, pas de SDK publicitaire, pas de rapport de crash, aucune transmission.',
      },
      {
        title: "Liste d'attente",
        body: "Si tu t'inscris à la liste d'attente, nous enregistrons ton adresse e-mail et la langue dans laquelle tu as lu le site. Uniquement pour cela : un e-mail quand Schirmziit sera publié. Pas de newsletter, pas de transmission. Écris-nous et nous supprimons l'entrée.",
      },
    ],
    analyticsTitle: 'Ce site web',
    analyticsBody:
      'Ce site compte les visites avec une instance Umami auto-hébergée : pas de cookies, pas de stockage d’IP, rien transmis à des tiers. Nous voulons seulement savoir si quelqu’un lit.',
  },

  resources: {
    title: 'Aide et recommandations',
    lead:
      'Schirmziit montre des chiffres, pas des conseils. Quelle durée d’écran est raisonnable, et quoi faire quand cela tourne à la dispute, ces organisations suisses l’expliquent mieux que nous :',
    items: [
      {
        name: 'Jeunes et médias',
        note: 'La plateforme nationale de la Confédération : repères par âge, règles, fiches pour les parents.',
        href: 'https://www.jugendundmedien.ch/fr',
      },
      {
        name: 'Pro Juventute',
        note: 'Valeurs indicatives par âge et conseils pour fixer des accords en famille.',
        href: 'https://www.projuventute.ch/fr',
      },
      {
        name: 'Conseils 147',
        note: 'Conseil gratuit pour les enfants et les jeunes, 24 h sur 24, par téléphone, chat ou SMS.',
        href: 'https://www.147.ch/',
      },
      {
        name: 'Addiction Suisse',
        note: 'Information et aide sur les usages problématiques des écrans.',
        href: 'https://www.addictionsuisse.ch/',
      },
    ],
  },

  footer: { madeIn: 'Développé en Suisse', source: 'Code source', contact: 'Contact' },
}
