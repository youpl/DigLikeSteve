package io.github.youpl.diglikesteve.platform.paper.inventory;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Sérialisation binaire de l'état complet d'un joueur (DAT §8.1).
 *
 * <p>Les {@code ItemStack} passent par le format binaire Paper, qui embarque la
 * {@code DataVersion} : il survit aux migrations bien mieux que la sérialisation YAML Bukkit.
 * Le reste de l'état est écrit en champs typés explicites.</p>
 *
 * <p>L'Ender Chest n'est ni vidé ni sauvegardé : DLS ne le modifie pas.</p>
 */
public final class PlayerStateCodec {

    /** Version du format produit par cet adaptateur. */
    public static final int FORMAT_VERSION = 1;

    private static final int MAGIC = 0x444C5301;

    private PlayerStateCodec() {
    }

    /** Sérialise sans rien modifier. À exécuter sur l'{@code EntityScheduler} du joueur. */
    public static byte[] encode(Player player) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
             DataOutputStream out = new DataOutputStream(buffer)) {

            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);

            PlayerInventory inventory = player.getInventory();
            writeItems(out, inventory.getContents());
            writeItems(out, inventory.getArmorContents());
            writeItems(out, inventory.getExtraContents());
            writeItem(out, player.getItemOnCursor());

            out.writeInt(inventory.getHeldItemSlot());
            out.writeInt(player.getLevel());
            out.writeFloat(player.getExp());
            out.writeInt(player.getTotalExperience());
            out.writeUTF(player.getGameMode().name());
            out.writeDouble(player.getHealth());
            out.writeDouble(maxHealth(player));
            out.writeInt(player.getFoodLevel());
            out.writeFloat(player.getSaturation());
            out.writeBoolean(player.getAllowFlight());
            out.writeBoolean(player.isFlying());

            writePotions(out, player.getActivePotionEffects());

            Location location = player.getLocation();
            out.writeUTF(location.getWorld() == null ? "" : location.getWorld().getName());
            out.writeDouble(location.getX());
            out.writeDouble(location.getY());
            out.writeDouble(location.getZ());
            out.writeFloat(location.getYaw());
            out.writeFloat(location.getPitch());

            out.flush();
            return buffer.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException("Sérialisation de l'état du joueur impossible", failure);
        }
    }

    /**
     * Réapplique exactement tous les champs. Utilise {@code setContents} et des slots exacts,
     * jamais {@code addItem} : aucun doublon, aucun débordement au sol.
     */
    public static void decodeAndApply(Player player, byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalStateException("Snapshot invalide : entête inattendu");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IllegalStateException("Version de snapshot non supportée : " + version);
            }

            ItemStack[] contents = readItems(in);
            ItemStack[] armor = readItems(in);
            ItemStack[] extra = readItems(in);
            ItemStack cursor = readItem(in);

            int heldSlot = in.readInt();
            int level = in.readInt();
            float exp = in.readFloat();
            int totalExperience = in.readInt();
            String gameMode = in.readUTF();
            double health = in.readDouble();
            double maxHealth = in.readDouble();
            int foodLevel = in.readInt();
            float saturation = in.readFloat();
            boolean allowFlight = in.readBoolean();
            boolean flying = in.readBoolean();
            List<PotionEffect> potions = readPotions(in);

            String worldName = in.readUTF();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float pitch = in.readFloat();

            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setContents(contents);
            inventory.setArmorContents(armor);
            inventory.setExtraContents(extra);
            player.setItemOnCursor(cursor);
            if (heldSlot >= 0 && heldSlot < 9) {
                inventory.setHeldItemSlot(heldSlot);
            }

            player.setGameMode(parseGameMode(gameMode));
            player.setLevel(level);
            player.setExp(exp);
            player.setTotalExperience(totalExperience);
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setHealth(Math.max(0.1d, Math.min(health, maxHealth(player, maxHealth))));
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);

            player.clearActivePotionEffects();
            potions.forEach(player::addPotionEffect);

            // L'emplacement d'origine reste dans le snapshot et n'est relu que par
            // originLocation() : la destination de sortie configurée prime sur lui.
            if (worldName.isEmpty() || Double.isNaN(x + y + z + yaw + pitch)) {
                throw new IllegalStateException("Snapshot invalide : emplacement d'origine illisible");
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Restauration de l'état du joueur impossible", failure);
        }
    }

    /** Emplacement d'origine mémorisé dans le snapshot. */
    public static Location originLocation(byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            in.readInt();
            in.readInt();
            skipItems(in);
            skipItems(in);
            skipItems(in);
            skipItem(in);
            in.readInt();
            in.readInt();
            in.readFloat();
            in.readInt();
            in.readUTF();
            in.readDouble();
            in.readDouble();
            in.readInt();
            in.readFloat();
            in.readBoolean();
            in.readBoolean();
            skipPotions(in);
            String worldName = in.readUTF();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float pitch = in.readFloat();
            World world = worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
            return world == null ? null : new Location(world, x, y, z, yaw, pitch);
        } catch (IOException failure) {
            return null;
        }
    }

    private static void writeItems(DataOutputStream out, ItemStack[] items) throws IOException {
        byte[] encoded = ItemStack.serializeItemsAsBytes(items);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static ItemStack[] readItems(DataInputStream in) throws IOException {
        byte[] encoded = new byte[in.readInt()];
        in.readFully(encoded);
        return ItemStack.deserializeItemsFromBytes(encoded);
    }

    private static void skipItems(DataInputStream in) throws IOException {
        in.skipNBytes(in.readInt());
    }

    private static void writeItem(DataOutputStream out, ItemStack item) throws IOException {
        if (item == null || item.getType().isAir()) {
            out.writeInt(0);
            return;
        }
        byte[] encoded = item.serializeAsBytes();
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static ItemStack readItem(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == 0) {
            return ItemStack.empty();
        }
        byte[] encoded = new byte[length];
        in.readFully(encoded);
        return ItemStack.deserializeBytes(encoded);
    }

    private static void skipItem(DataInputStream in) throws IOException {
        in.skipNBytes(in.readInt());
    }

    private static void writePotions(DataOutputStream out, Collection<PotionEffect> effects) throws IOException {
        out.writeInt(effects.size());
        for (PotionEffect effect : effects) {
            NamespacedKey key = Registry.MOB_EFFECT.getKey(effect.getType());
            out.writeUTF(key == null ? "minecraft:speed" : key.toString());
            out.writeInt(effect.getDuration());
            out.writeInt(effect.getAmplifier());
            out.writeBoolean(effect.isAmbient());
            out.writeBoolean(effect.hasParticles());
            out.writeBoolean(effect.hasIcon());
        }
    }

    private static List<PotionEffect> readPotions(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<PotionEffect> effects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String key = in.readUTF();
            int duration = in.readInt();
            int amplifier = in.readInt();
            boolean ambient = in.readBoolean();
            boolean particles = in.readBoolean();
            boolean icon = in.readBoolean();
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.fromString(key));
            if (type != null) {
                effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            }
        }
        return effects;
    }

    private static void skipPotions(DataInputStream in) throws IOException {
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            in.readUTF();
            in.readInt();
            in.readInt();
            in.readBoolean();
            in.readBoolean();
            in.readBoolean();
        }
    }

    private static GameMode parseGameMode(String name) {
        try {
            return GameMode.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return GameMode.SURVIVAL;
        }
    }

    private static double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0d : attribute.getValue();
    }

    private static double maxHealth(Player player, double recorded) {
        double current = maxHealth(player);
        return recorded <= 0.0d ? current : Math.min(recorded, current);
    }
}
