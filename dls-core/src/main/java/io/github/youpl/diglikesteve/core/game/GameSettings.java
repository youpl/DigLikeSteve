package io.github.youpl.diglikesteve.core.game;

import java.util.Objects;

/**
 * Réglages immuables d'une partie (DAT §17). L'objet entier est remplacé atomiquement après
 * validation ; il n'est jamais muté en place.
 *
 * @param registrationSeconds   durée d'inscription (60 par défaut)
 * @param gameSeconds           durée de jeu (600 par défaut)
 * @param reconnectGraceSeconds délai de grâce après déconnexion (30 par défaut)
 * @param tieStrategy           départage des ex æquo
 * @param maxParticipants       nombre de poches de spawn réservées
 * @param pvpEnabled            PvP pendant la partie
 * @param cancelEnvironmentalDamage annulation des dégâts non-TNT
 * @param tntDamageLethal       la TNT peut-elle tuer le joueur
 * @param penaltyRatePerHalfHeart part du gain brut retirée par demi-cœur reçu (0.05)
 * @param capPenaltyToGrossPoints la pénalité ne peut pas dépasser le gain de la TNT concernée
 */
public record GameSettings(
        long registrationSeconds,
        long gameSeconds,
        long reconnectGraceSeconds,
        TieStrategy tieStrategy,
        int maxParticipants,
        boolean pvpEnabled,
        boolean cancelEnvironmentalDamage,
        boolean tntDamageLethal,
        double penaltyRatePerHalfHeart,
        boolean capPenaltyToGrossPoints) {

    public GameSettings {
        Objects.requireNonNull(tieStrategy, "tieStrategy");
        if (registrationSeconds <= 0L) {
            throw new IllegalArgumentException("La durée d'inscription doit être strictement positive");
        }
        if (gameSeconds <= 0L) {
            throw new IllegalArgumentException("La durée de partie doit être strictement positive");
        }
        if (reconnectGraceSeconds < 0L) {
            throw new IllegalArgumentException("Le délai de grâce ne peut pas être négatif");
        }
        if (maxParticipants <= 0) {
            throw new IllegalArgumentException("maxParticipants doit être positif");
        }
        if (penaltyRatePerHalfHeart < 0.0d || penaltyRatePerHalfHeart > 1.0d) {
            throw new IllegalArgumentException("Le taux de pénalité doit rester entre 0 et 1");
        }
    }

    /** Valeurs recommandées par le DAT (§2 et §5.2). */
    public static GameSettings defaults() {
        return new GameSettings(60L, 600L, 30L, TieStrategy.SHARED, 32, false, true, false, 0.05d, true);
    }

    public GameSettings withDurations(long registration, long game) {
        return new GameSettings(registration, game, reconnectGraceSeconds, tieStrategy, maxParticipants,
                pvpEnabled, cancelEnvironmentalDamage, tntDamageLethal, penaltyRatePerHalfHeart,
                capPenaltyToGrossPoints);
    }
}
