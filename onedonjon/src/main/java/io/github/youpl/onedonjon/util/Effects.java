package io.github.youpl.onedonjon.util;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Effects {

    private Effects() {}

    /** Analyse "EFFET:niveau" (niveau 1 par defaut). */
    public static PotionEffect parse(String spec, int durationTicks) {
        String[] p = spec.trim().split(":");
        PotionEffectType type = type(p[0]);
        if (type == null) return null;
        int level = 1;
        if (p.length > 1) {
            try {
                level = Limits.clamp(Integer.parseInt(p[1].trim()), 1, 255);
            } catch (NumberFormatException ignored) {
                // niveau 1
            }
        }
        return new PotionEffect(type, durationTicks, level - 1, false, true);
    }

    public static PotionEffectType type(String name) {
        return Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(name.trim().toLowerCase()));
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (PotionEffectType t : Registry.POTION_EFFECT_TYPE) out.add(t.getKey().getKey().toUpperCase());
        out.sort(String::compareTo);
        return out;
    }
}
