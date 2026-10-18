# Architecture et décisions

## Périmètre

Le système reçoit, archive et expose les messages à la consultation. Le contexte évoque un routage vers d’autres destinations, mais les fonctions demandées ne spécifient ni règles ni protocoles de routage. Aucun transfert métier vers d’autres applications n’est implémenté. Le transfert en quarantaine est une gestion technique des formats refusés. L’authentification et les autorisations applicatives sont hors périmètre.

## Transaction de réception

1. Le `DefaultMessageListenerContainer` ouvre une transaction JMS locale et reçoit le message.
2. `JmsMessageDecoder` extrait l’identifiant JMS/MQ, la corrélation, la source et le contenu.
3. `MessageMqAdapter` appelle le port `IngestMessageUseCase`. Son implémentation est le décorateur Spring `TransactionalMessageIngestion`, sous `@Transactional`, qui délègue à `ArchiveMessageService` puis au port `MessageArchivePort`. Le repository Spring Data JPA applique une insertion native `INSERT ... ON CONFLICT DO NOTHING` ; la contrainte unique arbitre les doublons, y compris entre instances concurrentes.
4. Le commit PostgreSQL est terminé avant le retour du service vers le listener.
5. Si le contenu est refusé, le listener envoie le message d’origine en quarantaine avec **la même session JMS**, en livraison persistante et sans expiration.
6. Au retour du listener, le conteneur commit la transaction MQ. En cas d’exception, il rollback la réception et l’éventuel envoi en quarantaine.

| Point de panne | Conséquence et reprise |
|---|---|
| Avant commit SQL | Pas de nouvelle ligne durable ; rollback MQ puis nouvelle livraison |
| Après commit SQL, avant commit MQ | Ligne déjà présente ; nouvelle livraison puis conflit unique sans insertion |
| Envoi vers la quarantaine refusé | La référence SQL peut être visible ; le message reste dans la file d’entrée et l’envoi est retenté |
| Après commit MQ | Le message est archivé ; un rejet est aussi présent dans la file de quarantaine |
| SQL indisponible | Pas d’acquittement ; nouvelles tentatives espacées d’une seconde par consommateur |
| MQ indisponible | Reconnexion du client et du conteneur ; archives existantes consultables tant que SQL fonctionne |

Le champ `receivedAt` décrit le **premier archivage réussi**, sans modification à chaque tentative. Deux messages avec des contenus identiques et des identifiants MQ différents restent deux messages distincts. Le producteur doit fournir des messages persistants, avec une durée de vie compatible avec les délais de reprise ; l’application ne peut empêcher l’expiration décidée par le producteur ou la perte d’un message non persistant lors d’une panne MQ.

L'archivage en base et la mise en quarantaine sont deux commits de ressources distinctes : une ligne `QUARANTINED` peut être visible avant que l'envoi MQ aboutisse. L'IHM affiche le motif de rejet sans annoncer la fin du transfert MQ. Le nouveau message de quarantaine peut recevoir un nouvel identifiant MQ ; la référence SQL conserve l'identifiant reçu sur la file d'entrée.

La déduplication repose sur la conservation de la clé `(source_queue, mq_message_id)`. Une politique de purge doit conserver ces clés au moins pendant la fenêtre maximale de rejeu (ou les déplacer dans un registre de déduplication). Elle n’est pas fondée sur un hash de contenu, qui supprimerait des paiements légitimement identiques.

## MQ et messages problématiques

Les files de développement sont persistantes et limitées à 4 Mio par message. Le seuil d’acceptation applicatif est de 1 Mio. Un `TextMessage` doit être matérialisé avant sa taille UTF-8 ; `MAXMSGL` protège donc également la mémoire. Pour un `BytesMessage`, la longueur est contrôlée avant allocation.

`BOTHRESH(0)` désactive le déplacement automatique du client JMS vers une backout queue. Ce point est essentiel : une panne SQL prolongée ne doit pas déplacer un message sans référence SQL. Les formats refusés sont gérés explicitement par le listener. Si la quarantaine est pleine ou inaccessible, la réception est annulée et une alerte d’exploitation est nécessaire. Un identifiant MQ absent ou une erreur de lecture de métadonnées provoque aussi une reprise ; cela requiert une intervention si le message est structurellement illisible.

Les modèles contiennent l'identifiant MQ, la corrélation, la source, la file, le type, le contenu, le statut et le motif éventuel de rejet. La lecture ajoute l'identifiant SQL et la date de réception. `sourceApplication` manque : valeur `UNKNOWN` ; dépasse 128 caractères : tronquée à 128. Le texte est conservé comme contenu sémantique UTF-8, pas comme une capture brute des octets du protocole MQ.

La date d'émission, la taille stockée et l'indicateur de relivraison ont été retirés des modèles et des réponses REST. Le contrôle de taille reste local au décodage. La migration V2 rend les anciennes colonnes techniques optionnelles et conserve les valeurs déjà archivées ; V1 reste inchangée. L'entité JPA ne mappe que les champs utiles.

## Performance

Spring Data JPA construit les filtres avec `Specification`. L'API fluide projette les résultats en `MessageSummary` et limite la sélection aux métadonnées. La limite est fixée à la taille de page plus un pour détecter la suite sans requête `COUNT`. Un test inspecte le SQL produit pour vérifier l'absence de contenu binaire et de comptage global. `open-in-view` est désactivé et Hibernate valide le schéma géré par Flyway.

Les insertions unitaires utilisent une requête native dans le repository. `ON CONFLICT DO NOTHING` évite de mettre la transaction en échec lors d'un doublon concurrent, ce qu'un simple `save()` ne garantit pas. Chaque consommateur attend le commit SQL ; le pool et les consommateurs bornent la concurrence. Le débit doit être mesuré sur l'infrastructure cible.

La pagination utilise une clé `id < beforeId`, avec tri décroissant et limite plafonnée. Les filtres sont paramétrés, sans concaténation de saisie utilisateur. Les index portent sur la date, le statut, la source et la corrélation. Les index ne couvrent pas toutes les combinaisons possibles ; utiliser `EXPLAIN (ANALYZE, BUFFERS)` avec la distribution réelle avant d’en ajouter.

L’identité SQL correspond à l’ordre d’allocation, pas à un horodatage global ni à l’ordre des commits. La consultation est vivante et ne constitue pas un export transactionnel figé : une transaction tardive ou de nouveaux messages nécessitent une actualisation. Le contrat évite de promettre un instantané global.

Les lectures restent limitées à une page de métadonnées ou au contenu du message sélectionné. Le dimensionnement, la rétention et le partitionnement seront fixés avec les objectifs de volumétrie du client.

## Conception hexagonale du backend

Le module fonctionnel `messages` possède trois couches :

- `domain` : records immuables, types énumérés et invariants. Les tableaux d'octets sont copiés pour ne pas exposer un état mutable.
- `application` : ports entrants `IngestMessageUseCase` et `ConsultMessagesUseCase`, port sortant `MessageArchivePort` et services Java. Aucun import Spring, JDBC, JMS ou IBM.
- `infrastructure` : adaptateurs MQ, REST et PostgreSQL, mapping des DTO, transaction et configuration Spring.

```mermaid
flowchart LR
  MQ[Adaptateur JMS] --> Ingest[Port ingestion]
  REST[Adaptateur REST] --> Consult[Port consultation]
  Ingest --> TX[Décorateur transactionnel]
  TX --> Archive[Service archivage]
  Consult --> Query[Service consultation]
  Archive --> Port[Port de stockage]
  Query --> Port
  JPA[Adaptateur Spring Data JPA] -. implémente .-> Port
  JPA --> DB[(PostgreSQL)]
```

`MessageBeanConfiguration` assemble les services et le décorateur. Le bean d'ingestion injecté dans l'adaptateur MQ est le décorateur proxifié par Spring : la transaction SQL est donc terminée avant le retour au listener. Le service métier ne dépend pas de la transaction Spring et n'est pas enregistré une seconde fois comme bean d'ingestion.

Les DTO HTTP restent dans l'adaptateur REST. `MessageResponseMapper` déclare le mapping MapStruct des records ; Maven génère son implémentation Spring à la compilation. `unmappedTargetPolicy = ERROR` empêche d'oublier un nouveau champ cible. Les identifiants 64 bits deviennent des chaînes ; deux conversions nommées encodent le contenu en texte UTF-8 ou Base64. Le domaine et l'application ne dépendent ni de MapStruct ni des DTO. Le décodage JMS emploie le pattern matching de Java 21.

### Verrouillage optimiste

`MessageEntity.version`, annoté `@Version`, est une métadonnée interne. La migration V3 ajoute une colonne `BIGINT NOT NULL DEFAULT 0` et initialise les archives existantes sans modifier leur contenu. Les insertions natives utilisent cette valeur par défaut ; un rejeu ignoré ne modifie pas la version.

Hibernate contrôle et incrémente la version lors de ses écritures d'entités. Le test d'intégration charge une même archive dans deux contextes de persistance, demande `OPTIMISTIC_FORCE_INCREMENT`, valide la première transaction puis vérifie le rejet de la seconde et la version finale à 1. Il teste le mécanisme JPA sans ajouter de setter métier ni d'API d'édition aux archives.

Les deux routes métier restent des lectures. Elles ne déclenchent pas normalement de conflit optimiste ; le conseil REST et l'intercepteur Angular savent néanmoins traiter une exception Spring de ce type en 409. Une future écriture JPA devra conserver cette vérification. Les requêtes SQL natives de mise à jour ne bénéficient pas automatiquement de `@Version` : elles devraient contrôler et incrémenter explicitement la version. L'insertion actuelle reste protégée contre les doublons par la contrainte unique.

| Pattern | Usage et raison |
|---|---|
| Ports et adaptateurs | Les cas d'utilisation restent indépendants des protocoles et de PostgreSQL |
| Repository via un port | Le contrat de stockage est testable sans imposer un framework de persistance |
| Decorator | La transaction encadre l'ingestion sans annotation Spring dans le cœur |
| Mapper | Les réponses REST sont séparées des modèles et des octets stockés |
| Facade | Une façade Angular centralise recherche, pagination et sélection pour la page |
| Observer via RxJS | Les changements de recherche pilotent les requêtes annulables et leur état |

L'infrastructure de persistance comprend une entité JPA, un repository et l'adaptateur du port de stockage. Les lectures ne nécessitent plus de mapping manuel de `ResultSet` ni de concaténation SQL. Les quatre règles ArchUnit vérifient l'indépendance du cœur, l'absence d'accès direct des adaptateurs entrants au stockage concret et l'absence de cycles entre les couches.

## Exceptions REST

`InvalidMessageSearchException` décrit une recherche invalide et `MessageNotFoundException` une archive absente. Les cas d'utilisation ne dépendent pas des statuts HTTP. `ApiExceptionHandler`, annoté `@RestControllerAdvice` et dérivé de `ResponseEntityExceptionHandler`, traduit ces erreurs et celles de Spring MVC en réponses `application/problem+json` : 400 pour les paramètres, 404 pour une archive absente, 409 pour un conflit optimiste, 503 avec `Retry-After` pour une défaillance de persistance/transaction, et 500 pour une erreur inattendue. Le gestionnaire 409 est plus spécifique que celui des erreurs de stockage. Une erreur interne arbitraire n'est pas transformée en faute de saisie. Les détails SQL et les messages internes ne sont pas renvoyés au client.

Les responsabilités et les tests de chaque type sont détaillés dans [classes.md](classes.md), avec l'application concrète des principes SOLID.

## Organisation Angular et flux réactifs

`core` regroupe les services transversaux uniques (URL API, intercepteur d'erreur, en-tête). `shared` contient uniquement le loader réutilisable. La fonctionnalité `features/messages` regroupe sa route, sa page, ses composants de présentation, son client HTTP, ses modèles et son état. Les alias TypeScript `@core`, `@shared` et `@features` raccourcissent les imports transversaux ; les imports internes à la fonctionnalité restent relatifs.

La route `/messages` charge la page à la demande. Les composants standalone utilisent `OnPush`, les entrées/sorties `input` et `output`, et les templates `@if`/`@for` avec suivi par identifiant. Le bootstrap active la détection des changements zoneless. Les dates utilisent la locale française.

`MessagesStore` est fourni au niveau de la page. Ses flux RxJS reçoivent les recherches et sélections ; `switchMap` annule la requête précédente, notamment lorsqu'on ferme un détail en cours de chargement. Chaque requête émet un état de chargement puis un résultat ou une erreur. `catchError` reste dans le flux interne pour qu'une erreur n'empêche pas les recherches suivantes. `toSignal` expose cet état aux templates et gère le désabonnement avec le cycle de vie de la page. Les composants de présentation n'appellent pas `HttpClient` et ne font pas de `subscribe` manuel.

Le formulaire réactif typé valide la longueur des filtres et l'ordre des dates avant émission. L'identifiant MQ, la source et le statut restent visibles ; un élément HTML natif `details` regroupe corrélation et dates. Une période invalide ouvre cette section pour permettre la correction. Le bouton Rechercher déclenche explicitement la recherche ; il n'y a pas de requête à chaque frappe. Le retour à une page précédente conserve les filtres appliqués. L'intercepteur fonctionnel transforme les erreurs API en messages lisibles sans exposer les détails SQL. La fenêtre native `dialog` gère le focus modal et la fermeture par Échap ; Angular échappe le contenu affiché. Les formulaires réactifs typés suffisent ici, sans dépendance à une API de formulaire expérimentale.

Prettier utilise explicitement le parseur Angular pour les templates et le pipeline vérifie le formatage. Les tests couvrent l'annulation, la reprise après erreur, la pagination, le nettoyage à la destruction, le formulaire, les erreurs HTTP et l'affichage échappé.

## Déploiement bancaire

L’environnement Compose sert au développement et aux tests, avec une instance de chaque service. Il ne constitue pas une configuration haute disponibilité. Pour la production : utiliser le MQ existant avec TLS, fournir `MQ_SSL_CIPHER_SUITE` et les paramètres de truststore JVM, externaliser les secrets, déployer PostgreSQL avec sauvegardes/restauration vérifiées, superviser la profondeur des files et les taux de rollback, et configurer les ressources des instances applicatives. Le TLS n’est pas activé dans le Compose local.

L'interface et l'API n'intègrent aucune authentification, selon la demande. Les payloads sont affichés comme du texte échappé par Angular et ne sont pas interprétés comme du HTML. Ils ne sont pas écrits dans les journaux applicatifs. Seul l'endpoint Actuator de santé est exposé ; il sert aux sondes Docker et au test de reprise.

## Sources techniques

- [Spring : transactions JMS locales et détection des doublons](https://docs.spring.io/spring-framework/reference/integration/jms/receiving.html)
- [Spring Boot : prérequis](https://docs.spring.io/spring-boot/system-requirements.html)
- [Angular : compatibilité Node et TypeScript](https://angular.dev/reference/versions)
- [Angular : organisation par fonctionnalité](https://angular.dev/style-guide)
- [Angular : interopérabilité RxJS et signals](https://angular.dev/ecosystem/rxjs-interop)
- [Angular : formulaires typés](https://angular.dev/guide/forms/typed-forms)
- [IBM MQ : configuration de l’image de développement](https://github.com/ibm-messaging/mq-container/blob/master/docs/developer-config.md)

- [Spring Data JPA : specifications et projections](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html)
- [Spring MVC : ControllerAdvice et ProblemDetail](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [MapStruct : génération de mappers et contrôle des propriétés cibles](https://mapstruct.org/documentation/stable/reference/html/)
- [Jakarta Persistence : annotation Version](https://jakarta.ee/specifications/platform/11/apidocs/jakarta/persistence/version)
