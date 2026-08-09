package io.github.youpl.diglikesteve.application.service;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.NotificationPort;
import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.core.arena.Arena;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.tnt.ExplosionAccount;
import io.github.youpl.diglikesteve.core.tnt.TntOwnershipRegistry;
import io.github.youpl.diglikesteve.core.tnt.TntSettlement;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Comptabilité des explosions (DAT §7.2 et §7.3).
 *
 * <p>L'événement de blocs et l'événement de dégâts n'ont pas d'ordre garanti : le règlement est
 * donc différé de quelques ticks, le temps que les deux soient arrivés. Il reste idempotent —
 * un {@link ExplosionAccount} ne peut être crédité qu'une fois.</p>
 */
public final class TntAccountingService {

    /** Délai de regroupement, en ticks, entre l'explosion et son règlement. */
    private static final long SETTLEMENT_DELAY_TICKS = 2L;

    private final TntOwnershipRegistry ownership;
    private final ScoringService scoring;
    private final SchedulerPort schedulers;
    private final NotificationPort notifications;
    private final Clock clock;
    private final LogPort log;

    private final ConcurrentHashMap<UUID, ExplosionAccount> accounts = new ConcurrentHashMap<>();
    private final long accountTtlNanos;

    public TntAccountingService(
            TntOwnershipRegistry ownership,
            ScoringService scoring,
            SchedulerPort schedulers,
            NotificationPort notifications,
            Clock clock,
            LogPort log,
            long accountTtlSeconds) {
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
        this.accountTtlNanos = TimeUnit.SECONDS.toNanos(accountTtlSeconds);
    }

    /** Une TNT vient d'être amorcée par un joueur avec le briquet DLS. */
    public void onPrimed(Commands.TntPrimed command) {
        ownership.register(command.tntId(), command.owner());
    }

    /** Réaction en chaîne : la TNT source transmet son propriétaire. */
    public void onChained(Commands.TntChained command) {
        ownership.propagate(command.sourceTntId(), command.chainedTntId());
    }

    /**
     * Comptabilise les blocs détruits par une explosion.
     *
     * <p>Seules les positions situées dans l'arène et qui remportent leur bit de consommation
     * sont additionnées. La TNT elle-même vaut zéro : elle ne crédite que ce qu'elle détruit.</p>
     *
     * @return le compte ouvert, ou vide si l'explosion n'appartient à personne
     */
    public Optional<ExplosionAccount> onExplosion(GameSession session, Commands.ExplosionBlocks command) {
        if (!session.phase().acceptsScoring()) {
            return Optional.empty();
        }
        PlayerId owner = ownership.lookup(command.tntId()).orElse(null);
        if (owner == null) {
            return Optional.empty();
        }
        Arena arena = session.arena().orElse(null);
        if (arena == null) {
            return Optional.empty();
        }

        ExplosionAccount account = accounts.computeIfAbsent(command.tntId(),
                id -> new ExplosionAccount(id, owner, clock.nanoTime()));

        long gross = 0L;
        for (BlockPos pos : command.destroyed()) {
            gross += arena.consumeForPoints(pos);
        }
        account.addBlockPoints(gross);

        scheduleSettlement(session, command.tntId());
        return Optional.of(account);
    }

    /**
     * Enregistre les dégâts finaux subis par le propriétaire de la TNT.
     *
     * <p>Un adversaire touché par la TNT d'un autre joueur ne déclenche rien : la pénalité ne
     * concerne que le propriétaire, ce qui empêche de faire perdre des points à une victime.</p>
     */
    public void onOwnerDamaged(Commands.OwnerDamaged command) {
        ExplosionAccount account = accounts.get(command.tntId());
        if (account == null) {
            return;
        }
        if (!account.owner().equals(command.victim())) {
            return;
        }
        account.addOwnerDamage(command.finalDamage());
    }

    private void scheduleSettlement(GameSession session, UUID tntId) {
        schedulers.globalDelayed(() -> settle(session, tntId), SETTLEMENT_DELAY_TICKS);
    }

    /** Règlement unique d'une explosion. Les appels suivants n'ont aucun effet. */
    public Optional<TntSettlement> settle(GameSession session, UUID tntId) {
        ExplosionAccount account = accounts.get(tntId);
        if (account == null) {
            return Optional.empty();
        }
        Optional<TntSettlement> settlement = account.settle(
                session.settings().penaltyRatePerHalfHeart(),
                session.settings().capPenaltyToGrossPoints());
        if (settlement.isEmpty()) {
            return Optional.empty();
        }

        TntSettlement result = settlement.get();
        accounts.remove(tntId, account);
        ownership.forget(tntId);

        if (result.netPoints() != 0L) {
            scoring.creditExplosion(session, account.owner(), result.netPoints());
        }
        if (result.penalty() > 0L) {
            notifications.message(account.owner(), "tnt.penalty",
                    "gross", result.grossPoints(),
                    "penalty", result.penalty(),
                    "net", result.netPoints(),
                    "hearts", result.halfHearts());
        } else if (result.grossPoints() > 0L) {
            notifications.message(account.owner(), "tnt.gain", "gross", result.grossPoints());
        }
        log.debug("Explosion réglée", "tnt", tntId, "owner", account.owner(),
                "gross", result.grossPoints(), "net", result.netPoints());
        return settlement;
    }

    /** Règle tous les comptes en attente, appelé en fin de partie et au nettoyage. */
    public int settleAll(GameSession session) {
        int settled = 0;
        for (UUID tntId : accounts.keySet()) {
            if (settle(session, tntId).isPresent()) {
                settled++;
            }
        }
        return settled;
    }

    /** Purge les comptes orphelins et les propriétés expirées (DAT §15). */
    public int purgeExpired() {
        long now = clock.nanoTime();
        int removed = 0;
        Iterator<Map.Entry<UUID, ExplosionAccount>> iterator = accounts.entrySet().iterator();
        while (iterator.hasNext()) {
            ExplosionAccount account = iterator.next().getValue();
            if (now - account.createdAtNanos() >= accountTtlNanos) {
                iterator.remove();
                removed++;
            }
        }
        removed += ownership.purgeExpired();
        return removed;
    }

    public int pendingAccounts() {
        return accounts.size();
    }

    public int trackedTnt() {
        return ownership.size();
    }

    public void clear() {
        accounts.clear();
        ownership.clear();
    }
}
