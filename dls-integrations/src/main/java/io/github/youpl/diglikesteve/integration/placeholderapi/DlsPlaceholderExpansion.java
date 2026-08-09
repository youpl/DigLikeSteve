package io.github.youpl.diglikesteve.integration.placeholderapi;

import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.game.SessionPhase;
import io.github.youpl.diglikesteve.core.score.Leaderboard;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Placeholders exposés à PlaceholderAPI (DAT §13.4).
 *
 * <p>L'intégration est facultative : elle n'est enregistrée que si PlaceholderAPI est détecté,
 * et son absence n'a aucun effet sur la logique de jeu.</p>
 */
public final class DlsPlaceholderExpansion extends PlaceholderExpansion {

    private final DlsServices services;
    private final String version;

    public DlsPlaceholderExpansion(DlsServices services, String version) {
        this.services = Objects.requireNonNull(services, "services");
        this.version = Objects.requireNonNull(version, "version");
    }

    @Override
    public String getIdentifier() {
        return "dls";
    }

    @Override
    public String getAuthor() {
        return "youpl";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        // L'expansion survit aux rechargements de PlaceholderAPI.
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        Optional<GameSession> session = services.lifecycle().session();

        return switch (key) {
            case "phase" -> session.map(s -> s.phase().name()).orElse(SessionPhase.IDLE.name());
            case "time_left" -> String.valueOf(remainingSeconds(session.orElse(null)));
            case "players" -> String.valueOf(session.map(GameSession::activeCount).orElse(0));
            case "score" -> String.valueOf(scoreOf(session.orElse(null), player));
            case "rank" -> String.valueOf(rankOf(session.orElse(null), player));
            case "wins" -> player == null ? "0"
                    : String.valueOf(services.stats().wins(PlayerId.of(player.getUniqueId())));
            case "best_score" -> player == null ? "0"
                    : String.valueOf(services.stats().bestScore(PlayerId.of(player.getUniqueId())));
            default -> null;
        };
    }

    private long remainingSeconds(GameSession session) {
        if (session == null) {
            return 0L;
        }
        return switch (session.phase()) {
            case REGISTRATION -> session.registrationDeadline().remainingSeconds(services.clock());
            case RUNNING -> session.gameDeadline().remainingSeconds(services.clock());
            default -> 0L;
        };
    }

    private long scoreOf(GameSession session, OfflinePlayer player) {
        if (session == null || player == null) {
            return 0L;
        }
        return session.participant(PlayerId.of(player.getUniqueId()))
                .map(participant -> participant.score())
                .orElse(0L);
    }

    private int rankOf(GameSession session, OfflinePlayer player) {
        if (session == null || player == null) {
            return -1;
        }
        Leaderboard leaderboard = services.scoring().leaderboard(session);
        return leaderboard.rankOf(PlayerId.of(player.getUniqueId()));
    }
}
