package io.github.youpl.diglikesteve.application.service;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.core.arena.Arena;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.player.Participant;
import io.github.youpl.diglikesteve.core.score.Leaderboard;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Attribution des points (DAT §7.1).
 *
 * <p>Chemin le plus chaud du plugin : aucun accès configuration, aucune allocation superflue,
 * aucune écriture SQL et aucun tri global. La valeur vient du plan de génération, jamais d'un
 * nom d'item ou d'un texte.</p>
 */
public final class ScoringService {

    private final AtomicLong blocksScored = new AtomicLong();

    /**
     * @return les points crédités, ou {@code 0} si la casse ne rapporte rien
     */
    public long consume(GameSession session, Commands.ConsumeBlock command) {
        Objects.requireNonNull(session, "session");
        if (!session.phase().acceptsScoring()) {
            return 0L;
        }
        Arena arena = session.arena().orElse(null);
        if (arena == null) {
            return 0L;
        }
        Participant participant = session.participant(command.playerId()).orElse(null);
        if (participant == null || !participant.status().canScore()) {
            return 0L;
        }
        int points = arena.consumeForPoints(command.x(), command.y(), command.z());
        if (points <= 0) {
            return 0L;
        }
        blocksScored.incrementAndGet();
        return participant.addScore(points, session.clock());
    }

    /**
     * Crédite le gain net d'une explosion à son propriétaire.
     *
     * @return le score après application, ou vide si le joueur n'est plus créditable
     */
    public Optional<Long> creditExplosion(GameSession session, PlayerId owner, long netPoints) {
        if (netPoints == 0L) {
            return Optional.empty();
        }
        Participant participant = session.participant(owner).orElse(null);
        if (participant == null || !participant.status().isRanked()) {
            return Optional.empty();
        }
        return Optional.of(participant.addScore(netPoints, session.clock()));
    }

    /** Classement ponctuel. À appeler sur l'exécuteur CPU ou pour l'affichage périodique. */
    public Leaderboard leaderboard(GameSession session) {
        return Leaderboard.of(session.participants());
    }

    public long blocksScored() {
        return blocksScored.get();
    }

    public void reset() {
        blocksScored.set(0L);
    }
}
