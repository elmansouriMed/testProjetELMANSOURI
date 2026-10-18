# Vérifications de livraison — 18 octobre 2026

## Résultats observés

| Vérification | Résultat |
|---|---|
| Maven `verify` | 71 tests backend réussis, dont 20 cas d'intégration PostgreSQL/HTTP ; aucun échec ni test ignoré |
| Persistance JPA | Projection sans payload, sans COUNT global ni OFFSET ; déduplication concurrente et commit SQL avant PUT validés |
| Exceptions REST | 8 tests du conseil REST : réponses ProblemDetail 400/404/409/503/500 et absence de fuite technique |
| MapStruct | 5 tests de l'implémentation générée : UTF-8, Base64, quarantaine, pagination et précision des identifiants |
| Verrouillage optimiste | Deux transactions JPA sur la même archive ; seconde validation refusée, version finale à 1 et rejeu sans incrément |
| Architecture Java | 4 règles ArchUnit : indépendance du cœur, dépendances vers le cœur, isolation des adaptateurs et absence de cycles |
| PostgreSQL éphémère des tests | Migrations, transactions, concurrence de déduplication, filtres et API HTTP validés |
| Angular 22 / Node 24.15 | 29 tests réussis, dont 4 tests d'intégration de la page ; build de production et contrôle Prettier réussis |
| Flux Angular | Annulation des requêtes obsolètes, reprise après erreur, curseurs et libération des abonnements validés |
| Consultation du contenu | Texte affiché sans interprétation HTML, binaire visible en Base64 et quarantaine expliquée sans contenu fictif |
| Images Docker | Backend exécuté avec Java 21 ; frontend servi par Nginx non privilégié |
| IBM MQ réel | Image IBM MQ 9.4.5.1, queue manager QM1, connexion authentifiée au canal de développement |
| PostgreSQL réel | PostgreSQL 17 dans Docker, stockage durable sur volume |
| MQ → SQL → REST | Messages persistants publiés dans MQ, archivés puis consultés via l’API |
| Quarantaine | MapMessage refusé, référence SQL créée et message original présent dans la file MQ de quarantaine |
| Panne SQL et reprise | API 503, messages maintenus dans MQ, 12 messages supplémentaires archivés après relance de PostgreSQL |
| Interface navigateur | Liste réelle, filtres principaux et avancés, réinitialisation, pagination, dates françaises et détail vérifiés |
| Interface mobile | Affichage à 390 px vérifié ; aucun débordement horizontal global, défilement du tableau contenu dans son panneau |
| Exclusion des secrets | Mots de passe locaux absents des fichiers suivis par Git |

Les scripts reproductibles sont `scripts/smoke-test.ps1` et `scripts/resilience-test.ps1`. Ils ont été exécutés après la refonte hexagonale. Le pipeline GitHub Actions est configuré pour exécuter les tests backend, frontend, le contrôle de formatage et les deux scripts avec Docker. L'interface reste centrée sur la recherche et le détail, sans statistiques ni rafraîchissement automatique.

## Portée des résultats

Il s'agit de tests fonctionnels et de reprise sur un environnement local de développement. Aucun test de charge soutenu, exercice de bascule HA ou certification de sécurité n'a été réalisé. Le débit d'ingestion et de consultation reste à mesurer selon la volumétrie et l'infrastructure du client.

Les tests de déduplication concurrente utilisent 24 tentatives pour un même identifiant, dont une seule insertion réussit. Le comportement dans la fenêtre commit SQL / commit MQ repose sur cette contrainte unique et sur la propagation des erreurs du listener. Aucun arrêt forcé précisément injecté entre les deux commits n’a été réalisé.

Un test d'intégration vérifie la visibilité du commit SQL avec une seconde connexion PostgreSQL au moment de l'appel du producteur de quarantaine simulé. Les frontières de simulation, les cas couverts et les commandes sont décrits dans [le plan de tests](tests.md). La suite complète compte 100 cas.
