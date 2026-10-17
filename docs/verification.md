# Vérifications de livraison — 17 octobre 2026

## Résultats observés

| Vérification | Résultat |
|---|---|
| Maven `verify` | 21 tests backend réussis, aucun échec ni test ignoré |
| Architecture Java | 4 règles ArchUnit : indépendance du cœur, dépendances vers le cœur, isolation des adaptateurs et absence de cycles |
| PostgreSQL éphémère des tests | Migrations, transactions, concurrence de déduplication, filtres et API HTTP validés |
| Angular 22 / Node 24.15 | 15 tests réussis ; build de production et contrôle Prettier réussis |
| Flux Angular | Annulation des requêtes obsolètes, reprise après erreur, curseurs et libération des abonnements validés |
| Téléchargement | Octets binaires et texte UTF-8 avec accents et symbole euro préservés |
| Images Docker | Backend exécuté avec Java 21 ; frontend servi par Nginx non privilégié |
| IBM MQ réel | Image IBM MQ 9.4.5.1, queue manager QM1, connexion authentifiée au canal de développement |
| PostgreSQL réel | PostgreSQL 17 dans Docker, stockage durable sur volume |
| MQ → SQL → REST | Messages persistants publiés dans MQ, archivés puis consultés via l’API |
| Quarantaine | MapMessage refusé, référence SQL créée et message original présent dans la file MQ de quarantaine |
| Panne SQL et reprise | API 503, messages maintenus dans MQ, 12 messages supplémentaires archivés après relance de PostgreSQL |
| Interface navigateur | Liste réelle, filtres source/statut, pagination, dates françaises, détail et téléchargement du contenu vérifiés |
| Interface mobile | Affichage à 390 px vérifié ; aucun débordement horizontal global, défilement du tableau contenu dans son panneau |
| Exclusion des secrets | Mots de passe locaux absents des fichiers suivis par Git |

Les scripts reproductibles sont `scripts/smoke-test.ps1` et `scripts/resilience-test.ps1`. Ils ont été exécutés après la refonte hexagonale. Le pipeline GitHub Actions est configuré pour exécuter les tests backend, frontend, le contrôle de formatage et les deux scripts avec Docker. L'interface reste centrée sur la recherche et le détail, sans statistiques ni rafraîchissement automatique.

## Portée des résultats

Il s’agit de tests fonctionnels et de reprise sur un environnement local de développement. Aucun test de charge soutenu, exercice de bascule HA ou certification de sécurité n’a été réalisé. Le scénario `scripts/load.js` fournit un point de départ k6 pour mesurer les lectures REST sur l’infrastructure cible ; il ne mesure pas à lui seul le débit d’ingestion MQ.

Les tests de déduplication concurrente utilisent 24 tentatives pour un même identifiant, dont une seule insertion réussit. Le comportement dans la fenêtre commit SQL / commit MQ repose sur cette contrainte unique et sur la propagation des erreurs du listener. Aucun arrêt forcé précisément injecté entre les deux commits n’a été réalisé.
