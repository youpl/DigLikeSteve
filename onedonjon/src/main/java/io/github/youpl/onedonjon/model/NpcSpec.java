package io.github.youpl.onedonjon.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

/** PNJ Citizens present dans une salle ; un clic droit declenche ses actions. */
public final class NpcSpec {

    public String id = "npc";
    public String name = "Gardien";
    public String entityType = "PLAYER";
    public Pos pos;
    /** Modele ModelEngine optionnel. */
    public String model = "";
    public List<Action> onClick = new ArrayList<>();

    public void save(ConfigurationSection s) {
        s.set("id", id);
        s.set("name", name);
        s.set("entity-type", entityType);
        if (pos != null) pos.save(s.createSection("pos"));
        s.set("model", model);
        Action.saveList(s, "on-click", onClick);
    }

    public static NpcSpec load(ConfigurationSection s) {
        NpcSpec n = new NpcSpec();
        n.id = s.getString("id", "npc");
        n.name = s.getString("name", "Gardien");
        n.entityType = s.getString("entity-type", "PLAYER");
        n.pos = Pos.load(s.getConfigurationSection("pos"));
        n.model = s.getString("model", "");
        n.onClick = Action.loadList(s, "on-click");
        return n;
    }
}
