package io.github.youpl.onedonjon.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.NpcSpec;
import io.github.youpl.onedonjon.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/** PNJ Citizens : creation temporaire par donjon, detection du clic droit. */
public final class CitizensHook implements Listener {

    private final OneDonjonPlugin plugin;
    /** Callback (joueur, id de PNJ Citizens) ; enregistre une seule fois. */
    private BiConsumer<Player, Integer> clickHandler;
    private boolean listening;

    public CitizensHook(OneDonjonPlugin plugin) {
        this.plugin = plugin;
    }

    public void onClick(BiConsumer<Player, Integer> handler) {
        this.clickHandler = handler;
        if (listening || !plugin.hooks().citizens()) return;
        Class<?> ev = Reflect.cls("net.citizensnpcs.api.event.NPCRightClickEvent");
        if (ev == null) return;
        EventExecutor exec = (l, e) -> {
            try {
                Object npc = Reflect.call(e, "getNPC");
                Player clicker = (Player) Reflect.call(e, "getClicker");
                int id = (Integer) Reflect.call(npc, "getId");
                if (clickHandler != null) clickHandler.accept(clicker, id);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Citizens : clic PNJ illisible", ex);
            }
        };
        @SuppressWarnings("unchecked")
        Class<? extends Event> evc = (Class<? extends Event>) ev;
        Bukkit.getPluginManager().registerEvent(evc, this, EventPriority.NORMAL, exec, plugin);
        listening = true;
    }

    /** Cree et fait apparaitre un PNJ ; renvoie son id Citizens, ou -1. */
    public int spawn(NpcSpec spec) {
        if (!plugin.hooks().citizens() || spec.pos == null || spec.pos.toLocation() == null) return -1;
        try {
            Object registry = Reflect.call(Reflect.cls("net.citizensnpcs.api.CitizensAPI"), "getNPCRegistry");
            EntityType type;
            try {
                type = EntityType.valueOf(spec.entityType.toUpperCase());
            } catch (IllegalArgumentException e) {
                type = EntityType.PLAYER;
            }
            Object npc = Reflect.call(registry, "createNPC", type, spec.name);
            Reflect.call(npc, "spawn", spec.pos.toLocation());
            if (!spec.model.isBlank()) {
                Object ent = Reflect.call(npc, "getEntity");
                if (ent instanceof Entity e) plugin.hooks().applyModel(e, spec.model);
            }
            return (Integer) Reflect.call(npc, "getId");
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Citizens : PNJ '" + spec.id + "' non cree", e);
            return -1;
        }
    }

    public void destroy(int npcId) {
        if (!plugin.hooks().citizens()) return;
        try {
            Object registry = Reflect.call(Reflect.cls("net.citizensnpcs.api.CitizensAPI"), "getNPCRegistry");
            Object npc = Reflect.call(registry, "getById", npcId);
            if (npc != null) Reflect.call(npc, "destroy");
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Citizens : PNJ " + npcId + " non supprime", e);
        }
    }

}
