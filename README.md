# Dig Like Steve

Mini-jeu de minage à partie unique pour **Paper `26.1.2.build.74-stable`** et **Folia**, écrit en
**Java 25**.

Un administrateur définit un cuboïde. Avant chaque partie, le plugin le remplit avec une
distribution pondérée de blocs. Après 60 secondes d'inscription, les participants disposent de
10 minutes pour détruire les blocs et obtenir le meilleur score.

> **Version bêta.** Le cycle de jeu complet est vérifié sur Paper 26.1.2, mais la qualification
> Folia et les tests de charge du lot 6 restent à mener. Voir les
> [écarts connus](#écarts-connus-par-rapport-au-dat).

---

## Compilation

```bash
./gradlew build
```

Le JAR distribuable est produit dans `build/libs/DigLikeSteve-<version>.jar`.

Le toolchain Java 25 est résolu automatiquement par Gradle (résolveur foojay). Aucun JDK 25
installé manuellement n'est nécessaire.

## Installation

1. Déposez le JAR dans `plugins/`.
2. Démarrez le serveur une première fois. Le driver SQLite est téléchargé par le *LibraryLoader*
   de Paper — le premier démarrage exige donc un accès réseau, les suivants non.
3. Éditez `plugins/DigLikeSteve/config.yml` et `blocks.yml`.
4. Définissez la zone, puis lancez une partie.

Le plugin démarre **sans** WorldEdit, FAWE, EssentialsX, PlaceholderAPI ni plugin de crates.

## Définir la mine

Trois modes, au choix :

```
/dls admin pos1            # coin 1 à votre position
/dls admin pos2            # coin 2 à votre position
/dls admin selection import  # depuis WorldEdit ou FAWE
```

…ou directement `arena.corner-1` / `arena.corner-2` dans `config.yml`.

L'enregistrement passe par le menu (`/dls admin menu` → *Enregistrer l'arène*) ou par l'import.
Les changements d'arène, de blocs et de durée ne prennent effet qu'au repos : un rechargement
est refusé pendant une partie.

## Lancer une partie

```
/dls admin start [secondes-inscription] [secondes-partie]
```

La préparation écrase intégralement la zone : la commande demande une confirmation explicite.
Les inscriptions n'ouvrent qu'une fois la mine entièrement remplie.

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/dls join` | `dls.join` | S'inscrire (commande canonique du message cliquable) |
| `/dls leave` | `dls.join` | Quitter et récupérer son inventaire |
| `/dls top` | `dls.command.top` | Classement courant ou dernier classement figé |
| `/dls status` | — | Phase, joueurs, temps restant, progression |
| `/dls admin menu` | `dls.admin` | Menu d'administration |
| `/dls admin pos1\|pos2` | `dls.admin.area` | Coins de la zone |
| `/dls admin selection import` | `dls.admin.area` | Import WorldEdit/FAWE |
| `/dls admin start [reg] [jeu]` | `dls.admin.start` | Préparer puis ouvrir les inscriptions |
| `/dls admin stop` | `dls.admin.stop` | Arrêt idempotent |
| `/dls admin reload` | `dls.admin.reload` | Recharger la configuration validée |
| `/dls admin recovery list\|restore` | `dls.admin.recovery` | Gardes d'inventaire en attente |
| `/dls admin rewards pending\|retry\|mark-delivered` | `dls.admin.rewards` | Outbox de récompense |
| `/dls admin debug` | `dls.admin.debug` | Compteurs internes |

## Ce que le plugin garantit

**Aucune duplication ni perte d'inventaire.** L'inventaire n'est jamais vidé avant qu'un
snapshot complet ait été persisté *et confirmé*. Une déconnexion avant l'application du kit
laisse l'inventaire intact ; après, le snapshot devient la source de restauration. La
restauration utilise `setContents` et des slots exacts, jamais `addItem`. Un index unique partiel
en base rend impossible une deuxième garde active pour le même joueur.

**Chaque position ne rapporte qu'une fois.** Un octet par position mémorise le type
initialement généré, et un bit atomique indique si elle a déjà été payée. Une casse manuelle et
une explosion visant le même bloc au même tick sont départagées par un `compare-and-set`.
Replacer un bloc puis le recasser ne rapporte rien.

**Les durées suivent l'horloge murale.** Une baisse de TPS ne transforme jamais dix minutes en
quinze : les tâches périodiques ne font que rafraîchir l'affichage et comparer `now >= deadline`.

**Le score TNT est traçable et réglé une seule fois.** Le propriétaire est inscrit sur l'entité
via un PDC et dans un registre à durée de vie limitée ; les réactions en chaîne propagent
l'identité. Le règlement est différé de deux ticks pour réunir les blocs et les dégâts, puis
verrouillé par un drapeau atomique.

**Compatibilité Folia réelle.** Chaque accès bloc différé passe par le `RegionScheduler`
propriétaire de sa position, chaque accès joueur par son `EntityScheduler`, et les calculs purs
par des exécuteurs privés bornés. `dls-core` ne compile contre aucune classe Minecraft.

## Formule de pénalité TNT

```
halfHearts = ceil(dégâtsFinaux - epsilon)
rate       = min(1.00, halfHearts × 0.05)
penalty    = arrondi(pointsBruts × rate)
netPoints  = pointsBruts - penalty
```

La TNT vaut 0 point en elle-même : elle ne crédite que les blocs scorables qu'elle détruit. La
pénalité est plafonnée au gain de cette explosion et ne concerne que le propriétaire touché par
sa propre TNT — un adversaire ne peut donc pas faire perdre de points à une victime.

Exemple : 100 points de blocs détruits, 3 demi-cœurs de dégâts finaux → 15 de pénalité, 85 nets.

## Récompenses

La ligne d'outbox est écrite en base **avant** tout appel au fournisseur, dans la même
transaction que l'incrément du compteur de victoires. Sa clé d'idempotence `session:joueur` est
unique.

Une commande console arbitraire ne peut pas garantir « exactement une fois » si le serveur tombe
entre l'exécution et l'accusé de réception : la sémantique annoncée est **at-least-once**.
`/dls admin rewards` permet de lister, rejouer et marquer manuellement les entrées.

## Reprise après incident

Au démarrage, toute garde non terminale passe en restauration obligatoire ; le joueur concerné ne
peut rejoindre aucune nouvelle partie tant que son inventaire ne lui a pas été rendu. Aucun
snapshot n'est supprimé avant confirmation de sa restauration.

Un redémarrage complet **annule** la session en cours : la V1 ne tente pas de reprendre une
partie, seuls les inventaires sont sauvés.

## Architecture

```
dls-core/            domaine, règles, ports et cas d'usage — aucun import Minecraft
dls-platform-paper/  événements, schedulers Paper/Folia, monde, inventaires, SQLite
dls-integrations/    WorldEdit/FAWE, PlaceholderAPI, pont de warp
dls-plugin/          bootstrap JavaPlugin, assemblage, ressources
dls-test-support/    doublures de ports et bancs de test
```


## Configuration

| Fichier | Contenu |
|---|---|
| `config.yml` | Durées, arène, budgets, dégâts, TNT, stockage, warp, intégrations |
| `blocks.yml` | Distribution (somme = 100 %), points, blocs dangereux au spawn |
| `messages.yml` | Textes MiniMessage |
| `rewards.yml` | Stratégie et commandes de récompense |
| `gui.yml` | Menu d'administration |
| `data/dls.db` | État durable (SQLite, WAL) |

Une configuration invalide laisse le plugin chargé en mode administration : corrigez puis
relancez le serveur.
