package io.github.youpl.diglikesteve.platform.paper.audience;

import io.github.youpl.diglikesteve.application.port.outbound.NotificationPort;
import io.github.youpl.diglikesteve.application.port.outbound.PlayerPort;
import io.github.youpl.diglikesteve.core.score.Leaderboard;
import io.github.youpl.diglikesteve.core.score.ScoreEntry;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Affichage Adventure (DAT §14). Une seule boss bar est partagée, ses viewers sont ajoutés et
 * retirés explicitement pour ne laisser aucune audience derrière soi (DAT §15).
 */
public final class PaperNotificationAdapter implements NotificationPort {

    private final MessageCatalog messages;
    private final PlayerPort players;
    private final AtomicReference<BossBar> bossBar = new AtomicReference<>();
    private final Set<PlayerId> viewers = ConcurrentHashMap.newKeySet();

    public PaperNotificationAdapter(MessageCatalog messages, PlayerPort players) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public void announceRegistrationOpen(long registrationSeconds) {
        Component line = messages.render("announce.registration", "seconds", registrationSeconds)
                .clickEvent(ClickEvent.runCommand("/dls join"))
                .hoverEvent(HoverEvent.showText(messages.renderBare("announce.registration-hover")));
        Bukkit.getServer().sendMessage(line);
    }

    @Override
    public void announceGameStarted(long gameSeconds) {
        Bukkit.getServer().sendMessage(messages.render("announce.started", "seconds", gameSeconds));
        Title title = Title.title(
                messages.renderBare("title.start"),
                messages.renderBare("title.start-subtitle"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500)));
        Bukkit.getOnlinePlayers().forEach(player -> player.showTitle(title));
    }

    @Override
    public void announceGameFinished(Leaderboard leaderboard, List<PlayerId> winners) {
        Bukkit.getServer().sendMessage(messages.render("announce.finished"));
        List<ScoreEntry> top = leaderboard.top(3);
        for (ScoreEntry entry : top) {
            Bukkit.getServer().sendMessage(messages.render("announce.podium",
                    "rank", entry.rank(),
                    "player", players.nameOf(entry.playerId()).orElse(entry.playerId().toString()),
                    "score", entry.score()));
        }
        if (winners.isEmpty()) {
            Bukkit.getServer().sendMessage(messages.render("announce.no-winner"));
            return;
        }
        String names = String.join(", ", winners.stream()
                .map(id -> players.nameOf(id).orElse(id.toString()))
                .toList());
        Bukkit.getServer().sendMessage(messages.render("announce.winner", "players", names));
    }

    @Override
    public void announceCancelled(String reason) {
        Bukkit.getServer().sendMessage(messages.render("announce.cancelled", "reason", reason));
    }

    @Override
    public void message(PlayerId playerId, String messageKey, Object... placeholders) {
        Player player = Bukkit.getPlayer(playerId.value());
        if (player != null) {
            player.sendMessage(messages.render(messageKey, placeholders));
        }
    }

    @Override
    public void broadcast(String messageKey, Object... placeholders) {
        Bukkit.getServer().sendMessage(messages.render(messageKey, placeholders));
    }

    @Override
    public void refreshHud(Leaderboard leaderboard, long remainingSeconds) {
        BossBar bar = bossBar.updateAndGet(existing -> existing != null ? existing
                : BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
        bar.name(messages.renderBare("hud.bossbar", "time", formatTime(remainingSeconds)));

        for (PlayerId viewer : viewers) {
            Player player = Bukkit.getPlayer(viewer.value());
            if (player == null) {
                continue;
            }
            int rank = leaderboard.rankOf(viewer);
            long score = leaderboard.entries().stream()
                    .filter(entry -> entry.playerId().equals(viewer))
                    .mapToLong(ScoreEntry::score)
                    .findFirst()
                    .orElse(0L);
            player.sendActionBar(messages.renderBare("hud.actionbar",
                    "score", score,
                    "rank", rank < 0 ? "-" : String.valueOf(rank),
                    "time", formatTime(remainingSeconds)));
        }
    }

    @Override
    public void showBossBar(PlayerId playerId) {
        Player player = Bukkit.getPlayer(playerId.value());
        if (player == null) {
            return;
        }
        BossBar bar = bossBar.updateAndGet(existing -> existing != null ? existing
                : BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
        viewers.add(playerId);
        player.showBossBar(bar);
    }

    @Override
    public void hideBossBar(PlayerId playerId) {
        viewers.remove(playerId);
        Player player = Bukkit.getPlayer(playerId.value());
        BossBar bar = bossBar.get();
        if (player != null && bar != null) {
            player.hideBossBar(bar);
        }
    }

    @Override
    public void clearAllAudiences() {
        BossBar bar = bossBar.getAndSet(null);
        if (bar != null) {
            for (PlayerId viewer : viewers) {
                Player player = Bukkit.getPlayer(viewer.value());
                if (player != null) {
                    player.hideBossBar(bar);
                }
            }
        }
        viewers.clear();
    }

    private static String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }
}
