package io.github.youpl.onedonjon.model;

import java.util.ArrayList;
import java.util.List;

import io.github.youpl.onedonjon.util.Limits;
import org.bukkit.configuration.ConfigurationSection;

public final class Wave {

    /** Delai (ticks) entre la fin de la vague precedente et l'apparition de celle-ci. */
    public int delayTicks = 60;
    public List<MobSpec> mobs = new ArrayList<>();
    public List<Action> onStart = new ArrayList<>();

    public void save(ConfigurationSection s) {
        s.set("delay-ticks", delayTicks);
        Action.saveList(s, "on-start", onStart);
        ConfigurationSection m = s.createSection("mobs");
        for (int i = 0; i < mobs.size(); i++) mobs.get(i).save(m.createSection(String.valueOf(i)));
    }

    public static Wave load(ConfigurationSection s) {
        Wave w = new Wave();
        w.delayTicks = Limits.clamp(s.getInt("delay-ticks", 60), 0, Limits.MAX_DELAY_TICKS);
        w.onStart = Action.loadList(s, "on-start");
        ConfigurationSection m = s.getConfigurationSection("mobs");
        if (m != null) for (String k : m.getKeys(false)) w.mobs.add(MobSpec.load(m.getConfigurationSection(k)));
        return w;
    }

    public int totalMobs() {
        return mobs.stream().mapToInt(x -> x.count).sum();
    }
}
