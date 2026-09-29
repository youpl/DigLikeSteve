package io.github.youpl.onedonjon.util;

/** Bornes appliquees aux valeurs lues des fichiers YAML (editables a la main) et des saisies. */
public final class Limits {

    public static final int MAX_MOBS_PER_SPEC = 200;
    public static final double MAX_HEALTH = 100_000;
    public static final double MAX_DAMAGE_MULT = 100;
    public static final double MAX_TRAP_VALUE = 1_000;
    public static final double MAX_EXPLOSION_POWER = 8;
    public static final double MAX_TRAP_RADIUS = 30;
    public static final int MAX_DELAY_TICKS = 12_000;
    public static final int MAX_DOOR_VOLUME = 20_000;
    public static final int MAX_ITEM_AMOUNT = 2_304;
    public static final int MAX_TEXT = 256;
    public static final int MAX_LIST = 200;

    private Limits() {}

    public static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
