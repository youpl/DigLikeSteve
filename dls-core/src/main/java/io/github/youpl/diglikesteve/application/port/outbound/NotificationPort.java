package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.score.Leaderboard;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.List;

/**
 * Sorties d'affichage (DAT §14). Adventure natif suffit : message cliquable, boss bar, action
 * bar et titres. Aucune couche paquet n'est utilisée.
 *
 * <p>Le domaine appelle des méthodes sémantiques ; le choix des textes appartient à
 * {@code messages.yml} et à l'adaptateur.</p>
 */
public interface NotificationPort {

    /** Annonce cliquable ouvrant l'inscription, avec {@code ClickEvent.runCommand("/dls join")}. */
    void announceRegistrationOpen(long registrationSeconds);

    void announceGameStarted(long gameSeconds);

    void announceGameFinished(Leaderboard leaderboard, List<PlayerId> winners);

    void announceCancelled(String reason);

    /** Message privé à un joueur, résolu depuis {@code messages.yml}. */
    void message(PlayerId playerId, String messageKey, Object... placeholders);

    /** Message à tous les joueurs du serveur. */
    void broadcast(String messageKey, Object... placeholders);

    /** Rafraîchit boss bar et action bar. Au plus une fois par seconde (DAT §10.4). */
    void refreshHud(Leaderboard leaderboard, long remainingSeconds);

    void showBossBar(PlayerId playerId);

    void hideBossBar(PlayerId playerId);

    /** Retire explicitement tous les viewers : aucune audience ne doit survivre au nettoyage. */
    void clearAllAudiences();
}
