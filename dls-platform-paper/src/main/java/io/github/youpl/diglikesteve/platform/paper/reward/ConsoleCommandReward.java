package io.github.youpl.diglikesteve.platform.paper.reward;

import io.github.youpl.diglikesteve.application.port.outbound.PlayerPort;
import io.github.youpl.diglikesteve.application.port.outbound.RewardProviderPort;
import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.core.reward.RewardOutboxEntry;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Remise par commande console (DAT §12 et §13.4).
 *
 * <p>Compatible avec la plupart des plugins de crates dès qu'ils exposent une commande
 * d'attribution. Aucune garantie « exactement une fois » n'est possible si le serveur tombe
 * entre l'exécution et l'accusé de réception : la sémantique annoncée est donc
 * <em>at-least-once</em>, et l'outbox reste rejouable manuellement.</p>
 */
public final class ConsoleCommandReward implements RewardProviderPort {

    /** Caractères refusés dans un nom substitué, pour éviter toute injection de commande. */
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9_\\-]");

    private final List<String> commands;
    private final PlayerPort players;
    private final SchedulerPort schedulers;

    public ConsoleCommandReward(List<String> commands, PlayerPort players, SchedulerPort schedulers) {
        this.commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        this.players = Objects.requireNonNull(players, "players");
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
    }

    @Override
    public String name() {
        return "console-command";
    }

    @Override
    public DeliverySemantics deliverySemantics() {
        return DeliverySemantics.AT_LEAST_ONCE;
    }

    @Override
    public boolean requiresOnlinePlayer() {
        // La plupart des plugins de crates exigent un joueur connecté.
        return true;
    }

    @Override
    public CompletableFuture<Void> deliver(RewardOutboxEntry entry) {
        String playerName = players.nameOf(entry.playerId()).orElse(null);
        if (playerName == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Nom de joueur inconnu"));
        }
        String safeName = UNSAFE.matcher(playerName).replaceAll("");
        if (safeName.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Nom de joueur non substituable"));
        }

        CompletableFuture<Void> done = new CompletableFuture<>();
        // Les commandes console s'exécutent sur la région globale.
        schedulers.global(() -> {
            try {
                for (String template : commands) {
                    String command = template
                            .replace("%player%", safeName)
                            .replace("%uuid%", entry.playerId().toString())
                            .replace("%session%", entry.sessionId().toString());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                done.complete(null);
            } catch (RuntimeException failure) {
                done.completeExceptionally(failure);
            }
        });
        return done;
    }
}
