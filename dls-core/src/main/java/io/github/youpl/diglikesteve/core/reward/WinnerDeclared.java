package io.github.youpl.diglikesteve.core.reward;

import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.util.List;
import java.util.Objects;

/** Événement de domaine émis une seule fois par session (DAT §12). */
public record WinnerDeclared(SessionId sessionId, List<PlayerId> winners, long topScore, long declaredAtEpochMillis) {

    public WinnerDeclared {
        Objects.requireNonNull(sessionId, "sessionId");
        winners = List.copyOf(Objects.requireNonNull(winners, "winners"));
    }

    public boolean hasWinners() {
        return !winners.isEmpty();
    }
}
