# Tests unitaires et d'intégration

Auteur : ELMANSOURI Mohamed.

La suite comprend 100 cas : 71 côté Java et 29 côté Angular. Les tests paramétrés comptent chaque jeu de données. Les vérifications visent les risques fonctionnels ; aucun pourcentage de couverture ou objectif de débit n'est déduit du nombre de tests.

## Backend : 71 cas

| Classe | Cas | Responsabilités vérifiées |
|---|---:|---|
| `MessageModelTest` | 10 | Copie défensive des contenus et des pages, motif de quarantaine obligatoire, bornes de pagination et période valide |
| `JmsMessageDecoderTest` | 15 | Texte vide, taille en octets UTF-8, limite exacte, source absente ou trop longue, métadonnées et corrélation binaire, identifiant obligatoire, lectures binaires fragmentées ou incomplètes, erreur JMS |
| `MessageMqAdapterTest` | 9 | Archivage avant retour, absence d'acquittement manuel, doublons, erreur SQL, quarantaine sur la session reçue, échec du PUT et fermeture du producteur |
| `ArchitectureTest` | 4 | Indépendance du cœur, sens des dépendances, isolation des adaptateurs et absence de cycles |
| `MessageApiIntegrationTest` | 20 | Spring Boot, migrations Flyway, JPA, transaction SQL et HTTP réels avec PostgreSQL temporaire ; projection SQL et conflit optimiste entre deux transactions |
| `ApiExceptionHandlerTest` | 8 | Conseil REST, validation, conversion, message absent, conflit 409 distinct d'une indisponibilité 503 avec Retry-After, et erreur interne 500 sans fuite technique |
| `MessageResponseMapperTest` | 5 | Implémentation MapStruct générée : identifiants et curseurs 64 bits, UTF-8, Base64, quarantaine sans contenu et page vide |

Les 20 cas d'intégration couvrent notamment :

- 24 insertions concurrentes d'un même message : une seule ligne créée ;
- même contenu avec deux identifiants MQ, et même identifiant sur deux files : messages distincts ;
- rejeu : contenu et métadonnées du premier archivage conservés ;
- rollback SQL puis reprise sans doublon ;
- pagination pendant de nouvelles insertions, dernière page complète et page vide ;
- projection JPA sans sélection du contenu binaire, sans comptage global et sans OFFSET ;
- deux transactions JPA sur une même version : premier commit accepté, second rejeté par `OptimisticLockException`, version inchangée lors d'un rejeu MQ ;
- filtres combinés, caractères spéciaux, requêtes paramétrées et limites de longueur à un caractère au-delà du maximum ;
- période de réception avec début inclus et fin exclue ;
- réponses 400/404, contenu UTF-8, binaire Base64 et identifiants supérieurs à la précision entière JavaScript ;
- erreur SQL réelle, réponse 503 normalisée avec `Retry-After`, absence de détails SQL et retour à un fonctionnement normal ;
- appel du véritable adaptateur d'ingestion, puis vérification par une **seconde connexion SQL** que la référence de quarantaine est déjà validée au moment du PUT.

PostgreSQL est une instance éphémère lancée par la suite ; la base de développement n'est pas touchée. Le test d'erreur SQL renomme temporairement la table de cette instance et la restaure dans un bloc `finally`. Les tests de cette classe s'exécutent séquentiellement et réinitialisent les données avant chaque cas.

Le dernier scénario utilise le véritable décodeur JMS, le port d'ingestion, son décorateur Spring et PostgreSQL. La session et le producteur JMS sont simulés pour observer précisément l'instant du PUT. Il ne remplace pas les essais avec un queue manager IBM MQ réel.

```sh
./mvnw verify
```

Sous Windows : `./mvnw.cmd verify`. Java 21+ est nécessaire ; sous Linux, PostgreSQL demande un utilisateur non root. Les rapports se trouvent dans `backend/target/surefire-reports/`.

## Frontend : 29 cas

| Suite | Cas | Responsabilités vérifiées |
|---|---:|---|
| Intercepteur HTTP | 7 | Erreurs réseau, 400, 404, 409, 500 et 503 ; détails techniques masqués ; ressources hors API préservées |
| Client REST | 2 | Filtres et caractères spéciaux, curseur 64 bits, propagation des erreurs |
| Façade `MessagesStore` | 8 | Annulation des recherches/détails obsolètes, destruction, reprise après erreur, conservation des filtres, remise à zéro des curseurs et doubles clics de pagination |
| Formulaire | 5 | Ordre des dates, dates égales, conversion ISO, longueurs et réinitialisation après erreur |
| Détail | 3 | Contenu HTML affiché comme texte, binaire consultable en Base64 et quarantaine avec contenu absent |
| Intégration de la page | 4 | Formulaire → HTTP → tableau/pagination, filtres avancés et réinitialisation, détail indisponible et reprise après 503 |

Les quatre tests de page assemblent les véritables composants, le formulaire, la façade, le client REST et l'intercepteur. Ils déclenchent des événements DOM puis contrôlent le rendu. `HttpTestingController` remplace uniquement le transport HTTP ; la méthode native `dialog.showModal` est adaptée à l'environnement DOM de test. Ces tests ne démarrent pas de serveur backend et ne remplacent pas la vérification navigateur.

```sh
cd frontend
npm ci
npm run format:check
npm test
npm run build
```

Utiliser la version Node indiquée dans le README. Les erreurs sont injectées de façon déterministe ; les tests ne reposent pas sur des délais arbitraires.

## Intégration avec IBM MQ réel

Après démarrage de Docker Compose, exécuter sous PowerShell 7, depuis la racine :

```powershell
./scripts/smoke-test.ps1
./scripts/resilience-test.ps1
```

Le premier script publie des messages persistants dans IBM MQ et vérifie l'archivage, l'API et la quarantaine. Le second interrompt réellement PostgreSQL, vérifie la réponse 503 et la présence des messages dans MQ, puis attend les 12 archivages après redémarrage. Les deux scripts ajoutent des messages de test ; les exécuter séquentiellement sur la pile de développement.

GitHub Actions exécute les suites Java et Angular, puis reconstruit les images et lance les deux scripts. Les résultats et les limites de validation sont précisés dans [verification.md](verification.md).
