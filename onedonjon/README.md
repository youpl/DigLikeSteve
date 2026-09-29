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
| `/donjon` | Éditeur (admin) ou liste |
| `/donjon play <id>` | Créer / rejoindre un groupe |
| `/donjon launch` | Le chef lance la partie |
| `/donjon leave`, `status`, `list` | Quitter, état, liste |
| `/donjon start <id>` | Lancement immédiat en solo (admin) |
| `/donjon stop [id]`, `wand`, `reload` | Administration |

Permissions : `onedonjon.play` (défaut : tous), `onedonjon.admin` (défaut : op).

## Limites connues (v0.1)

- Une seule partie à la fois par donjon (pas d'instances copiées du monde).
- Paper uniquement (pas de support Folia).
- Les portes sont remplies de leur matériau puis passées à l'air : la zone doit être vide à l'ouverture.
- Les intégrations tierces n'ont pas pu être testées contre les vrais plugins ; seul le démarrage sur Paper 26.1.2
  sans plugin tiers est vérifié (les menus n'ont pas été exercés en jeu). Signalez toute signature d'API différente.
