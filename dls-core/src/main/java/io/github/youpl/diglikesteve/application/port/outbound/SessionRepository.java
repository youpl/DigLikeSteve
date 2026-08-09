package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.game.SessionPhase;
import io.github.youpl.diglikesteve.core.player.ParticipantStatus;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

/** Métadonnées durables de session et de participants (DAT §11). */
public interface SessionRepository {

    void createSession(SessionId sessionId, long seed, String worldName, String boundsDescription, long createdAtEpochMillis);

    void updatePhase(SessionId sessionId, SessionPhase phase, long nowEpochMillis);

    void closeSession(SessionId sessionId, long endedAtEpochMillis);

    void upsertParticipant(SessionId sessionId, PlayerId playerId, ParticipantStatus status, long score, int rank, long nowEpochMillis);

    /** Marque toute session restée ouverte comme annulée : aucune reprise n'est tentée (DAT §2). */
    int abortOpenSessions(long nowEpochMillis);
}
