# Rôle des classes et tests associés

Auteur : ELMANSOURI Mohamed.

Ce guide couvre les types du code applicatif : classes, records, interfaces et composants. Les types sans comportement propre sont vérifiés par les tests des fonctions qui les emploient. Les suites et leurs commandes sont détaillées dans [tests.md](tests.md).

## Backend : domaine

Les chemins ci-dessous sont relatifs à `backend/src/main/java/fr/bank/payments/messages/`. Le domaine ne dépend ni de Spring, ni de JPA, ni de JMS, ni de MapStruct.

| Type dans `domain/` | Rôle | Tests associés |
|---|---|---|
| `model/BodyType` | Énumère les contenus texte, binaires et non pris en charge. | `JmsMessageDecoderTest`, `MessageResponseMapperTest` |
| `model/MessageStatus` | Distingue un message stocké d'un message référencé en quarantaine. | `MessageMqAdapterTest`, `MessageApiIntegrationTest` |
| `model/ReceivedMessage` | Commande d'archivage à huit champs ; vérifie les invariants et copie défensivement les octets. | `MessageModelTest`, `JmsMessageDecoderTest` |
| `model/MessageSummary` | Projection de lecture à neuf champs ; aucune donnée de contenu n'est chargée pour une liste. | `MessageApiIntegrationTest`, `MessageResponseMapperTest` |
| `model/MessageDetail` | Associe les métadonnées et une copie défensive du contenu brut. | `MessageModelTest`, `MessageResponseMapperTest` |
| `model/MessagePage` | Contient une liste immuable, le curseur suivant et l'indication de suite. | `MessageModelTest`, `MessageApiIntegrationTest` |
| `model/MessageSearch` | Porte les filtres et impose une limite de 1 à 100, un curseur positif et une période valide. | `MessageModelTest`, `MessageApiIntegrationTest` |
| `exception/InvalidMessageSearchException` | Signale une recherche invalide sans connaître HTTP. | `MessageModelTest`, `ApiExceptionHandlerTest` |
| `exception/MessageNotFoundException` | Signale qu'un identifiant demandé ne correspond à aucune archive. | `MessageApiIntegrationTest`, `ApiExceptionHandlerTest` |

Le contenu reste nécessaire à l'archivage. La corrélation permet de retrouver un paiement ; la file d'origine fait partie de la clé de déduplication. Le type sert au décodage et au téléchargement. Le statut et le motif expliquent les rejets. La date d'émission, la taille persistée et le drapeau de relivraison ont été retirés. La version JPA reste interne et n'ajoute aucun champ aux DTO.

## Backend : application

| Type dans `application/` | Rôle et dépendances | Tests associés |
|---|---|---|
| `port/in/IngestMessageUseCase` | Port entrant d'archivage ; retourne si une nouvelle archive a été créée. | `MessageMqAdapterTest`, `MessageApiIntegrationTest` |
| `port/in/ConsultMessagesUseCase` | Port entrant de recherche et de consultation du détail. | `MessageApiIntegrationTest`, `ApiExceptionHandlerTest` |
| `port/out/MessageArchivePort` | Contrat de persistance : insertion idempotente, recherche bornée et détail optionnel. | `MessageApiIntegrationTest` vérifie le contrat sur PostgreSQL |
| `service/ArchiveMessageService` | Exécute l'archivage via le port sortant ; ne gère ni JMS ni transaction Spring. | Tests d'intégration de concurrence, rejeu, rollback et commit avant quarantaine |
| `service/ConsultMessagesService` | Délègue la recherche et transforme un détail absent en exception métier. | Tests HTTP de recherche, détail et 404 dans `MessageApiIntegrationTest` |

L'application dépend uniquement du domaine et de ses ports. Les tests Mockito isolent les frontières JMS et REST ; les contrats SQL et les transactions sont vérifiés avec PostgreSQL réel plutôt qu'avec un faux repository.

## Backend : infrastructure

| Type dans `infrastructure/` | Rôle et choix de conception | Tests associés |
|---|---|---|
| `adapter/in/mq/JmsMessageDecoder` | Traduit JMS en `ReceivedMessage`. Contrôle la taille, les lectures binaires, les métadonnées et les formats refusés. Le record privé `DecodedBody` regroupe uniquement le résultat intermédiaire de décodage. | 15 cas `JmsMessageDecoderTest` |
| `adapter/in/mq/MessageMqAdapter` | Appelle l'archivage puis réalise le PUT de quarantaine avec la session MQ reçue. Propage les échecs pour permettre le rollback et compte les résultats sans tag par message. | 9 cas `MessageMqAdapterTest`, test du commit SQL avant PUT et scripts MQ réels |
| `transaction/TransactionalMessageIngestion` | Décorateur `@Transactional` : le commit SQL se termine avant le retour au listener. | Tests d'intégration de rollback et de visibilité du commit avec une seconde connexion |
| `adapter/out/persistence/MessageEntity` | Mapping JPA de la table et version optimiste interne. L'accès par champs évite des accesseurs inutiles ; `toDetail()` restitue un modèle immuable. | Migrations, lecture binaire, SQL de projection et conflit optimiste dans `MessageApiIntegrationTest` |
| `adapter/out/persistence/MessageJpaRepository` | Étend `JpaRepository` et `JpaSpecificationExecutor`. Une seule insertion native `ON CONFLICT DO NOTHING` assure la déduplication concurrente sans invalider la transaction. | 24 insertions concurrentes, rejeu, même identifiant sur deux files et rollback |
| `adapter/out/persistence/JpaMessageArchiveAdapter` | Implémente le port sortant. Compose les filtres `Specification`, projette les métadonnées et applique le curseur avec une limite de page plus un. | Tests PostgreSQL/HTTP de filtres, limites, pagination et inspection du SQL sans payload/COUNT/OFFSET |
| `adapter/in/rest/MessageRestAdapter` | Expose uniquement les deux GET, valide les paramètres et appelle le port de consultation puis le mapper. | `MessageApiIntegrationTest`, `ApiExceptionHandlerTest` |
| `adapter/in/rest/dto/MessageResponses` | Conteneur sans état regroupant les trois DTO records HTTP ci-dessous. | Tests MapStruct et HTTP |
| `MessageResponses.Summary` | Réponse de métadonnées, avec identifiant SQL sous forme de chaîne pour préserver la précision JavaScript. | Test d'identifiant supérieur à `2^53` |
| `MessageResponses.Detail` | Métadonnées et contenu texte ou Base64 ; aucun contenu conservé pour un rejet. | Tests UTF-8, Base64 et quarantaine |
| `MessageResponses.Page` | Liste de DTO, curseur chaîne et indicateur de suite. | Tests de pagination, curseur 64 bits et page vide |
| `adapter/in/rest/mapper/MessageResponseMapper` | Interface MapStruct injectée dans le contrôleur. Déclare le mapping des records et deux conversions nommées pour les octets. Toute propriété cible oubliée provoque une erreur de compilation. | 5 cas `MessageResponseMapperTest` avec le mapper réellement généré |
| `MessageResponseMapperImpl` | Classe Spring générée par `mapstruct-processor` sous `backend/target/generated-sources/annotations/`. Construit les DTO et leurs listes sans réflexion à l'exécution. Elle est régénérée par Maven et n'est pas versionnée. | Même suite MapStruct, compilation Maven et tests HTTP |
| `config/MessageBeanConfiguration` | Assemble les services purs et expose le décorateur transactionnel comme unique implémentation du port d'ingestion. | Chargement Spring et test du commit avant PUT |
| `config/MqProperties` | Configuration MQ typée et validée : connexion, files distinctes, consommateurs et taille maximale. | Utilisée par les tests du décodeur, du listener et par le démarrage Docker |
| `config/MqConfiguration` | Construit la connexion IBM MQ, le conteneur JMS transactionnel et le `JmsTemplate` du producteur de démonstration. | Scripts `smoke-test.ps1` et `resilience-test.ps1` avec IBM MQ réel |

Autres classes du backend :

| Type relatif à `fr/bank/payments/` | Rôle | Tests associés |
|---|---|---|
| `PaymentHubApplication` | Point de démarrage Spring Boot et découverte de la configuration typée. | Démarrage des tests HTTP et des conteneurs |
| `shared/exception/ApiExceptionHandler` | `@RestControllerAdvice` qui produit des `ProblemDetail` : 400, 404, 409, 503 avec `Retry-After` et 500. Les messages techniques ne sont pas exposés. | 8 cas `ApiExceptionHandlerTest`, 400/404/503 sur HTTP réel |
| `tools/SamplePublisher` | Producteur activé explicitement en ligne de commande ; envoie du texte persistant ou un `MapMessage` destiné au test de quarantaine. | Scripts MQ réels ; pas activé dans le serveur normal |

Flyway conserve V1, adapte les anciennes colonnes dans V2 et ajoute `version` dans V3. `@Version` protège les écritures d'entités JPA. La clé unique protège séparément les nouvelles livraisons MQ. L'API reste en lecture seule ; le test de verrouillage utilise deux transactions JPA et `OPTIMISTIC_FORCE_INCREMENT`, sans introduire d'édition d'archive.

## Frontend : configuration et éléments communs

Les chemins sont relatifs à `frontend/src/`. Tous les composants sont standalone et utilisent `OnPush`.

| Type ou module | Rôle | Tests associés |
|---|---|---|
| `main.ts` | Enregistre la locale française et démarre Angular. | Build de production et vérification navigateur |
| `app/App` | Racine minimale : en-tête et emplacement du routeur. | Build et vérification navigateur |
| `app/app.config.ts` | Active le mode zoneless, le routeur, `HttpClient`, l'intercepteur fonctionnel et la locale. | Build et vérification navigateur ; configuration HTTP équivalente dans les tests de page |
| `app/app.routes.ts` | Redirige vers la fonctionnalité et charge ses routes à la demande. | Build et navigation navigateur |
| `environments/environment*.ts`, `core/config/api.config.ts` | Séparent l'URL de l'API de son usage ; `API_BASE_URL` est un token injectable. | `messages-api.spec.ts`, tests de l'intercepteur et build de production |
| `core/layout/header/Header` | Affiche le titre et le lien de navigation. | Vérification navigateur |
| `core/interceptors/ApiError` | Erreur applicative typée, avec statut et message lisible. | `error.interceptor.spec.ts`, tests de façade et de page |
| `core/interceptors/apiErrorInterceptor` | Fonction qui traduit les erreurs réseau/API, y compris 409, sans afficher les détails techniques du serveur. | 7 cas `error.interceptor.spec.ts` |
| `shared/components/loader/Loader` | Indicateur de chargement accessible réutilisé par la page et le détail. | Tests de page et vérification navigateur |

## Frontend : fonctionnalité messages

| Type ou module dans `app/features/messages/` | Rôle | Tests associés |
|---|---|---|
| `messages.routes.ts` | Charge `MessagesPage` à la demande. | Build et navigation navigateur |
| `data-access/MessagesApi` | Seul client HTTP de la fonctionnalité ; construit les paramètres sans convertir les identifiants 64 bits en nombres. | 2 cas `messages-api.spec.ts`, 3 tests de page |
| `state/MessagesStore` | Façade fournie par page. Combine RxJS (`switchMap`, `catchError`, `startWith`) et signals (`signal`, `computed`, `toSignal`) pour recherche, pagination et sélection. | 8 cas `messages.store.spec.ts` et tests de page |
| `pages/message-list/MessagesPage` | Orchestre le formulaire, le tableau, le détail et le loader ; ne connaît pas `HttpClient`. | 3 cas `messages-page.integration.spec.ts` |
| `components/message-filters/MessageFiltersComponent` | Formulaire réactif typé et non nullable ; valide longueurs et période puis émet les filtres en ISO 8601. | 5 cas `message-filters.spec.ts`, recherche depuis le DOM dans les tests de page |
| `dateRangeValidator` | Fonction pure de validation de groupe ; refuse les dates invalides ou une fin qui ne suit pas le début. | Tests du formulaire |
| `components/message-table/MessageTable` | Présente les métadonnées et émet la sélection ; entrées/sorties signals, suivi des lignes par identifiant. | Tests de page : affichage, sélection et pagination |
| `components/message-status/MessageStatusComponent` | Traduit le statut technique en libellé visuel français. | Tests du tableau/détail et vérification navigateur |
| `components/message-detail/MessageDetailComponent` | Affiche une fenêtre `dialog`, le contenu échappé, le chargement ou l'erreur ; émet la fermeture et déclenche le téléchargement. | `message-detail.spec.ts`, tests de page pour le détail 404, vérification navigateur |
| `utils/messageBlob` | Fonction pure qui restitue les octets Base64 ou le texte UTF-8 dans un `Blob`. | 2 cas `message-download.spec.ts` |
| `utils/downloadMessage` | Crée le lien de téléchargement et libère l'URL temporaire. | Vérification navigateur du téléchargement ; conversion des octets testée séparément |

`models/message.model.ts` contient les interfaces immuables du contrat : `MessageSummary`, `MessageDetail`, `MessagePage`, `MessageFilters` et `LoadState<T>`, ainsi que `BodyType`, `MessageStatus`, `EMPTY_FILTERS` et `EMPTY_PAGE`. Les tests du client, de la façade, du formulaire et de la page utilisent ces contrats. `Query`, privé à la façade, associe les filtres à leur curseur sans créer un service supplémentaire. `testing/message.fixture.ts` fournit uniquement les exemples partagés entre tests.

La façade possède les flux ; les composants de présentation n'ont aucun abonnement manuel. `catchError` est placé dans chaque requête interne pour permettre la reprise. `toSignal` assure le nettoyage au retrait de la page, et `switchMap` annule les recherches et détails devenus obsolètes. Les formulaires réactifs typés répondent au besoin sans ajouter une API expérimentale.

## Principes SOLID appliqués

| Principe | Application concrète |
|---|---|
| Responsabilité unique | Décodage JMS, cas d'utilisation, transaction, stockage, mapping HTTP et affichage sont séparés. |
| Ouvert à l'extension | Un nouvel adaptateur peut implémenter `MessageArchivePort` sans modifier les services applicatifs. |
| Substitution | Une implémentation du stockage doit préserver le contrat : insertion idempotente, première archive conservée et pagination bornée. Les tests PostgreSQL vérifient ces garanties. |
| Ségrégation des interfaces | Le listener ne connaît que l'ingestion ; le contrôleur ne connaît que la consultation. |
| Inversion des dépendances | Les services Java dépendent du port sortant. La configuration Spring leur fournit l'adaptateur JPA. |

Les quatre règles `ArchitectureTest` empêchent les dépendances du cœur vers les frameworks, les accès directs des adaptateurs entrants au stockage concret et les cycles entre couches. Les patterns employés répondent aux frontières existantes : ports et adaptateurs, repository, décorateur transactionnel, mapper et façade de page.
