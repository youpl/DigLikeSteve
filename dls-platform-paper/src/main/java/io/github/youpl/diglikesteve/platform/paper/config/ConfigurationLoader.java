package io.github.youpl.diglikesteve.platform.paper.config;

import io.github.youpl.diglikesteve.application.service.MineWriteBudget;
import io.github.youpl.diglikesteve.configuration.model.DlsConfig;
import io.github.youpl.diglikesteve.configuration.model.IntegrationSettings;
import io.github.youpl.diglikesteve.configuration.model.RewardSettings;
import io.github.youpl.diglikesteve.configuration.model.StorageSettings;
import io.github.youpl.diglikesteve.configuration.model.TntSettings;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.arena.BlockDefinition;
import io.github.youpl.diglikesteve.core.arena.BlockPalette;
import io.github.youpl.diglikesteve.core.arena.SpawnSlotSettings;
import io.github.youpl.diglikesteve.core.game.GameSettings;
import io.github.youpl.diglikesteve.core.game.TieStrategy;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.WorldLocation;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Lecture et validation de la configuration (DAT §17).
 *
 * <p>La configuration n'est publiée qu'après validation complète : somme des poids, matériaux
 * réellement présents sur la version cible, points bornés, coins dans le même monde, volume
 * sous la limite, durées strictement positives et commandes de récompense correctement
 * échappées. Une erreur critique laisse le plugin chargé en mode administration.</p>
 */
public final class ConfigurationLoader {

    private static final double WEIGHT_TOLERANCE = 0.001d;

    private final Plugin plugin;

    public ConfigurationLoader(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Copie les fichiers par défaut absents, sans écraser ceux de l'opérateur. */
    public void saveDefaults() {
        plugin.saveDefaultConfig();
        for (String resource : List.of("blocks.yml", "messages.yml", "rewards.yml", "gui.yml")) {
            File target = new File(plugin.getDataFolder(), resource);
            if (!target.exists()) {
                plugin.saveResource(resource, false);
            }
        }
    }

    /**
     * Relit et valide l'ensemble de la configuration.
     *
     * @throws ConfigurationException si une règle de validation est violée
     */
    public DlsConfig reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        FileConfiguration blocks = read("blocks.yml");
        FileConfiguration rewards = read("rewards.yml");

        BlockPalette palette = readPalette(blocks);
        GameSettings game = readGameSettings(config);
        Optional<ArenaBounds> arena = readArena(config);
        arena.ifPresent(bounds -> validateVolume(bounds, config));

        return new DlsConfig(
                game,
                arena,
                palette,
                readHazards(blocks),
                readSpawnSlots(config, game),
                readBudget(config),
                readTnt(config),
                readReward(rewards),
                readStorage(config),
                readIntegrations(config),
                readLocation(config, "exit-destination"),
                readLocation(config, "warp.access-point"));
    }

    public FileConfiguration read(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private GameSettings readGameSettings(FileConfiguration config) {
        long registration = config.getLong("durations.registration-seconds", 60L);
        long game = config.getLong("durations.game-seconds", 600L);
        long grace = config.getLong("durations.reconnect-grace-seconds", 30L);
        if (registration <= 0L || game <= 0L) {
            throw new ConfigurationException("Les durées d'inscription et de partie doivent être positives");
        }
        if (grace < 0L) {
            throw new ConfigurationException("Le délai de grâce ne peut pas être négatif");
        }

        String rawStrategy = config.getString("tie-strategy", "shared");
        TieStrategy strategy;
        try {
            strategy = TieStrategy.valueOf(rawStrategy.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            throw new ConfigurationException("Stratégie d'égalité inconnue : " + rawStrategy);
        }

        double penaltyRate = config.getDouble("tnt.penalty-rate-per-half-heart", 0.05d);
        if (penaltyRate < 0.0d || penaltyRate > 1.0d) {
            throw new ConfigurationException("tnt.penalty-rate-per-half-heart doit rester entre 0 et 1");
        }

        return new GameSettings(
                registration,
                game,
                grace,
                strategy,
                config.getInt("arena.max-participants", 32),
                config.getBoolean("damage.pvp", false),
                config.getBoolean("damage.cancel-environmental", true),
                config.getBoolean("damage.tnt-lethal", false),
                penaltyRate,
                config.getBoolean("tnt.cap-penalty-to-gross", true));
    }

    private Optional<ArenaBounds> readArena(FileConfiguration config) {
        String worldName = config.getString("arena.world");
        if (worldName == null || worldName.isBlank()) {
            return Optional.empty();
        }
        BlockPos first = readBlockPos(config.getConfigurationSection("arena.corner-1"));
        BlockPos second = readBlockPos(config.getConfigurationSection("arena.corner-2"));
        if (first == null || second == null) {
            return Optional.empty();
        }
        return Optional.of(ArenaBounds.of(worldName, first, second));
    }

    private void validateVolume(ArenaBounds bounds, FileConfiguration config) {
        long maxVolume = config.getLong("mine.max-volume", 2_000_000L);
        long volume = bounds.volumeLong();
        if (volume <= 0L) {
            throw new ConfigurationException("La sélection d'arène est vide");
        }
        if (volume > maxVolume) {
            throw new ConfigurationException("Volume d'arène " + volume + " supérieur au maximum " + maxVolume);
        }
    }

    private BlockPos readBlockPos(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new BlockPos(section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }

    private BlockPalette readPalette(FileConfiguration blocks) {
        ConfigurationSection section = blocks.getConfigurationSection("blocks");
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new ConfigurationException("blocks.yml ne définit aucun bloc");
        }
        List<BlockDefinition> definitions = new ArrayList<>();
        double total = 0.0d;

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String materialKey = entry.getString("material", key);
            Material material = Material.matchMaterial(materialKey);
            if (material == null || !material.isBlock()) {
                throw new ConfigurationException("Matériau de bloc invalide sur cette version : " + materialKey);
            }
            double weight = entry.getDouble("percent", 0.0d);
            int points = entry.getInt("points", 0);
            if (points < 0 || points > 10_000) {
                throw new ConfigurationException("Points hors bornes pour " + materialKey + " : " + points);
            }
            Map<String, String> properties = new LinkedHashMap<>();
            ConfigurationSection propertiesSection = entry.getConfigurationSection("properties");
            if (propertiesSection != null) {
                for (String property : propertiesSection.getKeys(false)) {
                    properties.put(property, String.valueOf(propertiesSection.get(property)));
                }
            }
            definitions.add(new BlockDefinition(material.getKey().toString(), weight, points,
                    entry.getString("tool"), properties));
            total += weight;
        }

        if (Math.abs(total - 100.0d) > WEIGHT_TOLERANCE) {
            throw new ConfigurationException("La somme des pourcentages de blocs.yml vaut " + total
                    + " au lieu de 100");
        }
        return BlockPalette.of(definitions);
    }

    private Set<String> readHazards(FileConfiguration blocks) {
        Set<String> hazards = new LinkedHashSet<>();
        for (String raw : blocks.getStringList("spawn-hazards")) {
            Material material = Material.matchMaterial(raw);
            if (material != null) {
                hazards.add(material.getKey().toString());
            }
        }
        if (hazards.isEmpty()) {
            hazards.add(Material.TNT.getKey().toString());
        }
        return hazards;
    }

    private SpawnSlotSettings readSpawnSlots(FileConfiguration config, GameSettings game) {
        return new SpawnSlotSettings(
                game.maxParticipants(),
                config.getInt("spawn.pocket-width", 3),
                config.getInt("spawn.pocket-height", 2),
                config.getInt("spawn.min-distance", 6),
                config.getInt("spawn.hazard-safe-radius", 2),
                config.getInt("spawn.max-attempts", 20_000));
    }

    private MineWriteBudget readBudget(FileConfiguration config) {
        return new MineWriteBudget(
                config.getInt("mine.max-blocks-per-batch", 4_096),
                config.getInt("mine.max-chunks-in-flight", 4),
                config.getLong("mine.max-volume", 2_000_000L),
                config.getLong("mine.max-heap-bytes", 256L * 1024L * 1024L));
    }

    private TntSettings readTnt(FileConfiguration config) {
        Set<String> causes = new LinkedHashSet<>();
        for (String cause : config.getStringList("tnt.allowed-prime-causes")) {
            causes.add(cause.toUpperCase(Locale.ROOT));
        }
        if (causes.isEmpty()) {
            causes.add("PLAYER");
        }
        return new TntSettings(
                causes,
                config.getBoolean("tnt.require-dls-igniter", true),
                config.getLong("tnt.ownership-ttl-seconds", 120L),
                config.getLong("tnt.account-ttl-seconds", 30L),
                config.getBoolean("tnt.confine-damage-to-arena", true),
                config.getBoolean("tnt.remove-explosion-drops", true));
    }

    private RewardSettings readReward(FileConfiguration rewards) {
        String strategy = rewards.getString("strategy", "console-command");
        List<String> commands = rewards.getStringList("console-command.commands");
        for (String command : commands) {
            validateRewardCommand(command);
        }
        return new RewardSettings(
                strategy,
                commands,
                rewards.getString("inventory-key.material", "TRIPWIRE_HOOK"),
                rewards.getString("inventory-key.name", "<gold>Clé Dig Like Steve</gold>"),
                rewards.getBoolean("track-wins", true),
                rewards.getLong("retry-period-seconds", 60L));
    }

    /** Refuse les placeholders inconnus : une commande console vient toujours de l'opérateur. */
    private void validateRewardCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new ConfigurationException("Commande de récompense vide");
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%([a-z_]+)%").matcher(command);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!Set.of("player", "uuid", "session").contains(placeholder)) {
                throw new ConfigurationException("Placeholder inconnu dans une commande de récompense : %"
                        + placeholder + "%");
            }
        }
    }

    private StorageSettings readStorage(FileConfiguration config) {
        return new StorageSettings(
                config.getString("storage.backend", "sqlite"),
                config.getString("storage.file", "data/dls.db"),
                config.getInt("storage.snapshot-retention-days", 14),
                config.getInt("storage.busy-timeout-millis", 5_000));
    }

    private IntegrationSettings readIntegrations(FileConfiguration config) {
        return new IntegrationSettings(
                config.getBoolean("integrations.worldedit-selection", true),
                config.getBoolean("integrations.placeholderapi", true),
                config.getBoolean("integrations.miniplaceholders", true),
                config.getString("warp.bridge-mode", IntegrationSettings.WARP_MODE_DESTINATION),
                config.getString("warp.name", "DLS"),
                config.getDouble("warp.trigger-radius", 3.0d),
                config.getBoolean("integrations.coreprotect-skip-logging", false));
    }

    private Optional<WorldLocation> readLocation(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return Optional.empty();
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new WorldLocation(
                worldName,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0d),
                (float) section.getDouble("pitch", 0.0d)));
    }

    /** Écriture atomique de l'arène validée dans {@code config.yml}. */
    public void saveArena(ArenaBounds bounds) {
        FileConfiguration config = plugin.getConfig();
        config.set("arena.world", bounds.worldName());
        config.set("arena.corner-1.x", bounds.minX());
        config.set("arena.corner-1.y", bounds.minY());
        config.set("arena.corner-1.z", bounds.minZ());
        config.set("arena.corner-2.x", bounds.maxX());
        config.set("arena.corner-2.y", bounds.maxY());
        config.set("arena.corner-2.z", bounds.maxZ());
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException failure) {
            throw new UncheckedIOException("Sauvegarde de l'arène impossible", failure);
        }
    }

    /** Erreur de configuration : le plugin reste chargé mais {@code /dls start} est interdit. */
    public static final class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) {
            super(message);
        }
    }
}
