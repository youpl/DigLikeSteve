package io.github.youpl.diglikesteve.platform.paper.inventory;

import io.github.youpl.diglikesteve.core.shared.SessionId;
import io.github.youpl.diglikesteve.platform.paper.DlsKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;

/**
 * Kit de la partie (DAT §9).
 *
 * <p>Chaque outil est incassable et marqué au PDC avec la session et son type. C'est ce
 * marquage — et non un nom ou un lore falsifiable — qui permet de le reconnaître et de le
 * supprimer lors de toute restauration, même s'il a changé de slot.</p>
 */
public final class KitFactory {

    /** Slots de la hotbar réservés au kit. */
    public static final int[] KIT_SLOTS = {0, 1, 2, 3, 4};

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DlsKeys keys;

    public KitFactory(DlsKeys keys) {
        this.keys = Objects.requireNonNull(keys, "keys");
    }

    /** Applique le kit dans les slots réservés. L'inventaire doit déjà avoir été vidé. */
    public void apply(PlayerInventory inventory, SessionId sessionId) {
        List<ItemStack> kit = List.of(
                tool(Material.DIAMOND_PICKAXE, "pickaxe", "<aqua>Pioche de Steve</aqua>", sessionId),
                tool(Material.DIAMOND_AXE, "axe", "<aqua>Hache de Steve</aqua>", sessionId),
                tool(Material.DIAMOND_SHOVEL, "shovel", "<aqua>Pelle de Steve</aqua>", sessionId),
                tool(Material.SHEARS, "shears", "<aqua>Cisaille de Steve</aqua>", sessionId),
                tool(Material.FLINT_AND_STEEL, "igniter", "<red>Briquet de Steve</red>", sessionId));

        for (int i = 0; i < kit.size(); i++) {
            inventory.setItem(KIT_SLOTS[i], kit.get(i));
        }
        inventory.setHeldItemSlot(KIT_SLOTS[0]);
    }

    private ItemStack tool(Material material, String type, String miniMessageName, SessionId sessionId) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> {
            meta.setUnbreakable(true);
            meta.displayName(MINI.deserialize(miniMessageName).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(MINI.deserialize("<dark_gray>Dig Like Steve</dark_gray>")
                    .decoration(TextDecoration.ITALIC, false)));
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keys.sessionId(), PersistentDataType.STRING, sessionId.toString());
            container.set(keys.toolType(), PersistentDataType.STRING, type);
        });
        return stack;
    }

    /** Vrai si l'item appartient au kit DLS, quelle que soit la session. */
    public boolean isKitItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(keys.toolType(), PersistentDataType.STRING);
    }

    /** Vrai si l'item est le briquet DLS de la session indiquée. */
    public boolean isIgniter(ItemStack stack, SessionId sessionId) {
        if (!isKitItem(stack)) {
            return false;
        }
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
        return "igniter".equals(container.get(keys.toolType(), PersistentDataType.STRING))
                && sessionId.toString().equals(container.get(keys.sessionId(), PersistentDataType.STRING));
    }

    /** Vrai si l'item porte le marquage d'une session DLS quelconque. */
    public boolean belongsToSession(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(keys.sessionId(), PersistentDataType.STRING);
    }

    public Component displayName(String miniMessage) {
        return MINI.deserialize(miniMessage);
    }
}
