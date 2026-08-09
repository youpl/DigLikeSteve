package io.github.youpl.diglikesteve.platform.paper.command;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.reward.RewardOutboxEntry;
import io.github.youpl.diglikesteve.core.score.Leaderboard;
import io.github.youpl.diglikesteve.core.score.ScoreEntry;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.platform.paper.FoliaSupport;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import io.github.youpl.diglikesteve.platform.paper.config.ConfigurationLoader;
import io.github.youpl.diglikesteve.platform.paper.gui.AdminMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Commandes de Dig Like Steve (DAT §18).
 *
 * <p>Les commandes d'administration vérifient la phase courante et exigent une confirmation
 * explicite pour toute préparation qui écrasera la zone.</p>
 */
public final class DlsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of("join", "leave", "top", "status", "admin");
    private static final List<String> ADMIN = List.of("menu", "pos1", "pos2", "selection", "prepare",
            "start", "stop", "reload", "recovery", "rewards", "debug");

    private final DlsServices services;
    private final ConfigurationLoader configuration;
    private final AdminMenu adminMenu;
    private final SelectionDraft selection;

    public DlsCommand(DlsServices services, ConfigurationLoader configuration, AdminMenu adminMenu,
                      SelectionDraft selection) {
        this.services = Objects.requireNonNull(services, "services");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.adminMenu = Objects.requireNonNull(adminMenu, "adminMenu");
        this.selection = Objects.requireNonNull(selection, "selection");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "command.usage");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> join(sender);
            case "leave" -> leave(sender);
            case "top" -> top(sender);
            case "status" -> status(sender);
            case "admin" -> admin(sender, args);
            default -> {
                send(sender, "command.usage");
                yield true;
            }
        };
    }

    private boolean join(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.players-only");
            return true;
        }
        if (!player.hasPermission("dls.join")) {
            send(sender, "command.no-permission");
            return true;
        }
        GameSession session = services.lifecycle().session().orElse(null);
        if (session == null) {
            send(sender, "join.closed");
            return true;
        }
        Commands.Outcome outcome = services.registration()
                .join(session, new Commands.Join(PlayerId.of(player.getUniqueId())));
        send(sender, outcome.messageKey(), outcome.placeholders());
        return true;
    }

    private boolean leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.players-only");
            return true;
        }
        GameSession session = services.lifecycle().session().orElse(null);
        if (session == null) {
            send(sender, "leave.not-registered");
            return true;
        }
        Commands.Outcome outcome = services.registration()
                .leave(session, new Commands.Leave(PlayerId.of(player.getUniqueId())));
        send(sender, outcome.messageKey(), outcome.placeholders());
        return true;
    }

    private boolean top(CommandSender sender) {
        if (!sender.hasPermission("dls.command.top")) {
            send(sender, "command.no-permission");
            return true;
        }
        Leaderboard leaderboard = services.lifecycle().session()
                .map(session -> services.scoring().leaderboard(session))
                .orElseGet(services.lifecycle()::lastLeaderboard);

        if (leaderboard.isEmpty()) {
            send(sender, "top.empty");
            return true;
        }
        send(sender, "top.header");
        for (ScoreEntry entry : leaderboard.top(10)) {
            sender.sendMessage(services.messages().render("top.line",
                    "rank", entry.rank(),
                    "player", services.players().nameOf(entry.playerId()).orElse(entry.playerId().toString()),
                    "score", entry.score()));
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        GameSession session = services.lifecycle().session().orElse(null);
        if (session == null) {
            send(sender, "status.idle");
            return true;
        }
        long remaining = switch (session.phase()) {
            case REGISTRATION -> session.registrationDeadline().remainingSeconds(services.clock());
            case RUNNING -> session.gameDeadline().remainingSeconds(services.clock());
            default -> 0L;
        };
        sender.sendMessage(services.messages().render("status.running",
                "phase", session.phase().name(),
                "players", session.activeCount(),
                "suspended", session.suspendedCount(),
                "time", remaining,
                "progress", services.lifecycle().preparation().progressPercent()));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dls.admin")) {
            send(sender, "command.no-permission");
            return true;
        }
        if (args.length < 2) {
            send(sender, "command.admin-usage");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "menu" -> openMenu(sender);
            case "pos1" -> setCorner(sender, 1);
            case "pos2" -> setCorner(sender, 2);
            case "selection" -> importSelection(sender, args);
            case "prepare", "start" -> start(sender, args);
            case "stop" -> stop(sender);
            case "reload" -> reload(sender);
            case "recovery" -> recovery(sender, args);
            case "rewards" -> rewards(sender, args);
            case "debug" -> debug(sender);
            default -> {
                send(sender, "command.admin-usage");
                yield true;
            }
        };
    }

    private boolean openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.players-only");
            return true;
        }
        adminMenu.open(player);
        return true;
    }

    private boolean setCorner(CommandSender sender, int corner) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.players-only");
            return true;
        }
        if (!player.hasPermission("dls.admin.area")) {
            send(sender, "command.no-permission");
            return true;
        }
        BlockPos position = new BlockPos(
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ());
        selection.setCorner(PlayerId.of(player.getUniqueId()), player.getWorld().getName(), corner, position);
        sender.sendMessage(services.messages().render("admin.corner-set",
                "corner", corner, "position", position.toString()));
        selection.bounds(PlayerId.of(player.getUniqueId())).ifPresent(bounds ->
                sender.sendMessage(services.messages().render("admin.selection-ready",
                        "volume", bounds.volumeLong())));
        return true;
    }

    private boolean importSelection(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.players-only");
            return true;
        }
        if (args.length < 3 || !"import".equalsIgnoreCase(args[2])) {
            send(sender, "command.admin-usage");
            return true;
        }
        Optional<ArenaBounds> imported = selection.importExternal(PlayerId.of(player.getUniqueId()));
        if (imported.isEmpty()) {
            send(sender, "admin.selection-unavailable");
            return true;
        }
        applyBounds(sender, imported.get());
        return true;
    }

    private void applyBounds(CommandSender sender, ArenaBounds bounds) {
        if (!services.lifecycle().phase().acceptsConfigurationChange()) {
            send(sender, "admin.phase-locked");
            return;
        }
        configuration.saveArena(bounds);
        services.lifecycle().reload(configuration.reload());
        sender.sendMessage(services.messages().render("admin.arena-saved",
                "bounds", bounds.toString(), "volume", bounds.volumeLong()));
    }

    private boolean start(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dls.admin.start")) {
            send(sender, "command.no-permission");
            return true;
        }
        // La préparation écrase entièrement la zone : elle exige une confirmation explicite.
        boolean confirmed = args.length > 2 && "confirm".equalsIgnoreCase(args[args.length - 1]);
        if (!confirmed && !selection.isConfirmed(sender.getName())) {
            selection.requestConfirmation(sender.getName());
            send(sender, "admin.confirm-required");
            return true;
        }
        selection.clearConfirmation(sender.getName());

        Long registrationSeconds = parseSeconds(args, 2);
        Long gameSeconds = parseSeconds(args, 3);

        // La préparation dure plusieurs secondes : on n'annonce son démarrage que si la phase
        // courante l'autorise réellement, sinon seul le refus est rendu.
        if (services.lifecycle().phase().acceptsConfigurationChange()) {
            send(sender, "admin.preparing");
        }
        services.lifecycle().prepareAndStart(registrationSeconds, gameSeconds)
                .thenAccept(outcome -> sender.sendMessage(
                        services.messages().render(outcome.messageKey(), outcome.placeholders())));
        return true;
    }

    private Long parseSeconds(String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        try {
            long value = Long.parseLong(args[index]);
            return value > 0L ? value : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private boolean stop(CommandSender sender) {
        if (!sender.hasPermission("dls.admin.stop")) {
            send(sender, "command.no-permission");
            return true;
        }
        Commands.Outcome outcome = services.lifecycle().stop();
        send(sender, outcome.messageKey(), outcome.placeholders());
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("dls.admin.reload")) {
            send(sender, "command.no-permission");
            return true;
        }
        try {
            if (!services.lifecycle().reload(configuration.reload())) {
                send(sender, "admin.phase-locked");
                return true;
            }
            send(sender, "admin.reloaded");
        } catch (RuntimeException invalid) {
            sender.sendMessage(services.messages().render("admin.reload-failed",
                    "error", String.valueOf(invalid.getMessage())));
        }
        return true;
    }

    private boolean recovery(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dls.admin.recovery")) {
            send(sender, "command.no-permission");
            return true;
        }
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "list";
        if ("restore".equals(action)) {
            services.recovery().restoreAll().thenAccept(count ->
                    sender.sendMessage(services.messages().render("admin.recovery-restored", "count", count)));
            return true;
        }
        services.recovery().list().thenAccept(snapshots -> {
            sender.sendMessage(services.messages().render("admin.recovery-header", "count", snapshots.size()));
            snapshots.forEach(snapshot -> sender.sendMessage(services.messages().render("admin.recovery-line",
                    "player", services.players().nameOf(snapshot.playerId()).orElse(snapshot.playerId().toString()),
                    "state", snapshot.state().name(),
                    "snapshot", snapshot.id().toString())));
        });
        return true;
    }

    private boolean rewards(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dls.admin.rewards")) {
            send(sender, "command.no-permission");
            return true;
        }
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "pending";
        switch (action) {
            case "retry" -> withEntryId(sender, args, id -> services.rewards().retry(id)
                    .thenAccept(done -> sender.sendMessage(services.messages()
                            .render(done ? "admin.reward-retried" : "admin.reward-unknown"))));
            case "mark-delivered" -> withEntryId(sender, args, id -> services.rewards().markDelivered(id)
                    .thenAccept(done -> sender.sendMessage(services.messages()
                            .render(done ? "admin.reward-marked" : "admin.reward-unknown"))));
            default -> services.rewards().pending(50).thenAccept(entries -> {
                sender.sendMessage(services.messages().render("admin.rewards-header",
                        "count", entries.size(),
                        "provider", services.rewards().providerName(),
                        "semantics", services.rewards().deliverySemantics().name()));
                for (RewardOutboxEntry entry : entries) {
                    sender.sendMessage(services.messages().render("admin.rewards-line",
                            "id", entry.id().toString(),
                            "player", services.players().nameOf(entry.playerId())
                                    .orElse(entry.playerId().toString()),
                            "state", entry.state().name(),
                            "attempts", entry.attempts()));
                }
            });
        }
        return true;
    }

    private void withEntryId(CommandSender sender, String[] args, java.util.function.Consumer<UUID> action) {
        if (args.length < 4) {
            send(sender, "command.admin-usage");
            return;
        }
        try {
            action.accept(UUID.fromString(args[3]));
        } catch (IllegalArgumentException malformed) {
            send(sender, "admin.reward-unknown");
        }
    }

    private boolean debug(CommandSender sender) {
        if (!sender.hasPermission("dls.admin.debug")) {
            send(sender, "command.no-permission");
            return true;
        }
        GameSession session = services.lifecycle().session().orElse(null);
        // Le comptage des gardes est une requête SQL : elle passe par le writer dédié plutôt
        // que de bloquer le thread qui exécute la commande.
        services.schedulers().database(() -> {
            int snapshots = services.snapshots().countRequiringRecovery();
            services.schedulers().global(() -> sender.sendMessage(services.messages().render("admin.debug",
                    "platform", FoliaSupport.platformName(),
                    "phase", services.lifecycle().phase().name(),
                    "participants", session == null ? 0 : session.participants().size(),
                    "suspended", session == null ? 0 : session.suspendedCount(),
                    "snapshots", snapshots,
                    "tnt_accounts", services.tnt().pendingAccounts(),
                    "tracked_tnt", services.tnt().trackedTnt(),
                    "blocks_scored", services.scoring().blocksScored(),
                    "prepared", services.lifecycle().preparation().progressPercent())));
        });
        return true;
    }

    private void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(services.messages().render(key, placeholders));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            return filter(ADMIN, args[1]);
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0])) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "recovery" -> filter(List.of("list", "restore"), args[2]);
                case "rewards" -> filter(List.of("pending", "retry", "mark-delivered"), args[2]);
                case "selection" -> filter(List.of("import"), args[2]);
                case "start" -> filter(List.of("60"), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
