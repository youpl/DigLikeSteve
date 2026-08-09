# Changelog

Toutes les évolutions notables de Dig Like Steve sont consignées ici.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le projet respecte
le [versionnage sémantique](https://semver.org/lang/fr/).

## [1.0.0-beta.1] — 2026-08-09

Première version publiée. Implémentation complète du document d'architecture technique
[`DAT.md`](DAT.md), lots 1 à 5. Diffusée en bêta : la qualification Folia et les scénarios de
charge du lot 6 restent à mener.

### Ajouté

#### Cycle de vie et arène
- Machine à états de session `IDLE → PREPARING → REGISTRATION → STARTING → RUNNING → FINISHING
  → RESTORING → CLEANUP → IDLE`, avec branche `RECOVERY` sur erreur critique. Chaque transition
  est validée par comparaison atomique.
- Définition de la zone par `/dls admin pos1|pos2`, par import WorldEdit/FAWE ou directement
  dans `config.yml`.
- Génération exacte de la mine par méthode des plus forts restes puis mélange de Fisher-Yates :
  la somme des quotas vaut toujours le volume, et une graine reproduit exactement la même mine.
- Écriture progressive par chunks et par lots bornés, avec tickets de chunks comptés et libérés.
- Poches de spawn reproductibles : air garanti, sol stable, distance minimale entre joueurs et
  purge des blocs dangereux du rayon de sécurité — par échange, ce qui préserve les quotas.

#### Score
- Index compact anti-double-score : un octet par position pour le type généré, un bit atomique
  pour la consommation. Une casse manuelle et une explosion visant le même bloc au même tick sont
  départagées par un `compare-and-set`.
- Attribution des TNT sans NMS : `TNTPrimeEvent` retient l'intention, `EntitySpawnEvent` inscrit
  le propriétaire sur l'entité via PDC et dans un registre à durée de vie limitée,
  `EntityExplodeEvent` additionne les blocs consommés. Les réactions en chaîne propagent
  l'identité, avec `TNTPrimed#getSource()` en secours.
- Pénalité TNT différée de deux ticks pour réunir l'événement de blocs et celui de dégâts, puis
  réglée une seule fois : `penalty = arrondi(brut × min(1, demiCœurs × 0.05))`, plafonnée au gain
  de l'explosion concernée et appliquée au seul propriétaire touché par sa propre TNT.
- Classement avec rangs partagés et stratégies d'égalité `shared`, `first-reached` et `random`,
  cette dernière reproductible depuis la graine de session.

#### Garde d'inventaire
- Saga d'entrée transactionnelle : l'inventaire n'est jamais vidé avant qu'un snapshot complet
  ait été persisté **et confirmé**. Une déconnexion avant l'application du kit laisse l'inventaire
  intact ; après, le snapshot devient la source de restauration.
- Restauration par `setContents` et slots exacts, jamais `addItem` : ni doublon, ni débordement
  au sol. Un second appel voit `RESTORED` et ne fait rien.
- Snapshot binaire versionné avec checksum CRC32 : inventaire, armure, off-hand, curseur, slot
  tenu, niveau et expérience, mode de jeu, vie, faim, saturation, effets, vol et emplacement
  d'origine. L'Ender Chest n'est ni vidé ni sauvegardé.
- Délai de grâce de 30 secondes après déconnexion, puis disqualification et restauration à la
  prochaine connexion. Une restauration en attente est toujours traitée avant toute nouvelle
  inscription.
- `RecoveryService` au démarrage : toute garde non terminale passe en restauration obligatoire et
  bloque le joueur concerné tant que son inventaire ne lui a pas été rendu.

#### Kit et restrictions
- Kit incassable marqué au PDC de session : pioche, hache et pelle en diamant, cisaille, briquet.
  Reconnu par son PDC et non par son nom, il est supprimé lors de toute restauration.
- Politiques de restriction courtes et testables : placement, drop et ramassage, conteneurs,
  craft, portails, changements de monde, téléportations non issues du plugin, franchissement de
  la frontière par les liquides et les pistons.

#### Plateforme et persistance
- Abstraction unifiée des schedulers Paper et Folia : région globale pour l'état du jeu,
  `RegionScheduler` pour les blocs, `EntityScheduler` pour les joueurs, exécuteurs privés bornés
  `dls-db-1` et `dls-cpu-N` pour les calculs purs et la base.
- Durées mesurées sur une horloge murale injectée : une baisse de TPS ne rallonge jamais la
  partie. L'horloge est pilotable dans les tests.
- SQLite en WAL avec migrations versionnées dans le JAR, appliquées avant toute commande. Un index
  unique partiel interdit structurellement deux gardes actives pour un même joueur.
- Téléportations via `teleportAsync`, chunks préchargés, aucun `Future` bloqué sur un thread de
  région.

#### Récompenses et intégrations
- Outbox de récompense écrite avant tout appel au fournisseur, dans la même transaction que
  l'incrément du compteur de victoires ; clé d'idempotence `session:joueur` unique.
- Fournisseurs `console-command` (compatible ExcellentCrates, CrazyCrates, CrateReloaded…) et
  `inventory-key`, tous deux annonçant une sémantique *at-least-once*, avec disjoncteur simple et
  commandes `/dls admin rewards pending|retry|mark-delivered`.
- Intégrations facultatives détectées par nom et isolées : WorldEdit/FAWE pour l'import de
  sélection, PlaceholderAPI (`%dls_phase%`, `%dls_time_left%`, `%dls_score%`, `%dls_rank%`,
  `%dls_wins%`), pont `/warp DLS` en mode destination ou interception stricte.
- Affichage entièrement Adventure : message d'inscription cliquable, boss bar, action bar,
  titres. Aucune couche paquet, aucun NMS.

#### Administration
- Commandes `/dls join|leave|top|status` et `/dls admin
  menu|pos1|pos2|selection import|prepare|start|stop|reload|recovery|rewards|debug`, avec
  complétion et permissions dédiées.
- Menu d'administration en jeu opérant sur un brouillon ; validation et écriture atomiques,
  refusées hors phase `IDLE`.
- Confirmation explicite exigée avant toute préparation, puisqu'elle écrase la zone.

### Vérifié
- Démarrage et cycle de partie complet sur Paper `26.1.2.build.74-stable` avec Java 25 :
  préparation de 32 000 blocs, blocs réellement posés, inscriptions, annulation sans joueur,
  restauration, nettoyage et retour à `IDLE`.
- Second `/dls admin start` immédiat correctement refusé.
- Démarrage sans WorldEdit, FAWE, PlaceholderAPI ni plugin de crates.
- 42 tests unitaires et de concurrence : transitions interdites, quotas exacts et graine
  reproductible, mapping cuboïde/index, `compare-and-set` du bitset sous 16 threads, arrondis de
  demi-cœurs et plafonnement, frontière de reconnexion à 29,999 s et 30,001 s, stratégies
  d'égalité, idempotence des règlements.

### Limites connues
- La qualification Folia reste à mener : le code respecte l'affinité de région, mais seul Paper
  a été exercé.
- `MiniPlaceholders` et `integrations.coreprotect-skip-logging` ont leur clé de configuration
  sans implémentation.
- `/dls admin prepare` est un alias de `/dls admin start`.
- Le backend FAWE d'accélération du remplissage et les métriques p50/p95/p99 ne sont pas
  implémentés.
- La reprise d'une session après redémarrage n'est pas retenue : un redémarrage annule la partie
  et seuls les inventaires sont sauvés.

[1.0.0-beta.1]: https://github.com/youpl/DigLikeSteve/releases/tag/v1.0.0-beta.1
