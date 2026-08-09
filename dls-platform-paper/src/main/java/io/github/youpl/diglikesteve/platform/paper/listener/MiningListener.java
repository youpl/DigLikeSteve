package io.github.youpl.diglikesteve.platform.paper.listener;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Casse manuelle d'un bloc (DAT §7.1).
 *
 * <p>Ce listener est le chemin le plus chaud du plugin. Il ne fait que valider le contexte
 * Minecraft, désactiver drops et expérience, puis déléguer une commande immuable au cas
 * d'usage. Aucune règle de score n'y figure.</p>
 */
public final class MiningListener implements Listener {

    private final DlsServices services;

    public MiningListener(DlsServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /**
     * Priorité haute mais non {@code MONITOR} : une protection externe qui a déjà annulé
     * l'événement est respectée (DAT §20).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Optional<GameSession> maybeSession = services.lifecycle().session();
        if (maybeSession.isEmpty()) {
            return;
        }
        GameSession session = maybeSession.get();
        Block block = event.getBlock();
        ArenaBounds bounds = session.arena().map(arena -> arena.bounds()).orElse(null);
        if (bounds == null || !bounds.worldName().equals(block.getWorld().getName())
                || !bounds.contains(block.getX(), block.getY(), block.getZ())) {
            return;
        }

        PlayerId playerId = PlayerId.of(event.getPlayer().getUniqueId());
        if (session.participant(playerId).isEmpty()) {
            // Un non-participant ne casse rien dans l'arène.
            event.setCancelled(true);
            return;
        }
        if (!session.phase().acceptsScoring()) {
            // Pendant l'inscription les joueurs sont isolés, sans pouvoir miner.
            event.setCancelled(true);
            return;
        }

        // Aucun bloc ne produit de drop ni d'expérience pendant la partie.
        event.setDropItems(false);
        event.setExpToDrop(0);

        long score = services.scoring().consume(session,
                new Commands.ConsumeBlock(playerId, block.getX(), block.getY(), block.getZ()));
        if (score < 0L) {
            services.log().debug("Score négatif inattendu", "player", playerId);
        }
    }
}
