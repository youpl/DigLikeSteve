# Dig Like Steve — Document d'Architecture Technique

**Statut :** proposition d'architecture avant implémentation  
**Version du document :** 1.0  
**Cible :** Paper `26.1.2.build.74-stable` et Folia  
**Runtime :** Java 25  
**Dépôt cible :** `youpl/DigLikeSteve`

## 1. Objet et périmètre

Dig Like Steve (DLS) est un mini-jeu à partie unique. Un administrateur définit un cuboïde constituant la mine. Avant chaque partie, le plugin remplit cette zone avec une distribution pondérée de blocs. Après 60 secondes d'inscription par défaut, les participants disposent de 10 minutes pour détruire les blocs et obtenir le meilleur score.

Le système doit assurer en priorité :

- une compatibilité réelle Paper/Folia, et pas seulement la présence de `folia-supported: true` ;
- l'absence de duplication ou de perte d'inventaire, y compris lors des déconnexions et arrêts anormaux ;
- un calcul de score exact et non exploitable ;
- une génération de mine progressive afin de préserver les TPS ;
- des intégrations facultatives et isolées de la logique métier ;
- aucune dépendance NMS ou protocolaire pour le périmètre initial.

Ce DAT décrit l'architecture cible. Il ne constitue pas encore une implémentation.

## 2. Hypothèses fonctionnelles retenues

Les décisions suivantes complètent le besoin lorsque le comportement n'était pas totalement spécifié :

- une seule session et une seule arène peuvent exister à la fois par serveur ;
- la zone est un cuboïde dans un seul monde et sera entièrement écrasée lors de sa préparation ;
- la sélection peut être importée depuis WorldEdit ou FAWE sur une plateforme compatible ; à défaut, les deux coins sont définis en configuration ou avec les commandes DLS ;
- WorldEdit et FAWE restent des `softdepend` : le plugin doit démarrer et être utilisable sans eux ;
- le message cliquable exécute la commande canonique `/dls join` ;
- `/warp DLS` peut être relié à l'inscription par un pont configurable, sans rendre EssentialsX obligatoire ;
- un joueur déconnecté est `SUSPENDED` pendant 30 secondes ; s'il revient à temps, il reprend sa partie et son score ; sinon il est disqualifié et son inventaire est restauré à sa prochaine connexion ;
- un redémarrage complet du serveur annule la session active et place tous les inventaires non restaurés en récupération. Une reprise de session après redémarrage n'est pas retenue en V1, car elle exigerait de restaurer atomiquement l'état temporel, la mine et toutes les TNT actives ;
- aucun bloc ne produit de drop ni d'expérience pendant la partie ;
- un bloc posé par un joueur ne peut jamais rapporter de points ;
- la TNT elle-même vaut 0 point. Elle crédite uniquement la valeur des blocs scorables qu'elle détruit ;
- la pénalité TNT concerne le propriétaire touché par sa propre TNT. Cette règle évite qu'un adversaire puisse retirer des points à une victime ;
- les ex æquo sont récompensés ensemble par défaut. Une stratégie `shared`, `first-reached` ou `random` sera configurable ;
- le classement de session est éphémère. Un compteur permanent de victoires est une fonctionnalité facultative activable.

Les règles de dégâts non-TNT, de mort et de PvP devront être configurables. La recommandation V1 est : PvP désactivé, dégâts environnementaux annulés, dégâts TNT conservés mais non létaux afin que la pénalité de score reste la sanction principale.

## 3. Contraintes de plateforme

Paper 26.1+ requiert Java 25. Le projet utilisera donc un toolchain Gradle Java 25 et compilera précisément contre `io.papermc.paper:paper-api:26.1.2.build.74-stable`.

Folia ne possède pas un « main thread » unique. Les accès au monde et aux entités appartiennent à des régions différentes :

- `GlobalRegionScheduler` pour l'état global du mini-jeu, les commandes console et les annonces globales ;
- `RegionScheduler` pour lire ou modifier des blocs à une position donnée ;
- `EntityScheduler` pour l'inventaire, l'état et la téléportation d'un joueur ;
- `AsyncScheduler` ou des exécuteurs privés bornés uniquement pour les calculs purs et les entrées/sorties.

Mettre `folia-supported: true` dans `plugin.yml` est nécessaire mais insuffisant. Toute l'implémentation doit respecter l'affinité de région décrite ci-dessus.

### Limite WorldEdit/FAWE sur Folia

FAWE maintient la compatibilité de l'API WorldEdit, ce qui permet un seul adaptateur de sélection sur Paper. Toutefois, la déclaration Bukkit publique actuelle de FAWE ne contient pas `folia-supported: true`. DLS ne doit donc jamais rendre WorldEdit/FAWE nécessaire sur Folia. Le mode natif par coordonnées constitue le chemin garanti. L'adaptateur ne sera activé que si le plugin détecté est effectivement chargé par la plateforme.

WorldEdit/FAWE sert initialement à **importer une sélection**, pas à piloter le moteur de remplissage Folia. Cette séparation évite de faire dépendre la sûreté du jeu d'un moteur tiers.

## 4. Style architectural global

### 4.1 Architecture hexagonale

La logique métier ne doit importer aucune classe Bukkit, Paper, Adventure, WorldEdit ou plugin tiers. Elle manipule des types internes immuables tels que `PlayerId`, `BlockPos`, `ArenaBounds`, `Score`, `SessionId` et `Deadline`.

Le sens des dépendances est le suivant :

```text
Paper/Folia events, commandes, GUI
                |
                v
       adaptateurs entrants
                |
                v
      application / cas d'usage
                |
                v
         domaine DLS pur
                ^
                |
  ports sortants (stockage, monde,
  inventaire, téléportation, récompense)
                ^
                |
    adaptateurs Paper et intégrations
```

Cette architecture permet de tester le score, les transitions et les règles de reconnexion sans lancer un serveur Minecraft.

### 4.2 Modules Gradle recommandés

```text
DigLikeSteve/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── dls-core/
│   └── domaine, règles, ports et cas d'usage Java pur
├── dls-platform-paper/
│   └── événements Bukkit/Paper, schedulers Paper/Folia, monde et inventaires
├── dls-integrations/
│   └── WorldEdit/FAWE, placeholders, warps, spawn et récompenses
├── dls-plugin/
│   └── bootstrap JavaPlugin, assemblage et ressources finales
└── dls-test-support/
    └── fixtures et bancs d'intégration
```

`dls-plugin` produit un seul JAR distribuable. Les API facultatives restent en `compileOnly`; les adaptateurs ne sont instanciés qu'après détection de leur plugin.

### 4.3 Patterns retenus

- **State pattern** : cycle de vie strict de la session et transitions contrôlées.
- **Ports/Adapters** : séparation entre Core et Minecraft.
- **Observer** : listeners Paper minces, convertissant un événement Minecraft en commande métier.
- **Strategy** : distribution des blocs, égalités, téléportation de sortie, récompenses et backend de placement.
- **Factory** : création d'une session, du kit, des spawn slots et des adaptateurs disponibles.
- **Repository** : inventaires de secours, métadonnées de session, statistiques et outbox de récompense.
- **Unit of Work / transaction** : sauvegarde d'inventaire et changement de phase atomiques.
- **Outbox** : remise des récompenses avec reprise après incident.
- **Circuit breaker simple** : désactivation locale d'une intégration facultative qui échoue, sans casser la partie.

Le suffixe `Manager` doit être réservé aux objets d'orchestration très courts. Les règles métier appartiennent à des services nommés selon leur responsabilité (`ScoreService`, `InventoryCustodyService`, `MinePreparationService`), afin d'éviter un `GameManager` monolithique.

## 5. Modèle de domaine

### 5.1 Agrégats principaux

`GameSession` est la racine d'agrégat :

- identifiant de session ;
- phase courante ;
- bornes et monde de l'arène ;
- seed de génération ;
- début et échéance d'inscription ;
- début et échéance de jeu ;
- participants ;
- politique de score et de récompense.

`Participant` contient uniquement des données métier :

- UUID ;
- statut `JOINING`, `ACTIVE`, `SUSPENDED`, `DISQUALIFIED`, `FINISHED`, `RESTORED` ;
- score atomique ;
- instant de déconnexion et échéance de grâce ;
- identifiant du snapshot d'inventaire ;
- instant auquel son meilleur score a été atteint, utile à la stratégie d'égalité.

`Arena` contient le cuboïde normalisé, le volume, le mapping position/index, les spawn slots et l'index compact des blocs.

`ExplosionAccount` contient l'UUID de la TNT, son propriétaire, les points bruts gagnés, les dégâts finaux reçus par le propriétaire et un drapeau de règlement idempotent.

### 5.2 Machine à états de session

```text
IDLE
  -> PREPARING
  -> REGISTRATION
  -> STARTING
  -> RUNNING
  -> FINISHING
  -> RESTORING
  -> CLEANUP
  -> IDLE

Toute erreur critique -> RECOVERY -> CLEANUP ou IDLE
```

- `PREPARING` valide la configuration, calcule le plan, charge les chunks nécessaires et remplit progressivement la mine.
- `REGISTRATION` dure 60 secondes par défaut. Les inscrits sont sauvegardés puis isolés dans leur spawn slot, sans pouvoir miner.
- `STARTING` ferme les inscriptions et vérifie que tous les participants sont prêts.
- `RUNNING` dure 600 secondes par défaut.
- `FINISHING` bloque immédiatement tout nouveau score, fige le classement et crée la récompense.
- `RESTORING` rend les inventaires et téléporte les joueurs.
- `CLEANUP` retire les tâches, TNT, boss bars, tickets de chunks et caches de session.

Les transitions sont validées par comparaison atomique. Une commande `/dls stop` est idempotente : deux appels ne peuvent ni restaurer deux fois ni distribuer deux récompenses.

### 5.3 Temps

Les délais ne doivent pas dépendre d'un compteur de ticks. Une baisse de TPS ne doit pas transformer dix minutes en quinze minutes.

- en mémoire : échéance calculée avec une horloge monotone (`System.nanoTime`) ;
- en persistance : instant UTC (`Instant` / epoch millis) pour la récupération ;
- les tâches périodiques ne font que rafraîchir l'affichage et vérifier `now >= deadline`.

L'horloge est injectée dans le Core pour rendre les tests déterministes.

## 6. Mine, distribution et protection du score

### 6.1 Définition de la zone

Trois modes d'administration sont prévus :

1. `/dls admin pos1` et `/dls admin pos2` ;
2. `/dls admin selection import`, via l'adaptateur WorldEdit compatible FAWE ;
3. `arena.corner-1` et `arena.corner-2` dans `config.yml`.

La sauvegarde stocke le nom du monde, éventuellement son UUID, et les coordonnées entières normalisées. Une sélection multi-monde, vide, dépassant le volume maximal ou touchant une zone interdite est rejetée.

L'interface d'administration en jeu modifie un brouillon de configuration. La validation et l'écriture sont atomiques. Les changements d'arène, de blocs ou de durée ne prennent effet qu'en phase `IDLE`.

### 6.2 Distribution exacte

Chaque type de bloc est décrit par :

- une clé de matériau Minecraft ;
- un poids ou pourcentage ;
- une valeur en points ;
- un outil conseillé ;
- des propriétés facultatives de bloc.

Les valeurs initiales sont :

| Bloc | Points |
|---|---:|
| Cobblestone | 5 |
| Ice | 1 |
| Melon | 5 |
| Stone | 3 |
| Wood Log | 4 |
| TNT | 0 directement |
| Iron Block | 10 |

Les pourcentages seront configurables et devront totaliser 100 %. L'algorithme recommandé calcule d'abord le nombre exact de blocs de chaque type avec la méthode des plus forts restes, construit un tableau compact d'identifiants, puis le mélange avec Fisher-Yates à partir de la seed de session. Cette méthode évite les écarts importants d'une série de tirages indépendants et permet de reproduire une mine à partir de sa seed.

Le calcul du tableau est pur et asynchrone. L'application aux chunks ne l'est pas : chaque lot est envoyé au `RegionScheduler` propriétaire des positions concernées.

### 6.3 Écriture progressive

La mine est découpée par chunks, puis en petits lots avec un budget configurable :

- nombre maximal de blocs par lot ;
- durée maximale de travail par région et par tick ;
- nombre maximal de chunks simultanément chargés ;
- seuil d'arrêt automatique si le temps de tick dépasse le budget.

Sur Paper, un backend FAWE pourra être proposé ultérieurement comme accélérateur explicite. Le backend natif reste la référence fonctionnelle et le seul chemin garanti sur Folia.

L'inscription n'ouvre qu'après préparation complète. En cas d'échec partiel, la partie ne démarre pas et l'administrateur reçoit un diagnostic avec les chunks concernés.

### 6.4 Spawn aléatoire sûr

Téléporter un joueur dans un cuboïde entièrement solide provoquerait une suffocation. Le générateur réserve donc des spawn slots aléatoires reproductibles :

- poche d'air d'au moins 1 × 2 blocs, de préférence 3 × 3 × 2 ;
- sol stable non-TNT ;
- distance minimale entre deux joueurs ;
- positions exclues du score ;
- absence de liquide ou TNT dans un rayon configurable.

Un slot est réservé atomiquement pour un participant. Si tous les slots sont utilisés, l'inscription est refusée proprement plutôt que de superposer des joueurs.

### 6.5 Index compact anti-double-score

Le plugin ne doit pas faire confiance au matériau actuellement visible comme seule preuve. Sinon un joueur ou un autre plugin pourrait replacer un bloc et le scorer à nouveau.

Pour chaque position du cuboïde :

- un octet décrit le type généré initialement ;
- un bit atomique indique si la position a déjà été consommée.

Un million de blocs représente environ 1 Mo pour les types et 125 Ko pour le bitset consommé. Une opération de casse ou une explosion doit réussir un `compare-and-set` sur ce bit avant d'accorder les points. Cette structure protège aussi contre une collision entre casse manuelle et explosion dans le même tick.

Les blocs en dehors du cuboïde, les poches de spawn, l'air, les blocs ajoutés après génération et les positions déjà consommées valent toujours zéro.

## 7. Calcul des scores et TNT

### 7.1 Casse normale

Les listeners Paper filtrent : phase `RUNNING`, participant actif, position dans l'arène, événement non annulé et bloc consommable. Ils désactivent drops et expérience. Le Core reçoit ensuite `ConsumeBlock(playerId, arenaIndex, generatedType)`.

La valeur du bloc provient du plan de génération, pas d'un texte, d'un nom d'item ou d'une requête de configuration répétée.

### 7.2 Attribution d'une TNT

La solution repose sur les API Paper/Bukkit, sans NMS :

1. `TNTPrimeEvent` valide une cause joueur et le briquet DLS ;
2. le propriétaire est inscrit sur la TNT amorcée via un PDC `dls:owner` et dans un registre court terme par UUID ;
3. lors d'une réaction en chaîne, l'identité est propagée depuis la TNT source ;
4. `EntityExplodeEvent` fournit la liste des blocs qui seront détruits ;
5. seules les positions dans l'arène gagnant leur bit de consommation sont additionnées ;
6. les drops d'explosion sont supprimés ; le feu et les dommages aux blocs hors arène sont annulés.

`TNTPrimed#getSource()` est un secours utile, mais ne suffit pas seul : sa documentation précise que la source peut devenir nulle après déchargement/rechargement du chunk. Le PDC et le registre TTL donnent une attribution stable.

Une TNT amorcée par redstone, feu, projectile, commande ou plugin tiers ne rapporte rien par défaut. Les causes autorisées seront configurables.

### 7.3 Pénalité de dégâts

L'explosion ouvre un compte temporaire :

```text
grossPoints = somme des blocs scorables consommés par cette TNT
halfHearts  = ceil(finalDamage - epsilon)
rate        = min(1.00, halfHearts * 0.05)
penalty     = arrondi(grossPoints * rate)
netPoints   = grossPoints - penalty
```

Dans l'API Minecraft, 1 point de vie correspond à un demi-cœur. La pénalité utilise `EntityDamageEvent#getFinalDamage()` au niveau `MONITOR`, seulement si l'événement n'est pas annulé et si la source directe est la TNT suivie.

Le règlement est différé d'un très court délai afin de réunir l'événement de blocs et l'événement de dégâts, dont l'ordre interne ne doit pas être supposé. Il est idempotent : un `ExplosionAccount` ne peut être crédité qu'une fois.

Exemple : une TNT détruit 100 points de blocs et inflige 3 demi-cœurs de dégâts finaux à son propriétaire. La pénalité est 15 points ; le gain net est 85.

La pénalité est plafonnée au gain de la TNT concernée. Elle ne peut donc pas retirer les points acquis auparavant. Ce comportement pourra être rendu configurable si une règle plus punitive est souhaitée.

## 8. Garde d'inventaire sans duplication

### 8.1 Invariant principal

**Le plugin ne vide jamais un inventaire avant d'avoir persisté avec succès un snapshot complet et récupérable.**

Le snapshot comprend au minimum :

- stockage principal, hotbar, armure, off-hand et slots supplémentaires ;
- slot tenu ;
- niveau et expérience ;
- mode de jeu ;
- vie, nourriture et saturation ;
- effets de potion ;
- vol autorisé/actif ;
- emplacement d'origine et orientation.

L'Ender Chest n'est ni vidé ni sauvegardé, puisqu'il n'est pas modifié par DLS.

Les `ItemStack` sont sérialisés avec le format binaire Paper intégrant la DataVersion, plus sûr pour les migrations que la sérialisation YAML Bukkit. Un checksum détecte un snapshot tronqué.

### 8.2 Protocole d'entrée

Le protocole est une saga persistée :

1. sur l'`EntityScheduler` du joueur, fermer l'inventaire ouvert, cloner et sérialiser son état ;
2. sur l'exécuteur base de données, insérer le snapshot en phase `CAPTURED` dans une transaction durable ;
3. après confirmation, revenir sur l'`EntityScheduler` du même joueur ;
4. vérifier qu'il est toujours connecté, éligible et que la session accepte les inscriptions ;
5. vider l'inventaire une seule fois, appliquer le kit marqué PDC, puis passer à `KIT_APPLIED` ;
6. téléporter le joueur avec l'API asynchrone appropriée et passer à `ACTIVE`.

Si le joueur se déconnecte entre les étapes 2 et 5, son inventaire n'a pas été touché. Le snapshot `CAPTURED` est invalidé ou comparé à l'état courant à sa reconnexion. S'il se déconnecte après l'étape 5, le snapshot devient la source de restauration.

Un verrou logique par UUID et une contrainte unique en base interdisent deux gardes actives pour le même joueur.

### 8.3 Protocole de sortie

1. transition atomique vers `RESTORE_PENDING` ;
2. sur l'`EntityScheduler`, supprimer entièrement le kit DLS et tout contenu acquis pendant l'événement ;
3. restaurer exactement tous les champs du snapshot ;
4. téléporter vers la destination de sortie ;
5. persister `RESTORED` avec l'instant et le checksum ;
6. conserver le snapshot pendant une rétention configurable avant purge.

Un deuxième appel de restauration voit `RESTORED` et ne fait rien. La restauration utilise `setContents`/slots exacts et jamais `addItem`, ce qui empêche les doublons et les débordements au sol.

### 8.4 Déconnexion et délai de 30 secondes

- À la déconnexion : statut `SUSPENDED`, échéance persistée, aucune référence `Player` conservée.
- Reconnexion avant l'échéance : validation du snapshot et du kit, retour au spawn slot ou à une position sûre, statut `ACTIVE` avec score conservé.
- Reconnexion après l'échéance : statut `DISQUALIFIED`, exclusion du classement, restauration avant que le joueur puisse effectuer une autre action.
- Expiration pendant que le joueur reste hors ligne : phase `RESTORE_PENDING`; la restauration réelle s'exécute au prochain `PlayerJoinEvent`.

La reconnexion traite toujours une restauration en attente **avant** une nouvelle demande d'inscription.

### 8.5 Arrêt anormal

Au démarrage, un `RecoveryService` cherche tous les snapshots `KIT_APPLIED`, `ACTIVE`, `SUSPENDED` ou `RESTORE_PENDING`. Ils passent en restauration obligatoire. Tant que celle-ci n'est pas terminée, le joueur ne peut pas rejoindre une nouvelle session.

En `onDisable`, le plugin :

- ferme les inscriptions et le score ;
- marque les gardes restantes `RESTORE_PENDING` dans une transaction ;
- restaure immédiatement les joueurs encore en ligne via leurs schedulers si la plateforme le permet ;
- ne supprime jamais les snapshots non confirmés comme restaurés.

## 9. Kit et restrictions

Le kit contient : pioche, hache et pelle en diamant, cisaille et briquet. Chaque outil :

- possède le composant/incassabilité officiel ;
- est marqué par PDC avec la session et le type d'outil ;
- ne peut pas être jeté, stocké, échangé, posé dans un conteneur ou déplacé hors des slots autorisés ;
- est supprimé lors de toute restauration, même s'il a changé de slot.

Pendant la partie, le plugin bloque au minimum : placement de blocs, ramassage et drop d'items, accès aux conteneurs, craft, commandes de téléportation non autorisées, portails, changements de monde, fluides/pistons affectant la frontière et dégâts de blocs en dehors de l'arène.

Les restrictions sont appliquées par politiques courtes et testables, pas dans un listener universel.

## 10. Ordonnancement, concurrence et performances

### 10.1 Règle d'or

`ConcurrentHashMap` rend une collection concurrente ; il ne rend pas l'API Bukkit utilisable hors de son thread propriétaire. Aucune tâche asynchrone ne doit conserver ou appeler `Player`, `Entity`, `Block`, `World`, `Inventory` ou un objet WorldEdit mutable.

Les frontières de thread échangent uniquement des DTO immuables, UUID, primitives, noms de monde et coordonnées.

### 10.2 Données concurrentes

- `AtomicReference<SessionPhase>` pour la phase globale ;
- `ConcurrentHashMap<UUID, ParticipantRuntime>` pour les participants ;
- `AtomicLong` pour chaque score, afin de produire un classement ponctuel cohérent ;
- `AtomicLongArray` pour le bitset des blocs consommés ;
- `ConcurrentHashMap<UUID, ExplosionAccount>` avec TTL pour les TNT ;
- objets de configuration immuables remplacés atomiquement après validation.

Les opérations composées passent par des méthodes atomiques du domaine ou un verrou fin par joueur. Aucun verrou JVM n'est tenu pendant une opération Bukkit, une téléportation ou une requête SQL.

### 10.3 Exécuteurs privés

Deux exécuteurs bornés suffisent :

- `dls-db-1` : un seul writer SQLite, requêtes et migrations ;
- `dls-cpu-N` : 1 à 2 threads maximum pour génération du plan, tri du classement et calculs purs.

Ils utilisent des files bornées, des noms de threads, un gestionnaire d'erreurs et une stratégie de rejet explicite. Les pools communs (`ForkJoinPool.commonPool`) sont évités. Tous les exécuteurs sont arrêtés et attendus avec délai dans `onDisable`.

Sur MariaDB optionnel, un petit pool JDBC pourra être utilisé ; sur SQLite, multiplier les writers n'apporte rien et augmente les contentions.

### 10.4 Budget de tick

Les objectifs initiaux à mesurer sont :

- listener de casse : p95 inférieur à 100 µs ;
- rafraîchissement scoreboard/boss bar : au plus une fois par seconde ;
- aucun tri global à chaque bloc cassé ;
- aucune écriture SQL par bloc ;
- placement de mine adaptatif avec budget par tick ;
- aucun chargement synchrone de chunk lors d'une téléportation.

Les scores sont gardés en mémoire pendant la partie et sauvegardés par checkpoint groupé seulement si la récupération de session est activée ultérieurement.

### 10.5 Téléportations

Les destinations sont préparées et leurs chunks chargés avant le départ. L'API `teleportAsync` est privilégiée pour éviter les chargements synchrones ; son `Future` ne doit jamais être bloqué avec `get()` ou `join()` sur un thread de région.

Pour une entité Folia, toute continuation qui modifie le joueur repasse explicitement par son `EntityScheduler`.

## 11. Persistance

SQLite est la valeur par défaut, avec :

```text
PRAGMA journal_mode = WAL
PRAGMA synchronous = FULL
PRAGMA foreign_keys = ON
PRAGMA busy_timeout = 5000
```

Tables principales proposées :

- `schema_version` ;
- `game_session` : phase, seed, arène, échéances et résultat ;
- `participant` : statut, score, délais et rang ;
- `inventory_snapshot` : blobs versionnés, checksum, phase et timestamps ;
- `reward_outbox` : récompense à remettre et état ;
- `player_stats` : parties, victoires et meilleur score, facultatif.

Les migrations SQL sont versionnées dans le JAR et appliquées avant d'accepter une commande. Une sauvegarde de la base est recommandée avant toute migration destructive.

## 12. Récompense et compteur de victoires

Le domaine émet `WinnerDeclared(sessionId, playerIds)`. L'application inscrit ensuite une ligne unique dans `reward_outbox` avant d'appeler le fournisseur de récompense.

Stratégies prévues :

- `InventoryKeyReward` : item de clé remis après restauration, avec boîte aux lettres si le gagnant est hors ligne ;
- `ProviderApiReward` : API typée d'un plugin de crates ;
- `ConsoleCommandReward` : une ou plusieurs commandes configurables avec `%player%`, `%uuid%` et `%session%`.

Une commande arbitraire exécutée dans un autre plugin ne peut pas offrir une garantie mathématique « exactement une fois » lors d'un crash entre l'exécution et l'accusé de réception. L'adaptateur doit donc annoncer une sémantique `at-least-once`, préférer une API idempotente, conserver une clé d'idempotence quand le fournisseur l'accepte et fournir `/dls admin rewards pending|retry|mark-delivered`.

Le compteur de victoires, s'il est activé, est incrémenté dans la même transaction que la création de l'outbox. Le classement de la partie suivante repart toujours de zéro.

## 13. Intégrations

### 13.1 Politique générale

Le plugin doit privilégier les capacités natives et les contrats génériques plutôt qu'accumuler des dépendances fragiles. Chaque intégration est :

- facultative ;
- détectée par nom et version ;
- chargée dans un package isolé ;
- désactivable en configuration ;
- remplaçable par un fallback natif ou une commande console.

### 13.2 Sélection et protections

- WorldEdit ;
- FastAsyncWorldEdit via sa compatibilité d'API WorldEdit ;
- WorldGuard : respect des événements déjà annulés et documentation des flags nécessaires ;
- CoreProtect : aucune dépendance nécessaire, mais possibilité de désactiver la journalisation massive de la zone DLS côté administration.

### 13.3 Warps, spawn et accès

La commande officielle reste `/dls join`. Le message Adventure utilise un `ClickEvent.runCommand` vers cette commande.

Pour `/warp DLS`, trois modes sont proposés :

1. **Destination bridge recommandé** : le fournisseur de warp téléporte vers un point d'accès DLS ; l'arrivée dans son rayon déclenche l'inscription transactionnelle.
2. **Exact command bridge** : intercepter uniquement `/warp DLS`, l'annuler et appeler `/dls join`. Ce mode est désactivé par défaut, car l'interception de commandes est plus intrusive.
3. **Alias administrateur** : configuration du fournisseur ou de `commands.yml` vers la commande DLS lorsque la plateforme le permet.

La sortie utilise d'abord une location native configurée. Des adaptateurs ou commandes génériques permettent de fonctionner avec EssentialsX, CMI, HuskHomes, MyWarp, PlayerWarps et les plugins de hub courants sans rendre leur API obligatoire.

### 13.4 Crates et affichage

Le `ConsoleCommandReward` rend compatibles la plupart des plugins de crates, notamment ExcellentCrates, CrazyCrates, CrateReloaded et équivalents, dès lors qu'ils exposent une commande d'attribution. Une API dédiée ne sera ajoutée que si elle apporte l'idempotence ou la remise hors ligne.

PlaceholderAPI et MiniPlaceholders pourront exposer :

- `%dls_phase%` ;
- `%dls_time_left%` ;
- `%dls_score%` ;
- `%dls_rank%` ;
- `%dls_wins%`.

LuckPerms et Vault n'exigent aucun hook direct : DLS s'appuie sur les permissions Bukkit standard.

## 14. Paquets, NMS et affichage

### Décision

**Ni ProtocolLib, ni PacketEvents, ni wrapper NMS maison ne sont recommandés pour la V1.**

Toutes les fonctionnalités décrites disposent d'une API de haut niveau : événements de blocs/TNT/dégâts, PDC, Adventure, boss bars, inventaires et téléportation asynchrone. Une couche paquet ajouterait des risques de désynchronisation client/serveur et une matrice de compatibilité Paper/Folia inutile.

Adventure natif suffit pour :

- message cliquable d'inscription ;
- hover text ;
- boss bar du temps restant ;
- action bar du score ;
- titres de début et fin.

PacketEvents ne deviendrait pertinent que pour une future mine virtuelle différente par joueur, des blocs client-side ou un HUD impossible avec Adventure. Dans ce cas, il serait placé derrière un port et resterait facultatif. Un wrapper NMS maison doit rester le dernier recours, isolé dans un module versionné et couvert par des tests de démarrage sur chaque build cible.

## 15. Gestion mémoire et cycle de vie

Risques principaux et mesures :

- **Références `Player` conservées** : stocker uniquement UUID et DTO ; résoudre le joueur au dernier moment.
- **TNT disparues gardées en cache** : registre UUID avec expiration courte et purge périodique.
- **Participants déconnectés** : statuts persistés, éviction après restauration et fin de session.
- **Boss bars et audiences** : retirer explicitement tous les viewers au départ et à `onDisable`.
- **Tâches schedulées** : conserver des handles et annuler global, région et entité lors du nettoyage.
- **Chunks forcés** : compter les tickets acquis et tous les libérer dans un bloc de nettoyage idempotent.
- **Listeners d'intégrations rechargées** : enregistrer une fois, désenregistrer à la désactivation ; éviter les singletons statiques.
- **Executors et drivers JDBC** : fermer datasource, connexions et threads pour permettre le déchargement du classloader.
- **`ThreadLocal` d'une librairie** : ne pas exposer de long-lived thread aux objets du plugin ; arrêter les pools privés.
- **Futures tardifs** : toute continuation vérifie que le plugin est actif et que l'identifiant de session est encore courant.
- **Plan de mine surdimensionné** : volume maximal configurable, estimation mémoire avant allocation et refus explicite.
- **Snapshots historiques** : politique de rétention et purge par lots, jamais sur un thread de région.

Un diagnostic `/dls admin debug` affichera le nombre de participants, snapshots non restaurés, comptes TNT, tâches, chunks retenus, taille du plan et files des exécuteurs.

## 16. Packages Java

Package racine recommandé : `io.github.youpl.diglikesteve`.

```text
io.github.youpl.diglikesteve
├── core
│   ├── arena
│   ├── game
│   ├── player
│   ├── score
│   ├── tnt
│   ├── reward
│   └── shared
├── application
│   ├── command
│   ├── query
│   ├── service
│   └── port
│       ├── inbound
│       └── outbound
├── platform.paper
│   ├── bootstrap
│   ├── listener
│   ├── command
│   ├── gui
│   ├── scheduler
│   ├── world
│   ├── inventory
│   ├── audience
│   └── persistence
├── integration
│   ├── worldedit
│   ├── placeholderapi
│   ├── miniplaceholders
│   ├── warp
│   ├── spawn
│   └── crates
└── configuration
    ├── model
    ├── validation
    └── migration
```

Les listeners ne contiennent aucune règle de score complexe. Ils valident le contexte Minecraft, créent une commande immuable et délèguent au cas d'usage.

## 17. Configuration prévue

Fichiers proposés :

- `config.yml` : durées, arène, limites, stockage, politique de dégâts et intégrations ;
- `blocks.yml` : distribution, points et propriétés ;
- `messages.yml` : MiniMessage et locale ;
- `rewards.yml` : stratégies et commandes de Key Box ;
- `gui.yml` : disposition du menu administrateur ;
- `data/dls.db` : état durable.

Validations au démarrage :

- somme des poids égale à 100 % dans la tolérance définie ;
- matériaux valides sur 26.1.2 ;
- points non négatifs et dans les bornes ;
- deux coins présents dans le même monde ;
- volume inférieur au maximum ;
- durée d'inscription et de partie strictement positives ;
- destination de sortie sûre ;
- commande de récompense correctement échappée et sans placeholder inconnu.

Une erreur critique laisse le plugin chargé en mode administration mais interdit `/dls start`.

## 18. Commandes et permissions

Commandes cibles :

```text
/dls join
/dls leave
/dls top
/dls status
/dls admin menu
/dls admin pos1|pos2
/dls admin selection import
/dls admin prepare
/dls admin start [registration-seconds] [game-seconds]
/dls admin stop
/dls admin reload
/dls admin recovery list|restore
/dls admin rewards pending|retry|mark-delivered
/dls admin debug
```

Permissions :

```text
dls.join
dls.command.top
dls.admin
dls.admin.area
dls.admin.start
dls.admin.stop
dls.admin.reload
dls.admin.recovery
dls.admin.rewards
dls.admin.debug
```

Les commandes d'administration vérifient la phase et proposent une confirmation pour toute préparation qui écrasera une zone.

## 19. Observabilité

Les logs sont structurés autour de `sessionId`, `playerId`, `snapshotId` et `tntId`, sans sérialiser le contenu des inventaires.

Métriques recommandées :

- temps de préparation et blocs placés par seconde ;
- durée p50/p95/p99 des listeners critiques ;
- taille des files CPU/DB ;
- participants actifs/suspendus ;
- snapshots en attente ;
- comptes TNT non réglés ;
- temps de restauration ;
- erreurs par intégration.

Le plugin doit s'intégrer proprement aux profileurs serveur comme spark sans dépendre d'eux.

## 20. Sécurité et prévention des abus

- Toutes les identités sont des UUID, jamais des noms de joueurs comme clé primaire.
- La commande console de récompense provient uniquement de la configuration administrateur et ses placeholders sont échappés/validés.
- Un joueur ne score que si son statut est `ACTIVE` et si le bit de position passe de 0 à 1.
- Les événements annulés par une protection externe sont respectés.
- Les outils DLS ne sont pas identifiés par leur nom/lore mais par PDC de session.
- Les interactions hors arène et les explosions étrangères ne sont pas créditées.
- Les transitions sensibles, restaurations et récompenses possèdent des clés d'idempotence.
- Aucun fichier de snapshot n'est nommé à partir d'une entrée utilisateur non validée.
- La configuration et les migrations sont validées avant publication atomique.

## 21. Stratégie de tests

### Tests unitaires

- machine à états et transitions interdites ;
- distribution exacte, seed reproductible et somme des quotas ;
- mapping cuboïde/index ;
- CAS du bitset sous concurrence ;
- score normal et score TNT ;
- arrondis de demi-cœurs et plafonnement de pénalité ;
- délai de reconnexion à 29,999 s et 30,001 s ;
- stratégies d'égalité ;
- idempotence des restaurations et récompenses.

### Tests de propriétés et concurrence

- aucune distribution ne produit plus ou moins de blocs que le volume ;
- une position soumise simultanément par N threads n'est payée qu'une fois ;
- une TNT réglée plusieurs fois ne modifie le score qu'une fois ;
- une séquence arbitraire join/quit/timeout/stop ne produit jamais deux gardes actives.

### Tests d'intégration

- démarrage sur Paper 26.1.2 build 74 avec Java 25 ;
- démarrage sur une version Folia correspondante ;
- sélection avec WorldEdit puis avec FAWE sur Paper ;
- fonctionnement sans aucune intégration ;
- arrêt forcé entre chacune des phases de sauvegarde/restauration ;
- téléportation vers chunk chargé et non chargé ;
- explosions en chaîne, deux TNT proches et dégâts simultanés ;
- tests à volume élevé avec mesure TPS/MSPT et heap.

MockBukkit peut aider pour des listeners simples, mais ne valide ni les règles de thread Folia ni le comportement réel des explosions. Les scénarios critiques doivent tourner sur de vrais serveurs de test.

## 22. Plan d'implémentation

### Lot 1 — Fondations

- projet Gradle multi-module, Java 25, quality gates ;
- domaine, horloge, machine à états, configuration validée ;
- abstraction unifiée des schedulers Paper/Folia ;
- SQLite, migrations et récupération minimale.

### Lot 2 — Sûreté joueur

- garde transactionnelle d'inventaire ;
- kit PDC et restrictions ;
- join/leave, reconnexion 30 secondes et récupération au démarrage ;
- téléportation et spawn slots.

### Lot 3 — Mine et score

- définition native et import WorldEdit/FAWE ;
- génération exacte et placement progressif ;
- bitset anti-double-score ;
- casse, classement et affichage.

### Lot 4 — TNT

- propriété et propagation des chaînes ;
- compte d'explosion ;
- dégâts, pénalité et tests de concurrence ;
- confinement des dégâts aux blocs de l'arène.

### Lot 5 — Administration et intégrations

- GUI administrateur ;
- pont `/warp DLS`, spawn et placeholders ;
- outbox Key Box et adaptateurs de crates ;
- compteur optionnel de victoires.

### Lot 6 — Qualification

- tests Paper/Folia ;
- charge, profilage et réglage des budgets ;
- audit des scénarios de duplication ;
- documentation opérateur, sauvegarde et procédure de récupération.

## 23. Critères d'acceptation architecturaux

L'implémentation pourra être considérée conforme à ce DAT si :

- `dls-core` compile sans dépendance Minecraft ;
- aucun appel Bukkit/Paper n'est effectué depuis un exécuteur non propriétaire ;
- tous les accès joueur passent par l'`EntityScheduler` et tous les accès bloc différés par le `RegionScheduler` ;
- une interruption à n'importe quelle étape d'entrée/sortie ne duplique ni ne perd un item ;
- chaque position de la mine ne rapporte des points qu'une fois ;
- une TNT et ses réactions en chaîne ont un propriétaire traçable et un règlement idempotent ;
- une partie dure selon l'horloge murale configurée, indépendamment des TPS ;
- DLS démarre sans WorldEdit, FAWE, EssentialsX ou plugin de crates ;
- le JAR est testé séparément sur Paper et Folia ;
- toutes les tâches, caches, audiences, connexions et tickets sont libérés au nettoyage.

## 24. Références techniques

- [Paper — prise en charge de Paper et Folia](https://docs.papermc.io/paper/dev/folia-support/)
- [Paper — fonctionnement des régions Folia](https://docs.papermc.io/folia/reference/overview/)
- [Paper — prérequis Java](https://docs.papermc.io/paper/getting-started/)
- [Paper — configuration d'un projet](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper — téléportation asynchrone](https://docs.papermc.io/paper/dev/entity-teleport/)
- [Paper 26.1.2 — TNTPrimeEvent](https://jd.papermc.io/paper/26.1.2/org/bukkit/event/block/TNTPrimeEvent.html)
- [Paper 26.1.2 — EntityExplodeEvent](https://jd.papermc.io/paper/26.1.2/org/bukkit/event/entity/EntityExplodeEvent.html)
- [Paper 26.1.2 — EntityDamageEvent](https://jd.papermc.io/paper/26.1.2/org/bukkit/event/entity/EntityDamageEvent.html)
- [Paper 26.1.2 — TNTPrimed](https://jd.papermc.io/paper/26.1.2/org/bukkit/entity/TNTPrimed.html)
- [WorldEdit — Developer API](https://worldedit.enginehub.org/en/latest/api/)
- [WorldEdit — LocalSession et sélection](https://worldedit.enginehub.org/en/latest/api/concepts/local-sessions/)
- [FAWE — dépôt et compatibilité API WorldEdit](https://github.com/IntellectualSites/FastAsyncWorldEdit)

