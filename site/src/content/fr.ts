import type { Site } from './strings'

export const fr: Site = {
  htmlLang: 'fr-CH',
  swissLabel: 'Développé en Suisse',
  nav: { home: 'Aperçu', selfHost: 'Auto-hébergement', hosted: 'Hébergé', privacy: 'Confidentialité' },

  home: {
    kicker: 'Temps d’écran pour les familles',
    title: 'Voir combien de temps et à quel moment — sans agir en cachette',
    lead:
      'Nestling montre combien de temps et à quel moment de la journée le téléphone de ton enfant est utilisé. L’enfant voit les mêmes chiffres sur son propre téléphone. Aucun contenu, aucune position, aucune télécommande.',
    ctaSelfHost: 'Auto-héberger',
    ctaHosted: 'Version hébergée',

    measuresTitle: 'Ce que Nestling mesure',
    measures: [
      'Quelle application était au premier plan et pendant combien de temps — par heure.',
      'Combien de fois le téléphone a été déverrouillé.',
      'À quel moment de la journée le téléphone a été utilisé.',
    ],
    neverTitle: 'Ce que Nestling ne collecte jamais',
    never: [
      'Ni messages, ni discussions, ni recherches, ni photos, ni frappes au clavier.',
      'Aucune position.',
      'Aucun site web ni aucune vidéo regardée.',
      'Aucun enregistrement du micro ou de la caméra.',
      'Rien n’est bloqué — Nestling ne coupe aucune application.',
    ],

    howTitle: 'Comment ça marche',
    how: [
      'Une petite application tourne sur le téléphone de l’enfant. Elle lit les statistiques d’utilisation qu’Android tient de toute façon.',
      'Environ toutes les 30 minutes, elle envoie des valeurs horaires à ton serveur — sans internet, les valeurs attendent sur le téléphone.',
      'Tu les consultes dans le navigateur ou dans l’application iPhone.',
    ],

    ribbonTitle: 'Pas seulement combien, mais quand',
    ribbonBody:
      'Une heure à 23 h ne veut pas dire la même chose qu’une heure après le repas de midi. Nestling dessine donc la journée comme un ruban de minuit à minuit — la forme de la journée se lit d’un coup d’œil.',

    honestTitle: 'Ouvert, pas caché',
    honestBody:
      'L’application sur le téléphone de l’enfant est visible, a une icône et affiche en permanence un avis indiquant que le temps d’écran est transmis. Son écran explique, dans les mêmes mots, ce qui est envoyé et ce qui ne l’est pas. Pour surveiller en cachette, ce n’est pas le bon outil.',

    platformsTitle: 'Appareils',
    platformsBody: 'Ce qui fonctionne aujourd’hui — et ce qui ne fonctionne pas.',
    androidLabel: 'Android',
    androidBody:
      'Complet : temps par application et par heure, déverrouillages, forme de la journée. À partir d’Android 8.',
    iosLabel: 'iPhone',
    iosBody:
      'Pour l’instant uniquement comme vue pour les parents. Lire le temps d’écran sur un iPhone exige une autorisation d’Apple que nous n’avons pas encore — nous le dirons ici dès que cela change.',

    openTitle: 'Libre et vérifiable',
    openBody:
      'Nestling est open source. Tu peux lire ce qui est envoyé et l’héberger toi-même — même si un jour nous arrêtons.',
  },

  choose: {
    title: 'Deux voies',
    selfHostTitle: 'Auto-hébergement',
    selfHostFor: 'Pour toi si tu fais déjà tourner un serveur ou un Raspberry Pi.',
    selfHostPoints: [
      'Deux conteneurs : Nestling et Postgres.',
      'Les données restent sur ton matériel, dans ta base de données.',
      'Mises à jour, sauvegardes et TLS sont à ta charge.',
      'Gratuit, sans compte chez nous.',
    ],
    hostedTitle: 'Hébergé (bêta)',
    hostedFor: 'Pour toi si tu ne veux pas gérer de serveur.',
    hostedPoints: [
      'Nous le faisons tourner — pour l’instant sur notre propre homelab en Suisse.',
      'Les données sont en Suisse, pas chez un géant du cloud.',
      'Gratuit pendant la bêta, avec un nombre de places limité.',
      'Tu peux passer à l’auto-hébergement quand tu veux : c’est le même logiciel.',
    ],
  },

  selfHost: {
    title: 'Auto-hébergement',
    lead:
      'Nestling est un seul programme qui sert lui-même son tableau de bord, plus une base Postgres. Pas de Redis, pas de broker de messages, aucun service cloud.',
    needTitle: 'Ce qu’il te faut',
    need: [
      'Une machine avec Docker — un Raspberry Pi 4 suffit.',
      'Une adresse que le téléphone de l’enfant peut atteindre, avec TLS devant (Caddy, Traefik ou nginx).',
      'Environ 200 Mo de stockage pour la première année de données.',
    ],
    stepsTitle: 'Installation',
    proxyTitle: 'Reverse proxy et TLS',
    proxyBody:
      'Nestling n’écoute que sur 127.0.0.1:8080. Place un reverse proxy devant pour terminer TLS et donne à PUBLIC_URL exactement l’adresse que tu saisis dans le navigateur. Cette adresse est intégrée au code QR d’appairage : si elle est fausse, le téléphone s’appaire une fois puis n’envoie plus rien.',
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
    betaTitle: 'À quel point c’est petit, honnêtement',
    betaBody:
      'Cela tourne aujourd’hui sur un homelab géré par une seule personne. C’est suffisant pour les familles d’une bêta, et ce n’est pas déguisé en entreprise avec une équipe de garde. Si assez de gens participent, nous construirons cela correctement.',
    priceTitle: 'Prix',
    priceBody:
      'Gratuit pendant la bêta. Plus tard, il faudra bien que cela coûte quelque chose pour s’autofinancer — l’auto-hébergement restera gratuit dans tous les cas.',
    joinTitle: 'Participer',
    joinBody:
      'Envoie-nous un court e-mail en indiquant le système d’exploitation du téléphone de l’enfant. Nous te répondrons dès qu’une place se libère.',
    joinCta: 'Demander une place en bêta',
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
        body: 'Ni messages, discussions, recherches, photos, frappes au clavier, sites web, vidéos, données de micro ou de caméra, ni position. Nestling ne demande même pas ces autorisations.',
      },
      {
        title: 'Pendant combien de temps',
        body: 'Les valeurs horaires 13 mois, ensuite seuls les totaux journaliers. Tu peux supprimer les données d’un enfant à tout moment — elles disparaissent, elles ne sont pas archivées.',
      },
      {
        title: 'Qui peut les voir',
        body: 'Ton compte, et l’enfant sur son propre téléphone. En auto-hébergement, personne d’autre. Sur la bêta hébergée, techniquement aussi l’exploitant qui administre la base de données — et personne au-delà.',
      },
      {
        title: 'Tiers',
        body: 'Aucun. Pas d’analytics sur le téléphone de l’enfant, pas de SDK publicitaire, pas de rapport de crash, aucune transmission.',
      },
    ],
    analyticsTitle: 'Ce site web',
    analyticsBody:
      'Ce site compte les visites avec une instance Umami auto-hébergée : pas de cookies, pas de stockage d’IP, rien transmis à des tiers. Nous voulons seulement savoir si quelqu’un lit.',
  },

  footer: { madeIn: 'Développé en Suisse', source: 'Code source', contact: 'Contact' },
}
