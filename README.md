# Payment Message Hub

**Auteur : ELMANSOURI Mohamed**

Cette application lit les messages déposés dans une file IBM MQ, les enregistre dans PostgreSQL et permet de les consulter depuis une interface Web.

Elle reste centrée sur trois fonctions : rechercher les messages, consulter leur détail et télécharger leur contenu. Aucune authentification ou autorisation applicative n’est implémentée, conformément au besoin.

## Technologies

Java 21, Spring Boot 4.1.1, Angular 22.2.3, PostgreSQL 17, Maven et Docker Compose.

```text
Back Office → IBM MQ → Listener JMS → PostgreSQL
                                          ↑
                           Angular → API REST
```

## Lancer le projet

Prérequis : Docker Desktop démarré en mode conteneurs Linux, Docker Compose v2 et environ 6 Go de mémoire disponibles. Le premier lancement télécharge les images et les dépendances.

```sh
git clone https://github.com/elmansouriMed/testProjetELMANSOURI.git
cd testProjetELMANSOURI
```

Après lecture de la [licence IBM MQ Advanced for Developers](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=planning-mq-license-information), préparer la configuration locale. Le paramètre suivant indique votre acceptation de la licence pour l’image de développement.

Sous PowerShell 7 :

```powershell
./scripts/init-dev.ps1 -AcceptMqLicense
```

Sous Linux ou macOS avec OpenSSL :

```sh
sh scripts/init-dev.sh --accept-mq-license
```

Les scripts génèrent `.env` et `.secrets/mq-app-password.txt`, exclus de Git. Sans le paramètre d’acceptation, `MQ_LICENSE` reste vide ; le renseigner ensuite avec `accept`. Sous Linux, le répertoire des secrets est privé (`0700`) et le fichier monté dans MQ est lisible par le conteneur (`0444`).

```sh
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
```

| Service | Adresse |
|---|---|
| Interface | http://localhost:4200 |
| API | http://localhost:8080/api/messages |
| État de l’API et de la base | http://localhost:8080/actuator/health/readiness |
| Métriques | http://localhost:8080/actuator/prometheus |
| IBM MQ | localhost:1414 · QM1 · DEV.APP.SVRCONN |
| PostgreSQL | localhost:5432 · base et utilisateur `payments` |

Les ports sont limités au poste local. `docker compose down` arrête les services en conservant les données.

## Ajouter des messages de test

Cette commande publie 20 messages persistants dans MQ. Ils apparaissent ensuite dans l’interface après actualisation.

```sh
docker compose exec -T backend java -jar /app/app.jar --spring.main.web-application-type=none --app.mq.enabled=false --app.publisher.enabled=true --app.publisher.count=20
```

Pour tester la quarantaine, ajouter `--app.publisher.invalid=true` : le producteur envoie alors un `MapMessage`, format volontairement refusé.

## Tester

La suite comprend **56 tests Java**, dont 18 cas d'intégration avec PostgreSQL et HTTP réels, et **27 tests Angular**, dont 3 tests d'intégration de la page. Le [plan de tests](docs/tests.md) précise les scénarios et les frontières de simulation.

Le backend demande Java 21+ ; les tests démarrent leur propre PostgreSQL temporaire et ne modifient pas la base de développement. Sous Linux, les exécuter avec un utilisateur non root.

```sh
./mvnw verify
```

Sous Windows : `./mvnw.cmd verify`.

Pour Angular, utiliser Node 24.15+ dans la branche 24, ou une autre version compatible Angular 22 (`^22.22.3` ou `>=26`).

```sh
cd frontend
npm ci
npm run format:check
npm test
npm run build
```

Avec la pile Docker démarrée, lancer depuis la racine sous PowerShell 7 :

```powershell
./scripts/smoke-test.ps1
./scripts/resilience-test.ps1
```

Le premier script vérifie MQ → PostgreSQL → REST, la validation HTTP, la quarantaine et la disponibilité de l’interface. Le second arrête PostgreSQL, publie 12 messages dans MQ, vérifie une réponse 503 puis relance la base et attend l’archivage. Ces scripts ajoutent des données de test et conservent les volumes. GitHub Actions exécute également ces vérifications.

## API REST

| Route | Résultat |
|---|---|
| `GET /api/messages` | Liste paginée de métadonnées, sans le contenu |
| `GET /api/messages/{id}` | Métadonnées et contenu texte UTF-8 ou binaire Base64 |

Filtres facultatifs : `source`, `status=STORED|QUARANTINED`, `mqMessageId`, `correlationId`, `from`, `to`. Les recherches textuelles sont exactes et sensibles à la casse. Les dates API utilisent ISO 8601 avec fuseau ; `from` est inclus et `to` est exclu.

```sh
curl "http://localhost:8080/api/messages?source=BO-SEPA&limit=10"
curl "http://localhost:8080/api/messages?status=QUARANTINED"
curl "http://localhost:8080/api/messages/1"
```

`limit` est compris entre 1 et 100, avec 25 par défaut. Utiliser `nextCursor` comme `beforeId` pour lire la page suivante. Les identifiants sont transmis en chaînes pour préserver les entiers 64 bits dans JavaScript. Le [contrat OpenAPI](docs/openapi.yaml) décrit les paramètres et les réponses.

## Fiabilité et performance

- Le commit SQL précède l’acquittement MQ. Une panne SQL entraîne une nouvelle livraison ; la contrainte unique `(source_queue, mq_message_id)` évite les doublons.
- Les transactions sont locales : livraison au moins une fois, sans transaction XA. Le producteur doit envoyer des messages persistants et prévoir une durée de vie compatible avec la reprise.
- Les messages texte et binaires sont acceptés jusqu’à 1 Mio par défaut. Les formats refusés sont référencés en base et envoyés en quarantaine avec la même session MQ. Si ce transfert échoue, la réception est annulée.
- Quatre consommateurs et un pool SQL de 16 connexions par défaut, réglables par `MQ_CONSUMERS` et `DB_POOL_SIZE`. La pagination utilise un curseur, sans `OFFSET` ni comptage global.
- Les files locales utilisent `BOTHRESH(0)` pour empêcher un déplacement automatique hors de la file d’entrée pendant une panne SQL.

Le débit doit être mesuré sur l’infrastructure cible. Un scénario HTTP k6 est fourni dans `scripts/load.js`. Le Compose local ne constitue pas une configuration haute disponibilité.

## Organisation

```text
backend/src/main/java/fr/bank/payments/
  messages/
    domain/model/              Records, invariants et types métier
    application/port/in/       Cas d'utilisation ingestion et consultation
    application/port/out/      Contrat de stockage
    application/service/       Services Java indépendants des frameworks
    infrastructure/
      adapter/in/mq/           Réception et décodage JMS
      adapter/in/rest/         Contrôleur, DTO et mapping HTTP
      adapter/out/persistence/ Stockage JDBC PostgreSQL
      transaction/            Décorateur transactionnel Spring
      config/                 Assemblage des dépendances et configuration MQ
  shared/exception/            Gestion des erreurs HTTP
frontend/src/app/
  core/                       Configuration, intercepteur HTTP et en-tête
  shared/                     Loader et pipe de taille réutilisables
  features/messages/
    pages/                    Page de consultation
    components/               Filtres, tableau, statut et détail
    data-access/              Client REST typé
    models/                   Modèles et états de chargement
    state/                    Façade de page : RxJS et signals
    utils/                    Téléchargement du contenu
docker/     Définition des files MQ
scripts/    Initialisation et tests de bout en bout
docs/       Architecture, contrat API et correspondance avec les exigences
```

Le backend suit une architecture hexagonale : les cas d'utilisation dépendent de ports, les adaptateurs utilisent Spring, JDBC ou JMS. Des tests ArchUnit protègent cette séparation. Les API gardent le même contrat.

Angular utilise des composants standalone `OnPush`, le mode zoneless, des routes chargées à la demande, des formulaires réactifs typés et les API `input`/`output`. La façade locale combine `switchMap` pour annuler les requêtes obsolètes et `toSignal` pour exposer leur état aux templates avec nettoyage automatique à la destruction de la page. Aucun gestionnaire d'état externe n'est nécessaire.

Pour travailler hors des conteneurs applicatifs : démarrer `docker compose up -d postgres mq`, charger les variables de `.env` dans le terminal puis lancer `./mvnw -pl backend spring-boot:run`. Dans un second terminal, exécuter `npm start` depuis `frontend/`. Le serveur Angular relaie `/api` vers le backend local.

Voir [l’architecture](docs/architecture.md), [les exigences client](docs/exigences.md) et [les vérifications](docs/verification.md) pour les détails.
