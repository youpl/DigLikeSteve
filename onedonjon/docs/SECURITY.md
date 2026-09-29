# Sécurité — revue du code et mesures

Revue complète du code `onedonjon/` (v0.1). « Corrigé » = corrigé dans cette version et couvert par un test quand c'est possible.

| # | Risque | Gravité | Statut |
|---|---|---|---|
| 1 | Une action `COMMAND` (console) permettait à tout détenteur de `onedonjon.admin` d'exécuter `op`, `stop`… | Haute | Corrigé : permission dédiée `onedonjon.admin.commands`, liste noire interne + `blocked-commands`, liste blanche `command-allowlist`, refus aussi via `execute`/`sudo`. Revérifié à l'exécution (YAML édité à la main) |
| 2 | Injection dans la commande Typewriter via l'id d'entrée (`x {player}; op y`) | Haute | Corrigé : id validé `[A-Za-z0-9_.:/-]`, sans espace ni caractère de contrôle, à la saisie et à l'exécution |
| 3 | MiniMessage : `<click:run_command:…>` dans messages / noms permettait de faire exécuter une commande au joueur | Haute | Corrigé : MiniMessage restreint (couleurs, styles, dégradés) — clic, survol, insertion, police désactivés |
| 4 | Menus d'édition utilisables après retrait du droit admin (menu resté ouvert) | Moyenne | Corrigé : droit revérifié à chaque clic |
| 5 | Édition d'un donjon pendant une partie (état incohérent, `ConcurrentModification`) | Moyenne | Corrigé : édition bloquée tant qu'une partie tourne ; `reload`/suppression arrêtent les parties |
| 6 | Course de données : `AsyncChatEvent` (thread asynchrone) lisait une `HashMap` modifiée par le thread principal | Moyenne | Corrigé : `ConcurrentHashMap`, retour sur le thread principal avant tout accès Bukkit |
| 7 | Fichiers YAML édités à la main : valeurs extrêmes (100 000 monstres, explosion 1e9, énumérations invalides) | Moyenne | Corrigé : bornage central (`Limits`), énumérations tolérantes, clés de bloc validées, plafond `max-mobs-per-room` |
| 8 | Id de donjon `../../x` → écriture hors du dossier `dungeons/` | Moyenne | Corrigé : id validé `[a-zA-Z0-9_-]{1,32}` à la création, au chargement et à la sauvegarde ; le nom de fichier doit correspondre à l'id |
| 9 | PNJ Citizens créés dans le registre principal → persistés sur disque, orphelins après un crash ; collisions d'ids entre registres | Moyenne | Corrigé : registre anonyme, identification par objet NPC, anti-spam de clic (2 s) |
| 10 | Porte géante (YAML) → remplissage de millions de blocs | Moyenne | Corrigé : plafond 20 000 blocs appliqué aussi à l'exécution |
| 11 | Exception à chaque tick (type d'entité non apparaissable, salle corrompue) → console inondée | Basse | Corrigé : création protégée ; une erreur de salle arrête la partie proprement |
| 12 | Explosion de piège retardée après la fin de partie | Basse | Corrigé : ignorée si la partie n'est plus en cours |
| 13 | Injection dans les menus DeluxeMenus générés (`%placeholder%`, `[console]`, `&`) via le nom d'un donjon | Moyenne | Corrigé : nom nettoyé ; les commandes ne contiennent que des ids validés ; fichiers tiers jamais écrasés |
| 14 | Falsification du journal d'audit (retours à la ligne dans un nom / détail) | Basse | Corrigé : caractères de contrôle retirés, longueur bornée, rotation à 5 Mo |
| 15 | Fuite de mémoire (`respawn`, `editing`) | Basse | Corrigé : purgés à l'arrêt / à la déconnexion |

## Limites connues (risque résiduel)

- La liste noire de commandes est une défense en profondeur, pas une barrière : un détenteur de
  `onedonjon.admin.commands` reste un administrateur de fait. Accordez-la avec parcimonie ou utilisez `command-allowlist`.
- Le contrat OneCore est supposé ; tant qu'il n'est pas confronté à l'API réelle, `OneCoreAdapter` retombe sur le mode autonome.
- L'écriture du journal d'audit est synchrone (petits volumes).
- Les intégrations tierces passent par réflexion : une signature d'API différente produit un avertissement, jamais une élévation de droits.
- Dépendance de build unique : `paper-api` (version épinglée). Le wrapper Gradle est celui du dépôt.
