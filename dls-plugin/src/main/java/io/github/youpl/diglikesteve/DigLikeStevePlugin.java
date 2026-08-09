package io.github.youpl.diglikesteve;

import io.github.youpl.diglikesteve.application.port.outbound.RewardProviderPort;
import io.github.youpl.diglikesteve.application.service.InventoryCustodyService;
import io.github.youpl.diglikesteve.application.service.MinePreparationService;
import io.github.youpl.diglikesteve.application.service.RecoveryService;
import io.github.youpl.diglikesteve.application.service.RegistrationService;
import io.github.youpl.diglikesteve.application.service.RewardService;
import io.github.youpl.diglikesteve.application.service.ScoringService;
import io.github.youpl.diglikesteve.application.service.SessionLifecycleService;
import io.github.youpl.diglikesteve.application.service.TntAccountingService;
import io.github.youpl.diglikesteve.configuration.model.DlsConfig;
import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.tnt.TntOwnershipRegistry;
import io.github.youpl.diglikesteve.integration.IntegrationRegistry;
import io.github.youpl.diglikesteve.platform.paper.DlsKeys;
import io.github.youpl.diglikesteve.platform.paper.FoliaSupport;
import io.github.youpl.diglikesteve.platform.paper.PaperLogAdapter;
import io.github.youpl.diglikesteve.platform.paper.audience.MessageCatalog;
import io.github.youpl.diglikesteve.platform.paper.audience.PaperNotificationAdapter;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import io.github.youpl.diglikesteve.platform.paper.command.DlsCommand;
import io.github.youpl.diglikesteve.platform.paper.command.SelectionDraft;
import io.github.youpl.diglikesteve.platform.paper.config.ConfigurationLoader;
import io.github.youpl.diglikesteve.platform.paper.gui.AdminMenu;
import io.github.youpl.diglikesteve.platform.paper.inventory.KitFactory;
import io.github.youpl.diglikesteve.platform.paper.inventory.PaperInventoryCustodyAdapter;
import io.github.youpl.diglikesteve.platform.paper.listener.ConnectionListener;
import io.github.youpl.diglikesteve.platform.paper.listener.DamageListener;
import io.github.youpl.diglikesteve.platform.paper.listener.MiningListener;
import io.github.youpl.diglikesteve.platform.paper.listener.RestrictionListener;
import io.github.youpl.diglikesteve.platform.paper.listener.TntListener;
import io.github.youpl.diglikesteve.platform.paper.persistence.SqliteDatabase;
import io.github.youpl.diglikesteve.platform.paper.persistence.SqliteRewardOutboxRepository;
import io.github.youpl.diglikesteve.platform.paper.persistence.SqliteSessionRepository;
import io.github.youpl.diglikesteve.platform.paper.persistence.SqliteSnapshotRepository;
import io.github.youpl.diglikesteve.platform.paper.persistence.SqliteStatsRepository;
import io.github.youpl.diglikesteve.platform.paper.player.PaperPlayerAdapter;
import io.github.youpl.diglikesteve.platform.paper.reward.ConsoleCommandReward;
import io.github.youpl.diglikesteve.platform.paper.reward.InventoryKeyReward;
import io.github.youpl.diglikesteve.platform.paper.scheduler.BoundedExecutors;
import io.github.youpl.diglikesteve.platform.paper.scheduler.PaperSchedulerAdapter;
import io.github.youpl.diglikesteve.platform.paper.world.PaperMineWriter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Point d'entrée du plugin (DAT §4.2, module {@code dls-plugin}).
 *
 * <p>Cette classe se limite à l'assemblage : elle instancie les adaptateurs, câble les services
 * et enregistre les adaptateurs entrants. Aucune règle de jeu n'y figure.</p>
 *
 * <p>Si la configuration est invalide, le plugin reste chargé en mode administration afin que
 * l'opérateur puisse corriger puis recharger, mais {@code /dls admin start} est refusé.</p>
 */
public final class DigLikeStevePlugin extends JavaPlugin {

    private PaperSchedulerAdapter schedulers;
    private SqliteDatabase database;
    private DlsServices services;
    private IntegrationRegistry integrations;
    private TntListener tntListener;
    private PaperLogAdapter log;
    private ConfigurationLoader configuration;

    @Override
    public void onEnable() {
        long startedAt = System.nanoTime();
        boolean debug = getConfig().getBoolean("debug", false);
        this.log = new PaperLogAdapter(getLogger(), debug);
        this.configuration = new ConfigurationLoader(this);
        configuration.saveDefaults();

        DlsConfig config;
        try {
            config = configuration.reload();
        } catch (RuntimeException invalid) {
            getLogger().severe("Configuration invalide : " + invalid.getMessage());
            getLogger().severe("Le plugin reste chargé en mode administration ; /dls admin start est refusé.");
            config = null;
        }
        if (config == null) {
            // Mode dégradé : on ne câble rien qui pourrait lancer une partie.
            return;
        }

        Clock clock = Clock.system();
        DlsKeys keys = new DlsKeys(this);

        BoundedExecutors executors = new BoundedExecutors(getLogger(),
                Runtime.getRuntime().availableProcessors(), 4_096);
        this.schedulers = new PaperSchedulerAdapter(this, executors);

        Path databaseFile = getDataFolder().toPath().resolve(config.storage().file());
        this.database = SqliteDatabase.open(databaseFile, config.storage().busyTimeoutMillis(), log);

        SqliteSnapshotRepository snapshots = new SqliteSnapshotRepository(database, log);
        SqliteSessionRepository sessions = new SqliteSessionRepository(database, log);
        SqliteRewardOutboxRepository outbox = new SqliteRewardOutboxRepository(database, log);
        SqliteStatsRepository stats = new SqliteStatsRepository(database, log);

        MessageCatalog messages = new MessageCatalog();
        messages.load(configuration.read("messages.yml"));

        KitFactory kits = new KitFactory(keys);
        PaperInventoryCustodyAdapter custodyAdapter = new PaperInventoryCustodyAdapter(this, kits, clock);
        PaperPlayerAdapter players = new PaperPlayerAdapter(this, custodyAdapter);
        config.exitDestination().ifPresent(players::exitDestination);

        PaperNotificationAdapter notifications = new PaperNotificationAdapter(messages, players);
        PaperMineWriter mineWriter = new PaperMineWriter(this);

        InventoryCustodyService custody =
                new InventoryCustodyService(custodyAdapter, players, snapshots, schedulers, clock, log);
        RecoveryService recovery =
                new RecoveryService(snapshots, sessions, custody, players, schedulers, clock, log);
        RegistrationService registration =
                new RegistrationService(custody, recovery, snapshots, players, notifications, schedulers, log);
        ScoringService scoring = new ScoringService();
        TntOwnershipRegistry ownership = new TntOwnershipRegistry(clock, config.tnt().ownershipTtlSeconds());
        TntAccountingService tnt = new TntAccountingService(ownership, scoring, schedulers, notifications,
                clock, log, config.tnt().accountTtlSeconds());
        RewardService rewards = new RewardService(outbox, buildRewardProvider(config, keys, messages, players),
                players, schedulers, clock, log, config.reward().trackWins());
        MinePreparationService preparation = new MinePreparationService(schedulers, mineWriter, log);
        SessionLifecycleService lifecycle = new SessionLifecycleService(config, preparation, registration,
                scoring, tnt, rewards, notifications, players, schedulers, sessions, stats, clock, log);

        this.services = new DlsServices(lifecycle, registration, scoring, tnt, rewards, recovery, custody,
                notifications, players, schedulers, snapshots, stats, kits, keys, messages, clock, log);

        SelectionDraft selection = new SelectionDraft();
        AdminMenu adminMenu = new AdminMenu(services, configuration, selection);
        registerListeners(adminMenu);
        registerCommands(adminMenu, selection);

        this.integrations = new IntegrationRegistry(this, services, log);
        integrations.enableAll(config.integrations(), selection);

        // Reprise obligatoire des gardes laissées par un arrêt anormal (DAT §8.5).
        recovery.recoverOnStartup();
        scheduleMaintenance(config);

        log.info("Dig Like Steve activé",
                "plateforme", FoliaSupport.platformName(),
                "version", getPluginMeta().getVersion(),
                "arène", config.arena().map(Object::toString).orElse("non définie"),
                "démarrageMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }

    private RewardProviderPort buildRewardProvider(DlsConfig config, DlsKeys keys, MessageCatalog messages,
                                                   PaperPlayerAdapter players) {
        if ("inventory-key".equalsIgnoreCase(config.reward().strategy())) {
            return new InventoryKeyReward(this, keys, messages,
                    config.reward().itemMaterial(), config.reward().itemName());
        }
        return new ConsoleCommandReward(config.reward().commands(), players, schedulers);
    }

    private void registerListeners(AdminMenu adminMenu) {
        this.tntListener = new TntListener(services);
        var manager = getServer().getPluginManager();
        manager.registerEvents(new MiningListener(services), this);
        manager.registerEvents(tntListener, this);
        manager.registerEvents(new DamageListener(services, tntListener), this);
        manager.registerEvents(new ConnectionListener(services), this);
        manager.registerEvents(new RestrictionListener(services), this);
        manager.registerEvents(adminMenu, this);
    }

    private void registerCommands(AdminMenu adminMenu, SelectionDraft selection) {
        DlsCommand executor = new DlsCommand(services, configuration, adminMenu, selection);
        PluginCommand command = getCommand("dls");
        if (command == null) {
            log.error("Commande /dls absente de plugin.yml", null);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** Rejeu périodique de l'outbox et purge des snapshots restaurés (DAT §12 et §15). */
    private void scheduleMaintenance(DlsConfig config) {
        long periodTicks = Math.max(20L, config.reward().retryPeriodSeconds() * 20L);
        schedulers.globalRepeating(() -> services.rewards().deliverPending(16), periodTicks, periodTicks);

        long retentionMillis = TimeUnit.DAYS.toMillis(config.storage().snapshotRetentionDays());
        schedulers.globalRepeating(() -> schedulers.database(() -> {
            long threshold = services.clock().epochMillis() - retentionMillis;
            int purged = services.snapshots().purgeRestoredOlderThan(threshold, 200);
            if (purged > 0) {
                log.debug("Snapshots purgés", "count", purged);
            }
        }), 20L * 300L, 20L * 3600L);
    }

    @Override
    public void onDisable() {
        // Ordre imposé par le DAT §8.5 : fermer le jeu, puis sauver les inventaires, puis
        // seulement arrêter les exécuteurs et fermer la base.
        if (services != null) {
            services.lifecycle().onDisable();
        }
        if (integrations != null) {
            integrations.disableAll();
        }
        if (tntListener != null) {
            tntListener.clear();
        }
        if (services != null) {
            try {
                services.recovery().onDisable();
            } catch (RuntimeException failure) {
                getLogger().severe("Restauration d'arrêt incomplète : " + failure.getMessage());
            }
            services.notifications().clearAllAudiences();
            services.players().clearFrozen();
        }
        if (schedulers != null) {
            schedulers.shutdown();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("Dig Like Steve désactivé");
    }
}
