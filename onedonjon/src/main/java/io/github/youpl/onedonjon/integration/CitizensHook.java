package io.github.youpl.onedonjon.integration;

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
    private BiConsumer<Player, Object> clickHandler;
    /** Registre anonyme : les PNJ de donjon ne sont jamais ecrits sur disque par Citizens (pas d'orphelins apres un crash). */
    private Object registry;
    private boolean listening;

    public CitizensHook(OneDonjonPlugin plugin) {
        this.plugin = plugin;
    }

    public void onClick(BiConsumer<Player, Object> handler) {
        this.clickHandler = handler;
        if (listening || !plugin.hooks().citizens()) return;
        Class<?> ev = Reflect.cls("net.citizensnpcs.api.event.NPCRightClickEvent");
        if (ev == null) return;
        EventExecutor exec = (l, e) -> {
            try {
                Object npc = Reflect.call(e, "getNPC");
                Player clicker = (Player) Reflect.call(e, "getClicker");
                if (clickHandler != null) clickHandler.accept(clicker, npc);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Citizens : clic PNJ illisible", ex);
            }
        };
        @SuppressWarnings("unchecked")
        Class<? extends Event> evc = (Class<? extends Event>) ev;
        Bukkit.getPluginManager().registerEvent(evc, this, EventPriority.NORMAL, exec, plugin);
        listening = true;
    }

    private Object registry() throws ReflectiveOperationException {
        if (registry == null) {
            Class<?> store = Reflect.cls("net.citizensnpcs.api.npc.MemoryNPCDataStore");
            Class<?> api = Reflect.cls("net.citizensnpcs.api.CitizensAPI");
            registry = Reflect.call(api, "createAnonymousNPCRegistry", store.getConstructor().newInstance());
        }
        return registry;
    }

    /** Cree et fait apparaitre un PNJ ; renvoie l'objet NPC Citizens (identite), ou null. */
    public Object spawn(NpcSpec spec) {
        if (!plugin.hooks().citizens() || spec.pos == null || spec.pos.toLocation() == null) return null;
        try {
            Object registry = registry();
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
            return npc;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Citizens : PNJ '" + spec.id + "' non cree", e);
            return null;
        }
    }

    public void destroy(Object npc) {
        if (npc == null) return;
        try {
            Reflect.call(npc, "destroy");
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Citizens : PNJ non supprime", e);
        }
    }
}
