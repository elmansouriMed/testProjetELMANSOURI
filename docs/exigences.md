# Correspondance avec le besoin client

Auteur : ELMANSOURI Mohamed.

| Exigence | Réalisation | Vérification |
|---|---|---|
| Lire les messages IBM MQ | Listener JMS sur une file configurable | Producteur de test et script `smoke-test.ps1` |
| Stocker dans une base relationnelle | PostgreSQL, Spring Data JPA et migrations Flyway | Tests d’intégration avec PostgreSQL |
| Exposer des API REST | Liste paginée et détail d’un message | Tests HTTP et contrat OpenAPI |
| Fournir une IHM | Une page Angular : recherche, pagination et détail | Tests Angular et contrôle dans le navigateur |
| Performance | Nombre de consommateurs borné, pool SQL, index et pagination par curseur | Tests de concurrence et de projection SQL ; débit à mesurer sur la cible |
| Résilience | Commit SQL avant acquittement MQ, déduplication et reconnexion | Tests de rollback, doublons et `resilience-test.ps1` |
| Java 21+ et Spring Boot 4+ | Compilation Java 21, Spring Boot 4.1.1 | Maven `verify` et exécution Java 21 dans Docker |
| Angular 22 | Angular 22.2.3 | Tests et build de production |
| Maven | Projet parent avec un seul module backend | Wrapper Maven fourni |
| Docker | Compose : MQ, PostgreSQL, backend et frontend | Test de bout en bout |
| Documentation de test | README et scripts reproductibles | Commandes exécutées pendant la validation |
| Code sur GitHub | Dépôt `elmansouriMed/testProjetELMANSOURI` | Historique Git et workflow de validation |
| Authentification hors périmètre | Aucune authentification applicative | Vérification du périmètre |

## Organisation et qualité de code

Le backend organise la fonctionnalité `messages` en domaine, cas d'utilisation avec ports et infrastructure. Les adaptateurs JMS, REST et JPA dépendent de ces contrats. Un décorateur assure la transaction SQL avant l'acquittement MQ. MapStruct génère les DTO REST ; l'entité JPA porte un `@Version` testé avec deux transactions concurrentes. Des tests ArchUnit empêchent les dépendances inverses et les cycles. Un conseil REST centralise les exceptions et les réponses `ProblemDetail`.

Le [guide des classes](classes.md) explique chaque responsabilité, les relations entre les couches, les principes SOLID appliqués et les suites de tests correspondantes.

Le frontend suit `core`, `shared` et `features/messages`. La page orchestre des composants de présentation distincts ; le client REST typé et la façade RxJS/signals gèrent les données et leur cycle de vie. Les routes sont chargées à la demande, les composants sont standalone et `OnPush`, et les formulaires réactifs sont typés. Le mode zoneless est activé. Le frontend ne dépend pas d'une bibliothèque externe de gestion d'état ou de composants graphiques.

L'interface reste centrée sur la consultation demandée : recherche explicite, pagination et détail. L'identifiant MQ, la source et le statut sont visibles immédiatement ; corrélation et dates restent accessibles dans les filtres avancés. Les protections contre la perte et les doublons restent indispensables au traitement des paiements. Les exemples `users`, `products`, authentification et autorisations ne créent pas de fonctionnalités supplémentaires dans ce projet.

## Points à préciser avec le client

Le débit attendu, la taille habituelle des messages, la durée de rétention et les objectifs de reprise ne sont pas chiffrés. Les mesures de charge et le dimensionnement de production dépendent de ces informations. Les destinations et les règles de routage métier ne sont pas spécifiées dans les fonctions à réaliser.
