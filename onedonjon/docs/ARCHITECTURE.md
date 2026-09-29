# Architecture d'OneDonjon

## Vue d'ensemble

```
                 ┌──────────────────────────── OneDonjonPlugin ────────────────────────────┐
  Joueur / Admin │                                                                         │
      │          │   command/            gui/                    deluxe/                   │
      ├─ /donjon ┼─► DonjonCommand ──┬─► MainMenu, DungeonMenu,  DeluxeMenusBridge ──► fichiers
      │          │                   │   RoomMenu, WaveMenu,      (export YAML)         gui_menus/*.yml
      └─ menus ──┼───────────────────┘   MobMenu, TrapMenu,
                 │                       PuzzleMenu, NpcMenu,
                 │                       ActionsMenu, ItemPicker
                 │                            │ lit / modifie
                 │                            ▼
                 │   model/  Dungeon ─ Room ─ Wave ─ MobSpec │ Trap │ Puzzle │ NpcSpec │ Action
                 │                            │ persiste (YAML, 1 fichier / donjon)
                 │                            ▼
                 │   DungeonStorage ──► plugins/OneDonjon/dungeons/<id>.yml
                 │                            │ joue
                 │                            ▼
                 │   run/    RunManager ─► DungeonRun ─► RoomRun ─► MobFactory, ActionRunner
                 │           RunListener (mort, dégâts, clics, respawn)
                 │                            │ appelle
                 │                            ▼
                 │   integration/  Hooks (MythicMobs, MMOItems, Nexo, ModelEngine) · CitizensHook · Typewriter (commande)
                 │
                 │   bridge/  CoreBridge ◄── StandaloneCore (config locale, audit.log)
                 │                       ◄── OneCoreAdapter (API OneCore par réflexion)
                 └─────────────────────────────────────────────────────────────────────────┘
```

## Couches et règles de dépendance

| Package | Rôle | Peut dépendre de |
|---|---|---|
| `model` | Données pures (sans logique de jeu), sérialisation YAML, bornage des valeurs | `util` |
| `util` | `Msg` (MiniMessage restreint), `Limits`, `Reflect`, `Effects` | — |
| `bridge` | Configuration, droits et audit via OneCore, repli autonome | `util` |
| `integration` | Plugins tiers, **uniquement par réflexion** | `model`, `util` |
| `run` | Moteur de partie (salles, vagues, boss, pièges, énigmes) | `model`, `integration`, `bridge`, `util` |
| `gui` | Éditeur en menus internes | tout sauf `command` |
| `deluxe` | Génération des menus DeluxeMenus | `model`, `bridge` |
| `command` | `/donjon` | `gui`, `run`, `deluxe` |

`model` ne connaît ni Bukkit-runtime ni les plugins tiers ; il se teste sans serveur.

## Modèle de données

`Dungeon` → `Room[]` (ordre = ordre de jeu). Une `Room` porte : zone, porte, checkpoint, `SpawnPoint[]`,
`Wave[]` (→ `MobSpec[]`), `boss` (`MobSpec`), `Trap[]`, `Puzzle[]`, `NpcSpec[]`, et deux listes d'`Action`
(entrée / fin). Les `Action` (`MESSAGE`, `TITLE`, `COMMAND`, `TYPEWRITER`, `ITEM`, `XP`, `SOUND`) sont le
mécanisme unique de récompense et de scénario.

## Cycle d'une salle (`RoomRun`)

```
PENDING ──joueur dans la zone──► ACTIVE ──vagues + boss + énigmes obligatoires──► CLEARED
   │ PNJ visibles                  │ porte fermée, pièges actifs,                   │ porte ouverte,
   │                               │ onEnter                                        │ onClear, salle suivante
```
Pas de simulation : 5 ticks. Une exception dans une salle arrête proprement la partie (échec, nettoyage).

## Sources de configuration (`bridge`)

`plugin.core().setting(path, défaut)` lit d'abord OneCore (espace de noms `onedonjon`), puis `config.yml`.
Droits : `onedonjon.admin` reste **nécessaire** ; OneCore peut seulement restreindre. Audit : chaque
sauvegarde de donjon, création, suppression, test, arrêt et rechargement est journalisée (OneCore + `audit.log`).

Contrat OneCore supposé (à ajuster dans `OneCoreAdapter`) :
`getConfigValue(ns, path)`, `isAdmin(UUID)`, `audit(ns, actor, action, detail)`, `registerReloadListener(ns, Runnable)`.
Chaque capacité se dégrade indépendamment vers le mode autonome.

## Menus

- **Joueurs** (`/donjon menu`) et **administrateur** (`/donjon admin`) : servis par **DeluxeMenus** quand il est
  présent. OneDonjon génère `onedonjon_play.yml` et `onedonjon_admin.yml` (uniquement s'ils portent son marqueur,
  jamais un fichier tiers) et recharge DeluxeMenus. Les menus n'exécutent que des commandes `/donjon`, dont les droits
  sont revérifiés.
- **Édition détaillée** (salles, vagues, pièges…) : menus internes, car elle exige des listes paginées et des saisies
  dans le chat que DeluxeMenus ne sait pas faire. Le menu admin DeluxeMenus y mène via `/donjon edit <id>`.
- Sans DeluxeMenus : repli complet sur les menus internes.

## Sécurité (résumé, détail dans `SECURITY.md`)

Permission séparée pour les actions `COMMAND`, liste noire/blanche de commandes, validation des ids Typewriter,
MiniMessage sans clic/survol, bornage de toutes les valeurs YAML, plafonds de monstres/portes/explosions,
PNJ Citizens hors sauvegarde, édition interdite pendant une partie, audit des actions d'administration.
