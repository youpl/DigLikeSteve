package io.github.youpl.diglikesteve.application.service;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.PlayerPort;
import io.github.youpl.diglikesteve.application.port.outbound.RewardOutboxRepository;
import io.github.youpl.diglikesteve.application.port.outbound.RewardProviderPort;
import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.core.reward.RewardDeliveryState;
import io.github.youpl.diglikesteve.core.reward.RewardOutboxEntry;
import io.github.youpl.diglikesteve.core.reward.WinnerDeclared;
import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Remise des récompenses par outbox (DAT §12).
 *
 * <p>La ligne d'outbox est écrite <em>avant</em> tout appel au fournisseur, dans la même
 * transaction que l'incrément du compteur de victoires. Un incident entre l'écriture et la
 * remise laisse donc une entrée rejouable plutôt qu'une récompense perdue.</p>
 *
 * <p>Un disjoncteur simple désactive localement un fournisseur qui échoue en série, sans casser
 * la partie (DAT §4.3).</p>
 */
public final class RewardService {

    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;

    private final RewardOutboxRepository outbox;
    private final RewardProviderPort provider;
    private final PlayerPort players;
    private final SchedulerPort schedulers;
    private final Clock clock;
    private final LogPort log;
    private final boolean trackWins;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    public RewardService(
            RewardOutboxRepository outbox,
            RewardProviderPort provider,
            PlayerPort players,
            SchedulerPort schedulers,
            Clock clock,
            LogPort log,
            boolean trackWins) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.players = Objects.requireNonNull(players, "players");
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
        this.trackWins = trackWins;
    }

    public String providerName() {
        return provider.name();
    }

    public RewardProviderPort.DeliverySemantics deliverySemantics() {
        return provider.deliverySemantics();
    }

    public boolean isCircuitOpen() {
        return consecutiveFailures.get() >= CIRCUIT_BREAKER_THRESHOLD;
    }

    /** Écrit une ligne d'outbox par gagnant. La clé d'idempotence interdit les doublons. */
    public CompletableFuture<Integer> declare(WinnerDeclared event, String payload) {
        CompletableFuture<Integer> created = new CompletableFuture<>();
        schedulers.database(() -> {
            int inserted = 0;
            long now = clock.epochMillis();
            for (PlayerId winner : event.winners()) {
                RewardOutboxEntry entry = new RewardOutboxEntry(
                        UUID.randomUUID(),
                        event.sessionId(),
                        winner,
                        RewardOutboxEntry.idempotencyKeyFor(event.sessionId(), winner),
                        provider.name(),
                        payload,
                        RewardDeliveryState.PENDING,
                        0,
                        now,
                        now,
                        null);
                if (outbox.insertIfAbsent(entry, trackWins)) {
                    inserted++;
                }
            }
            log.info("Récompenses créées", "session", event.sessionId(), "winners", event.winners().size(),
                    "inserted", inserted);
            created.complete(inserted);
        }).exceptionally(failure -> {
            log.error("Échec de création des récompenses", failure, "session", event.sessionId());
            created.complete(0);
            return null;
        });
        return created;
    }

    /** Tente la remise des entrées en attente. Sûr à rejouer. */
    public CompletableFuture<Integer> deliverPending(int limit) {
        if (isCircuitOpen()) {
            log.warn("Disjoncteur ouvert : remise suspendue", "provider", provider.name());
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Integer> done = new CompletableFuture<>();
        schedulers.database(() -> {
            List<RewardOutboxEntry> pending = outbox.findByState(RewardDeliveryState.PENDING, limit);
            List<RewardOutboxEntry> mailbox = outbox.findByState(RewardDeliveryState.MAILBOX, limit);
            schedulers.global(() -> {
                int attempted = 0;
                for (RewardOutboxEntry entry : pending) {
                    deliver(entry);
                    attempted++;
                }
                for (RewardOutboxEntry entry : mailbox) {
                    if (players.isOnline(entry.playerId())) {
                        deliver(entry);
                        attempted++;
                    }
                }
                done.complete(attempted);
            });
        }).exceptionally(failure -> {
            done.completeExceptionally(failure);
            return null;
        });
        return done;
    }

    /** Remet une entrée précise. Le résultat est persisté quelle que soit l'issue. */
    public CompletableFuture<Boolean> deliver(RewardOutboxEntry entry) {
        if (provider.requiresOnlinePlayer() && !players.isOnline(entry.playerId())) {
            updateState(entry, RewardDeliveryState.MAILBOX, "Gagnant hors ligne");
            return CompletableFuture.completedFuture(false);
        }
        return provider.deliver(entry)
                .handle((ignored, failure) -> {
                    if (failure != null) {
                        consecutiveFailures.incrementAndGet();
                        updateState(entry, RewardDeliveryState.FAILED, String.valueOf(failure.getMessage()));
                        log.error("Remise de récompense échouée", failure,
                                "entry", entry.id(), "player", entry.playerId());
                        return false;
                    }
                    consecutiveFailures.set(0);
                    updateState(entry, RewardDeliveryState.DELIVERED, null);
                    return true;
                });
    }

    /** Rejoue une entrée échouée à la demande d'un administrateur. */
    public CompletableFuture<Boolean> retry(UUID entryId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        schedulers.database(() -> outbox.findById(entryId).ifPresentOrElse(
                entry -> {
                    consecutiveFailures.set(0);
                    schedulers.global(() -> deliver(entry).thenAccept(result::complete));
                },
                () -> result.complete(false)));
        return result;
    }

    /** Marque une entrée comme remise, sans appeler le fournisseur. */
    public CompletableFuture<Boolean> markDelivered(UUID entryId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        schedulers.database(() -> outbox.findById(entryId).ifPresentOrElse(
                entry -> {
                    outbox.updateState(entry.id(), RewardDeliveryState.DELIVERED, entry.attempts(),
                            "Marquée manuellement", clock.epochMillis());
                    result.complete(true);
                },
                () -> result.complete(false)));
        return result;
    }

    /** Remet les récompenses en boîte aux lettres à la reconnexion du gagnant. */
    public void onPlayerJoined(PlayerId playerId) {
        schedulers.database(() -> {
            List<RewardOutboxEntry> waiting = outbox.findPendingForPlayer(playerId);
            if (waiting.isEmpty()) {
                return;
            }
            schedulers.global(() -> waiting.forEach(this::deliver));
        });
    }

    public CompletableFuture<List<RewardOutboxEntry>> pending(int limit) {
        CompletableFuture<List<RewardOutboxEntry>> result = new CompletableFuture<>();
        schedulers.database(() -> {
            List<RewardOutboxEntry> entries = outbox.findByState(RewardDeliveryState.PENDING, limit);
            List<RewardOutboxEntry> failed = outbox.findByState(RewardDeliveryState.FAILED, limit);
            List<RewardOutboxEntry> mailbox = outbox.findByState(RewardDeliveryState.MAILBOX, limit);
            result.complete(java.util.stream.Stream.of(entries, failed, mailbox)
                    .flatMap(List::stream)
                    .toList());
        });
        return result;
    }

    private void updateState(RewardOutboxEntry entry, RewardDeliveryState state, String error) {
        schedulers.database(() -> outbox.updateState(entry.id(), state, entry.attempts() + 1, error,
                clock.epochMillis()));
    }
}
