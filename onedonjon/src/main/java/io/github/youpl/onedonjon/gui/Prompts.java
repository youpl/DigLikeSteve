package io.github.youpl.onedonjon.gui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Saisie d'une valeur dans le chat : le message du joueur est capture, jamais diffuse. */
public final class Prompts implements Listener {

    private record Pending(Consumer<String> onValue, Runnable onCancel) {}

    private final OneDonjonPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public Prompts(OneDonjonPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String question, Consumer<String> onValue, Runnable onCancel) {
        pending.put(p.getUniqueId(), new Pending(onValue, onCancel));
        p.closeInventory();
        Msg.send(p, "<white>" + Msg.escape(question));
        Msg.send(p, "<dark_gray>Ecrivez la valeur dans le chat, ou <red>annuler</red> pour abandonner.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Pending pd = pending.remove(e.getPlayer().getUniqueId());
        if (pd == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (text.length() > io.github.youpl.onedonjon.util.Limits.MAX_TEXT) text = text.substring(0, io.github.youpl.onedonjon.util.Limits.MAX_TEXT);
        final String value = text;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!e.getPlayer().isOnline()) return;
            if (value.equalsIgnoreCase("annuler") || value.equalsIgnoreCase("cancel") || value.isEmpty()) {
                pd.onCancel().run();
                return;
            }
            plugin.setActor(e.getPlayer());
            try {
                pd.onValue().accept(value);
            } catch (RuntimeException ex) {
                Msg.error(e.getPlayer(), "Valeur invalide : " + Msg.escape(String.valueOf(ex.getMessage())));
                pd.onCancel().run();
            } finally {
                plugin.setActor(null);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        plugin.setEditing(e.getPlayer(), null);
    }
}
