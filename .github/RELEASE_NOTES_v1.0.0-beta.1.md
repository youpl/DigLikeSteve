Première version publiée de **Dig Like Steve**, mini-jeu de minage à partie unique pour
**Paper `26.1.2.build.74-stable`** et **Folia**, en **Java 25**.

Implémentation complète du document d'architecture technique `DAT.md` (lots 1 à 5).

## Ce que cette version garantit

- **Aucune duplication ni perte d'inventaire.** L'inventaire n'est jamais vidé avant qu'un
  snapshot complet ait été persisté *et confirmé*. La restauration utilise `setContents` et des
  slots exacts, jamais `addItem`. Un index unique partiel en base rend impossible une deuxième
  garde active pour le même joueur.
- **Chaque position ne rapporte qu'une fois.** Un octet par position mémorise le type
  initialement généré, un bit atomique la consommation. Une casse manuelle et une explosion
  visant le même bloc au même tick sont départagées par un `compare-and-set`.
- **Les durées suivent l'horloge murale.** Une baisse de TPS ne transforme jamais dix minutes en
  quinze.
- **Le score TNT est traçable et réglé une seule fois**, chaînes d'explosions comprises, sans NMS
  ni couche paquet.
- **Compatibilité Folia réelle** : chaque accès bloc différé passe par le `RegionScheduler`
  propriétaire, chaque accès joueur par son `EntityScheduler`. `dls-core` ne compile contre
  aucune classe Minecraft.

## Installation

1. Déposez `DigLikeSteve-1.0.0-beta.1.jar` dans `plugins/`.
2. Démarrez le serveur une fois. Le driver SQLite est téléchargé par le *LibraryLoader* de Paper :
   le premier démarrage exige un accès réseau, les suivants non.
3. Éditez `plugins/DigLikeSteve/config.yml` et `blocks.yml`, définissez la zone, puis lancez avec
   `/dls admin start`.

Le plugin démarre sans WorldEdit, FAWE, EssentialsX, PlaceholderAPI ni plugin de crates.

## Vérifié sur cette version

Cycle complet exercé sur Paper 26.1.2 build 74 avec Java 25 : préparation de 32 000 blocs, blocs
réellement posés dans le monde, inscriptions, annulation sans joueur, restauration, nettoyage et
retour à `IDLE`. Second `/dls admin start` immédiat correctement refusé. 42 tests unitaires et de
concurrence au vert.

## Pourquoi une bêta

- La **qualification Folia** reste à mener : le code respecte l'affinité de région, mais seul
  Paper a été exercé.
- Les **tests de charge et le profilage** du lot 6 ne sont pas faits.
- `MiniPlaceholders` et `integrations.coreprotect-skip-logging` ont leur clé de configuration
  sans implémentation ; le backend FAWE d'accélération et les métriques p50/p95/p99 non plus.

Le détail complet figure dans le [CHANGELOG](CHANGELOG.md).
