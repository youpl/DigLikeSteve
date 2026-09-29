# OneDonjon

Créez des **donjons sur mesure entièrement en jeu**, via des menus (GUI) : zones, points d'apparition,
vagues, boss, pièges, énigmes, PNJ, dialogues et récompenses. Aucun fichier à éditer.

Cible : **Paper 26.1.2**, Java 25.

## Intégrations (toutes facultatives)

| Plugin | Ce que OneDonjon en fait |
|---|---|
| **MythicMobs** | Monstres et boss (liste choisie dans un menu) |
| **MMOItems** | Objets de récompense et objets-clés d'énigmes |
| **Nexo** | Objets de récompense et objets-clés d'énigmes |
| **ModelEngine** | Modèle appliqué à un monstre, un boss ou un PNJ |
| **Citizens** | PNJ dans les salles, clic droit → actions |
| **Typewriter** | Action `TYPEWRITER` : déclenche une entrée (dialogue, quête…) pour les joueurs |
| **DeluxeMenus** | Menus joueur et administrateur générés automatiquement |
| **OneCore** | Configuration centralisée, droits d'administration et audit (voir `docs/ARCHITECTURE.md`) |

Le plugin démarre sans aucun d'eux ; chaque option n'est active que si son plugin est présent.
Les appels passent par réflexion / commandes : aucune version de plugin tiers n'est imposée.

## Compilation

```bash
cd onedonjon
sh gradlew build      # JAR : build/libs/OneDonjon-<version>.jar
```

## Utilisation

1. Déposez le JAR dans `plugins/`, démarrez.
2. `/donjon` ouvre l'éditeur (permission `onedonjon.admin`).
3. **Nouveau donjon** → définissez départ, sortie, vies, limite de temps.
4. **Salles** → ajoutez des salles, jouées dans l'ordre. Pour chacune :
   - **Zone** et **porte** : `/donjon wand`, clic gauche/droit pour les deux coins, puis « prendre la sélection ».
     Entrer dans la zone active la salle ; la porte est fermée pendant le combat et ouverte à la fin.
   - **Points d'apparition** : placez-vous et cliquez « Ajouter ici ».
   - **Vagues** : délai, puis groupes de monstres (source vanilla / MythicMobs, quantité, vie, multiplicateur
     de dégâts, nom, effets, modèle ModelEngine, point d'apparition, actions à la mort).
   - **Boss** : même éditeur, avec barre de vie.
   - **Pièges** : zone de dégâts, potion, feu, tourelle à flèches, foudre, lanceur, explosion ;
     en continu ou au passage.
   - **Énigmes** : séquence de blocs à cliquer dans l'ordre, objet-clé (vanilla / MMOItems / Nexo),
     plaques à occuper ensemble.
   - **PNJ** (Citizens) et **actions** à l'entrée / à la fin de la salle.
5. **Tester** lance le donjon en solo. Le bouton *Vérification* liste ce qui manque.

Une salle est terminée quand toutes ses vagues, son boss et ses énigmes obligatoires sont réglés.

### Actions

Utilisées pour les récompenses, entrées/sorties de salle, morts de monstres, énigmes et PNJ :
`MESSAGE`, `TITLE`, `COMMAND` (`{player}`), `TYPEWRITER` (id d'entrée), `ITEM` (`source id quantité`), `XP`, `SOUND`.
La commande Typewriter est configurable (`typewriter-command` dans `config.yml`).

### Commandes

| Commande | Rôle |
|---|---|
| `/donjon` | Menu joueur, ou menu admin pour les administrateurs |
| `/donjon menu` | Liste des donjons jouables (DeluxeMenus, sinon menu interne) |
| `/donjon play <id>` / `launch` | Créer ou rejoindre un groupe / le chef lance |
| `/donjon leave`, `status`, `list` | Quitter, état, liste |
| `/donjon admin` | Menu admin (DeluxeMenus, sinon éditeur interne) |
| `/donjon editor` / `edit <id>` | Éditeur interne complet / éditer un donjon |
| `/donjon start <id>` | Lancement immédiat en solo (test) |
| `/donjon stop [id]`, `wand`, `reload`, `dmexport` | Administration |

Permissions : `onedonjon.play` (défaut : tous), `onedonjon.admin` (op),
`onedonjon.admin.commands` (op — nécessaire pour créer des actions `COMMAND` exécutées en console).

### Menus (DeluxeMenus)

Si DeluxeMenus est installé, OneDonjon génère `gui_menus/onedonjon_play.yml` et `onedonjon_admin.yml` et les
recharge à chaque changement (seuls les fichiers portant son marqueur sont réécrits). L'édition détaillée reste dans
les menus internes, ouverts depuis le menu admin (DeluxeMenus ne gère ni saisie de texte ni listes paginées).

### OneCore

Pont `CoreBridge` : configuration (valeur OneCore prioritaire, sinon `config.yml`), droits d'admin (OneCore ne peut
que restreindre) et audit (`audit.log` + OneCore). Renseignez `onecore.api-class` dans `config.yml`. Le contrat de
méthodes est **supposé** (`OneCoreAdapter`) et à ajuster à l'API réelle ; sans OneCore, mode autonome.

### Sécurité

Voir `docs/SECURITY.md` (revue complète et risques résiduels) : permission dédiée aux commandes console, listes
noire/blanche, validation des ids, MiniMessage sans clic/survol, valeurs YAML bornées, édition bloquée en partie, audit.

## Limites connues (v0.1)

- Une seule partie à la fois par donjon (pas d'instances copiées du monde).
- Tests réels uniquement sans plugin tiers : DeluxeMenus, OneCore et les autres intégrations sont couverts par des tests unitaires de génération / des replis, pas par un serveur équipé.
- Paper uniquement (pas de support Folia).
- Les portes sont remplies de leur matériau puis passées à l'air : la zone doit être vide à l'ouverture.
- Les intégrations tierces n'ont pas pu être testées contre les vrais plugins ; seul le démarrage sur Paper 26.1.2
  sans plugin tiers est vérifié (les menus n'ont pas été exercés en jeu). Signalez toute signature d'API différente.
