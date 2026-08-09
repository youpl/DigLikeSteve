package io.github.youpl.diglikesteve.application.service;

import io.github.youpl.diglikesteve.core.game.SessionPhase;
import io.github.youpl.diglikesteve.core.player.ParticipantStatus;

/**
 * Politiques de restriction (DAT §9).
 *
 * <p>Chaque règle est une fonction pure et courte, testable sans serveur : les listeners ne font
 * que fournir le contexte Minecraft et appliquer la décision. Il n'existe pas de listener
 * universel qui déciderait de tout.</p>
 */
public final class RestrictionPolicy {

    private RestrictionPolicy() {
    }

    /** Un participant ne pose jamais de bloc : un bloc posé ne pourrait de toute façon rien rapporter. */
    public static boolean blocksPlacement(SessionPhase phase, boolean participant) {
        return participant && phase.isActive();
    }

    /** Aucun item ne peut être jeté ni ramassé pendant la partie. */
    public static boolean blocksItemTransfer(SessionPhase phase, boolean participant) {
        return participant && phase.isActive();
    }

    /** Les conteneurs et le craft sont fermés aux participants. */
    public static boolean blocksContainerAccess(SessionPhase phase, boolean participant) {
        return participant && phase.isActive();
    }

    /** Le kit ne peut pas quitter les slots autorisés. */
    public static boolean blocksKitMove(boolean participant, boolean kitItem) {
        return participant && kitItem;
    }

    /** Pendant l'inscription, les joueurs sont isolés dans leur slot et ne peuvent pas miner. */
    public static boolean blocksMining(SessionPhase phase, ParticipantStatus status) {
        return !phase.acceptsScoring() || !status.canScore();
    }

    /** Portails et changements de monde sont interdits tant que la partie n'est pas rendue. */
    public static boolean blocksWorldChange(SessionPhase phase, boolean participant) {
        return participant && phase.isActive();
    }

    /**
     * Téléportations : seules celles provoquées par le plugin lui-même sont acceptées.
     *
     * @param pluginCause vrai si la cause de téléportation est {@code PLUGIN}
     */
    public static boolean blocksTeleport(SessionPhase phase, boolean participant, boolean pluginCause) {
        return participant && phase.isActive() && !pluginCause;
    }

    /** Un liquide ou un piston ne doit pas franchir la frontière de l'arène. */
    public static boolean blocksBoundaryCrossing(boolean fromInside, boolean toInside) {
        return fromInside != toInside;
    }

    /** Hors arène, un participant ne casse rien. */
    public static boolean blocksOutsideDamage(SessionPhase phase, boolean participant, boolean insideArena) {
        return participant && phase.isActive() && !insideArena;
    }
}
