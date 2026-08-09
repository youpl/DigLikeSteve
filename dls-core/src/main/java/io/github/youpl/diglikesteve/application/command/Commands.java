package io.github.youpl.diglikesteve.application.command;

import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Commandes immuables produites par les adaptateurs entrants (DAT §4.3, Observer).
 *
 * <p>Un listener Paper valide le contexte Minecraft, construit l'une de ces commandes et
 * délègue. Aucune règle de score ne vit dans un listener.</p>
 */
public final class Commands {

    private Commands() {
    }

    /** Demande d'inscription, issue de {@code /dls join} ou du pont de warp. */
    public record Join(PlayerId playerId) {
        public Join {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    /** Départ volontaire. */
    public record Leave(PlayerId playerId) {
        public Leave {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    /** Casse manuelle d'un bloc, déjà filtrée par le listener. */
    public record ConsumeBlock(PlayerId playerId, int x, int y, int z) {
        public ConsumeBlock {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    /** TNT amorcée par un joueur avec le briquet DLS. */
    public record TntPrimed(UUID tntId, PlayerId owner) {
        public TntPrimed {
            Objects.requireNonNull(tntId, "tntId");
            Objects.requireNonNull(owner, "owner");
        }
    }

    /** Réaction en chaîne : l'identité est propagée depuis la TNT source. */
    public record TntChained(UUID sourceTntId, UUID chainedTntId) {
        public TntChained {
            Objects.requireNonNull(sourceTntId, "sourceTntId");
            Objects.requireNonNull(chainedTntId, "chainedTntId");
        }
    }

    /** Liste des positions qu'une explosion va détruire. */
    public record ExplosionBlocks(UUID tntId, List<BlockPos> destroyed) {
        public ExplosionBlocks {
            Objects.requireNonNull(tntId, "tntId");
            destroyed = List.copyOf(Objects.requireNonNull(destroyed, "destroyed"));
        }
    }

    /** Dégâts finaux infligés par une TNT suivie à son propre propriétaire. */
    public record OwnerDamaged(UUID tntId, PlayerId victim, double finalDamage) {
        public OwnerDamaged {
            Objects.requireNonNull(tntId, "tntId");
            Objects.requireNonNull(victim, "victim");
        }
    }

    public record PlayerDisconnected(PlayerId playerId) {
        public PlayerDisconnected {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    public record PlayerReconnected(PlayerId playerId) {
        public PlayerReconnected {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    /** Résultat normalisé d'un cas d'usage, destiné au message rendu au joueur. */
    public record Outcome(boolean success, String messageKey, Object[] placeholders) {

        public static Outcome ok(String messageKey, Object... placeholders) {
            return new Outcome(true, messageKey, placeholders);
        }

        public static Outcome failure(String messageKey, Object... placeholders) {
            return new Outcome(false, messageKey, placeholders);
        }
    }

    /** Position simple utilisée par les commandes d'administration. */
    public record Corner(PlayerId playerId, String worldName, BlockPos position) {
        public Corner {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(worldName, "worldName");
            Objects.requireNonNull(position, "position");
        }
    }
}
