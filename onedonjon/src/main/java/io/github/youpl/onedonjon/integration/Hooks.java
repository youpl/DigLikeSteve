package io.github.youpl.onedonjon.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.github.youpl.onedonjon.model.ItemRef;
import io.github.youpl.onedonjon.model.MobSpec;
import io.github.youpl.onedonjon.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Facade unique vers MythicMobs, MMOItems, Nexo, ModelEngine, Citizens et Typewriter.
 * Tout passe par reflexion : le plugin demarre sans aucun d'eux et survit aux changements de version.
 */
public final class Hooks {

    private final Logger log;

    public Hooks(Logger log) {
        this.log = log;
    }

    public boolean has(String plugin) {
        Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
        return p != null && p.isEnabled();
    }

    public boolean mythic() { return has("MythicMobs"); }
    public boolean mmoItems() { return has("MMOItems"); }
    public boolean nexo() { return has("Nexo"); }
    public boolean modelEngine() { return has("ModelEngine"); }
    public boolean citizens() { return has("Citizens"); }
    public boolean typewriter() { return has("Typewriter"); }

    // ---------------------------------------------------------------- MythicMobs

    /** Fait apparaitre un mob MythicMobs, ou null en cas d'echec. */
    public Entity spawnMythic(String mobName, Location loc) {
        if (!mythic()) return null;
        try {
            Object inst = Reflect.call(Reflect.cls("io.lumine.mythic.bukkit.MythicBukkit"), "inst");
            Object mgr = Reflect.call(inst, "getMobManager");
            Object active = Reflect.call(mgr, "spawnMob", mobName, loc);
            if (active == null) return null;
            Object abstractEntity = Reflect.call(active, "getEntity");
            return (Entity) Reflect.call(abstractEntity, "getBukkitEntity");
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.log(Level.WARNING, "MythicMobs : impossible de faire apparaitre '" + mobName + "'", e);
            return null;
        }
    }

    public List<String> mythicMobNames() {
        List<String> out = new ArrayList<>();
        if (!mythic()) return out;
        try {
            Object inst = Reflect.call(Reflect.cls("io.lumine.mythic.bukkit.MythicBukkit"), "inst");
            Object mgr = Reflect.call(inst, "getMobManager");
            for (Object m : (Iterable<?>) Reflect.call(mgr, "getMobTypes")) {
                out.add((String) Reflect.call(m, "getInternalName"));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.log(Level.WARNING, "MythicMobs : liste des mobs indisponible", e);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    // ---------------------------------------------------------------- ModelEngine

    /** Applique un modele ModelEngine a une entite. Renvoie false si impossible. */
    public boolean applyModel(Entity entity, String modelId) {
        if (modelId == null || modelId.isBlank() || !modelEngine()) return false;
        try {
            Class<?> api = Reflect.cls("com.ticxo.modelengine.api.ModelEngineAPI");
            Object modeled = Reflect.call(api, "createModeledEntity", entity);
            Object active = Reflect.call(api, "createActiveModel", modelId);
            Reflect.call(modeled, "addModel", active, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.log(Level.WARNING, "ModelEngine : modele '" + modelId + "' non applique", e);
            return false;
        }
    }

    public List<String> modelIds() {
        List<String> out = new ArrayList<>();
        if (!modelEngine()) return out;
        try {
            Object api = Reflect.call(Reflect.cls("com.ticxo.modelengine.api.ModelEngineAPI"), "getAPI");
            Object registry = Reflect.call(api, "getModelRegistry");
            for (Object k : (Iterable<?>) Reflect.call(registry, "getKeys")) out.add(String.valueOf(k));
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.log(Level.WARNING, "ModelEngine : liste des modeles indisponible", e);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    // ---------------------------------------------------------------- Objets

    /** Resout une reference d'objet, ou null si le plugin / l'id est introuvable. */
    public ItemStack resolve(ItemRef ref) {
        try {
            switch (ref.source()) {
                case VANILLA -> {
                    Material m = Material.matchMaterial(ref.id());
                    return m == null || m.isAir() ? null : new ItemStack(m, ref.amount());
                }
                case NEXO -> {
                    if (!nexo()) return null;
                    Object builder = Reflect.call(Reflect.cls("com.nexomc.nexo.api.NexoItems"), "itemFromId", ref.id());
                    if (builder == null) return null;
                    ItemStack s = (ItemStack) Reflect.call(builder, "build");
                    s.setAmount(ref.amount());
                    return s;
                }
                case MMOITEMS -> {
                    if (!mmoItems()) return null;
                    String[] p = ref.id().split(":", 2);
                    if (p.length < 2) return null;
                    Object plugin = Reflect.staticField(Reflect.cls("net.Indyuce.mmoitems.MMOItems"), "plugin");
                    Object type = Reflect.call(Reflect.call(plugin, "getTypes"), "get", p[0].toUpperCase());
                    if (type == null) return null;
                    ItemStack s = (ItemStack) Reflect.call(plugin, "getItem", type, p[1].toUpperCase());
                    if (s == null) return null;
                    s.setAmount(ref.amount());
                    return s;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.log(Level.WARNING, "Objet introuvable : " + ref.describe(), e);
        }
        return null;
    }

    /** Identifiant "SOURCE:id" d'un objet en jeu (Nexo / MMOItems), ou null si vanilla. */
    public String identify(ItemStack item) {
        if (item == null) return null;
        try {
            if (nexo()) {
                Object id = Reflect.call(Reflect.cls("com.nexomc.nexo.api.NexoItems"), "idFromItem", item);
                if (id != null) return "NEXO:" + id;
            }
            if (mmoItems()) {
                Object nbt = Reflect.call(Reflect.cls("io.lumine.mythic.lib.api.item.NBTItem"), "get", item);
                String id = (String) Reflect.call(nbt, "getString", "MMOITEMS_ITEM_ID");
                String type = (String) Reflect.call(nbt, "getString", "MMOITEMS_ITEM_TYPE");
                if (id != null && !id.isEmpty() && type != null) return "MMOITEMS:" + type + ":" + id;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // pas un objet custom
        }
        return null;
    }

    public boolean matches(ItemStack item, ItemRef ref) {
        if (item == null || item.getType().isAir()) return false;
        switch (ref.source()) {
            case VANILLA -> {
                Material m = Material.matchMaterial(ref.id());
                return m != null && item.getType() == m && identify(item) == null;
            }
            case NEXO -> {
                return ("NEXO:" + ref.id()).equalsIgnoreCase(identify(item));
            }
            case MMOITEMS -> {
                return ("MMOITEMS:" + ref.id()).equalsIgnoreCase(identify(item));
            }
        }
        return false;
    }

    public List<String> nexoItemIds() {
        List<String> out = new ArrayList<>();
        if (!nexo()) return out;
        try {
            Object ids = Reflect.call(Reflect.cls("com.nexomc.nexo.api.NexoItems"), "itemNames");
            if (ids instanceof String[] arr) out.addAll(List.of(arr));
            else if (ids instanceof Iterable<?> it) for (Object o : it) out.add(String.valueOf(o));
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.log(Level.WARNING, "Nexo : liste des objets indisponible", e);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Ids MMOItems au format "TYPE:ID". */
    public List<String> mmoItemIds() {
        List<String> out = new ArrayList<>();
        if (!mmoItems()) return out;
        try {
            Object plugin = Reflect.staticField(Reflect.cls("net.Indyuce.mmoitems.MMOItems"), "plugin");
            Object types = Reflect.call(plugin, "getTypes");
            Object templates = Reflect.call(plugin, "getTemplates");
            for (Object type : (Iterable<?>) Reflect.call(types, "getAll")) {
                String typeId = (String) Reflect.call(type, "getId");
                for (Object id : (Iterable<?>) Reflect.call(templates, "getTemplateNames", type)) {
                    out.add(typeId + ":" + id);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.log(Level.WARNING, "MMOItems : liste des objets indisponible", e);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    // ---------------------------------------------------------------- Divers

    public static List<EntityType> spawnableMobTypes() {
        List<EntityType> out = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            if (t.isSpawnable() && t.isAlive() && t != EntityType.PLAYER && t != EntityType.ARMOR_STAND
                    && t != EntityType.GIANT) {
                out.add(t);
            }
        }
        return out;
    }

    public String describe() {
        return "MythicMobs=" + mythic() + " MMOItems=" + mmoItems() + " Nexo=" + nexo()
                + " ModelEngine=" + modelEngine() + " Citizens=" + citizens() + " Typewriter=" + typewriter();
    }

    /** Raccourci : type de source a partir d'un {@link MobSpec.Source}. */
    public boolean available(MobSpec.Source s) {
        return s == MobSpec.Source.VANILLA || mythic();
    }
}
