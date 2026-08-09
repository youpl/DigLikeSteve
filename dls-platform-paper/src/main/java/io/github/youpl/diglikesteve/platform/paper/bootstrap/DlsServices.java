package io.github.youpl.diglikesteve.platform.paper.bootstrap;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.NotificationPort;
import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.application.port.outbound.SnapshotRepository;
import io.github.youpl.diglikesteve.application.port.outbound.StatsRepository;
import io.github.youpl.diglikesteve.application.service.InventoryCustodyService;
import io.github.youpl.diglikesteve.application.service.RecoveryService;
import io.github.youpl.diglikesteve.application.service.RegistrationService;
import io.github.youpl.diglikesteve.application.service.RewardService;
import io.github.youpl.diglikesteve.application.service.ScoringService;
import io.github.youpl.diglikesteve.application.service.SessionLifecycleService;
import io.github.youpl.diglikesteve.application.service.TntAccountingService;
import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.platform.paper.DlsKeys;
import io.github.youpl.diglikesteve.platform.paper.audience.MessageCatalog;
import io.github.youpl.diglikesteve.platform.paper.inventory.KitFactory;
import io.github.youpl.diglikesteve.platform.paper.player.PaperPlayerAdapter;

import java.util.Objects;

/**
 * Assemblage des services, injecté aux adaptateurs entrants (listeners, commandes, GUI).
 *
 * <p>Ce conteneur ne porte aucune règle : il ne fait que rendre les services disponibles, ce qui
 * évite un objet d'orchestration monolithique (DAT §4.3).</p>
 */
public final class DlsServices {

    private final SessionLifecycleService lifecycle;
    private final RegistrationService registration;
    private final ScoringService scoring;
    private final TntAccountingService tnt;
    private final RewardService rewards;
    private final RecoveryService recovery;
    private final InventoryCustodyService custody;
    private final NotificationPort notifications;
    private final PaperPlayerAdapter players;
    private final SchedulerPort schedulers;
    private final SnapshotRepository snapshots;
    private final StatsRepository stats;
    private final KitFactory kits;
    private final DlsKeys keys;
    private final MessageCatalog messages;
    private final Clock clock;
    private final LogPort log;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public DlsServices(
            SessionLifecycleService lifecycle,
            RegistrationService registration,
            ScoringService scoring,
            TntAccountingService tnt,
            RewardService rewards,
            RecoveryService recovery,
            InventoryCustodyService custody,
            NotificationPort notifications,
            PaperPlayerAdapter players,
            SchedulerPort schedulers,
            SnapshotRepository snapshots,
            StatsRepository stats,
            KitFactory kits,
            DlsKeys keys,
            MessageCatalog messages,
            Clock clock,
            LogPort log) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.registration = Objects.requireNonNull(registration, "registration");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.tnt = Objects.requireNonNull(tnt, "tnt");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.custody = Objects.requireNonNull(custody, "custody");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.players = Objects.requireNonNull(players, "players");
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.kits = Objects.requireNonNull(kits, "kits");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    public SessionLifecycleService lifecycle() {
        return lifecycle;
    }

    public RegistrationService registration() {
        return registration;
    }

    public ScoringService scoring() {
        return scoring;
    }

    public TntAccountingService tnt() {
        return tnt;
    }

    public RewardService rewards() {
        return rewards;
    }

    public RecoveryService recovery() {
        return recovery;
    }

    public InventoryCustodyService custody() {
        return custody;
    }

    public NotificationPort notifications() {
        return notifications;
    }

    public PaperPlayerAdapter players() {
        return players;
    }

    public SchedulerPort schedulers() {
        return schedulers;
    }

    public SnapshotRepository snapshots() {
        return snapshots;
    }

    public StatsRepository stats() {
        return stats;
    }

    public KitFactory kits() {
        return kits;
    }

    public DlsKeys keys() {
        return keys;
    }

    public MessageCatalog messages() {
        return messages;
    }

    public Clock clock() {
        return clock;
    }

    public LogPort log() {
        return log;
    }
}
