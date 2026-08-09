package io.github.youpl.diglikesteve.platform.paper.world;

import io.github.youpl.diglikesteve.application.port.outbound.BlockBatch;
import io.github.youpl.diglikesteve.application.port.outbound.MineWriterPort;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.arena.BlockPalette;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Backend natif d'écriture de la mine (DAT §6.3).
 *
 * <p>C'est la référence fonctionnelle et le seul chemin garanti sur Folia : chaque lot est
 * exécuté sur le {@code RegionScheduler} propriétaire de son chunk, et aucun objet Bukkit ne
 * traverse une frontière de thread.</p>
 */
public final class PaperMineWriter implements MineWriterPort {

    private final Plugin plugin;
    private final AtomicReference<BlockData[]> resolvedPalette = new AtomicReference<>(new BlockData[0]);
    private final Set<Long> heldTickets = ConcurrentHashMap.newKeySet();
    private final AtomicReference<String> ticketWorld = new AtomicReference<>();

    public PaperMineWriter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void bindPalette(BlockPalette palette) {
        BlockData[] resolved = new BlockData[palette.size()];
        for (int id = 0; id < palette.size(); id++) {
            String key = palette.materialKey((byte) id);
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isBlock()) {
                throw new IllegalArgumentException("Matériau de bloc invalide dans la palette : " + key);
            }
            resolved[id] = material.createBlockData();
        }
        resolvedPalette.set(resolved);
    }

    @Override
    public CompletableFuture<Void> acquireChunk(String worldName, int chunkX, int chunkZ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Monde introuvable : " + worldName));
        }
        CompletableFuture<Void> acquired = new CompletableFuture<>();
        // Le chargement asynchrone évite tout chargement synchrone sur un thread de région.
        world.getChunkAtAsync(chunkX, chunkZ, true, chunk -> {
            world.addPluginChunkTicket(chunkX, chunkZ, plugin);
            ticketWorld.set(worldName);
            heldTickets.add(packChunk(chunkX, chunkZ));
            acquired.complete(null);
        });
        return acquired;
    }

    @Override
    public CompletableFuture<Void> writeBatch(String worldName, BlockBatch batch) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Monde introuvable : " + worldName));
        }
        BlockData[] palette = resolvedPalette.get();
        CompletableFuture<Void> written = new CompletableFuture<>();

        Bukkit.getRegionScheduler().execute(plugin, world, batch.chunkX(), batch.chunkZ(), () -> {
            try {
                ArenaBounds bounds = batch.bounds();
                int[] indices = batch.indices();
                byte[] ids = batch.paletteIds();
                for (int i = 0; i < batch.length(); i++) {
                    BlockPos pos = bounds.positionOf(indices[i]);
                    BlockData data = palette[Byte.toUnsignedInt(ids[i])];
                    Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
                    // applyPhysics=false : aucune cascade de mises à jour pendant le remplissage.
                    block.setBlockData(data, false);
                }
                written.complete(null);
            } catch (Throwable failure) {
                written.completeExceptionally(failure);
            }
        });
        return written;
    }

    @Override
    public void releaseChunks() {
        String worldName = ticketWorld.getAndSet(null);
        if (worldName == null) {
            heldTickets.clear();
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            world.removePluginChunkTickets(plugin);
        }
        heldTickets.clear();
    }

    @Override
    public int heldChunkTickets() {
        return heldTickets.size();
    }

    @Override
    public boolean worldExists(String worldName) {
        return Bukkit.getWorld(worldName) != null;
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFF_FFFFL);
    }
}
