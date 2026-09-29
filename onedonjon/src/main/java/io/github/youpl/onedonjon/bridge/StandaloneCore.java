package io.github.youpl.onedonjon.bridge;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import org.bukkit.permissions.Permissible;

/** Repli sans OneCore : config.yml local, permissions Bukkit, journal d'audit dans plugins/OneDonjon/audit.log. */
public class StandaloneCore implements CoreBridge {

    private static final long MAX_LOG_BYTES = 5L * 1024 * 1024;

    protected final OneDonjonPlugin plugin;
    private final List<Runnable> reloadListeners = new ArrayList<>();
    private final File logFile;

    public StandaloneCore(OneDonjonPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "audit.log");
    }

    @Override public String name() { return "Standalone"; }
    @Override public boolean connected() { return false; }

    @Override
    public boolean isAdmin(Permissible who) {
        return who.hasPermission("onedonjon.admin");
    }

    @Override
    public boolean has(Permissible who, String permission) {
        return who.hasPermission(permission);
    }

    @Override
    public String setting(String path, String def) {
        Object v = plugin.getConfig().get(path);
        return v == null ? def : String.valueOf(v);
    }

    @Override
    public void onReload(Runnable callback) {
        reloadListeners.add(callback);
    }

    /** Declenche les rappels enregistres (appele apres rechargement de la configuration). */
    public void fireReload() {
        for (Runnable r : new ArrayList<>(reloadListeners)) {
            try {
                r.run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Rappel de rechargement en echec", e);
            }
        }
    }

    @Override
    public void audit(String actor, String action, String detail) {
        String line = Instant.now() + " " + clean(actor) + " " + clean(action) + " " + clean(detail);
        try {
            File dir = logFile.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            if (logFile.length() > MAX_LOG_BYTES) {
                File old = new File(dir, "audit.log.1");
                Files.move(logFile.toPath(), old.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try (BufferedWriter w = Files.newBufferedWriter(logFile.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(line);
                w.newLine();
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Journal d'audit indisponible", e);
        }
    }

    /** Une entree = une ligne : retire retours a la ligne et caracteres de controle (injection de logs). */
    static String clean(String s) {
        if (s == null) return "-";
        String c = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return c.length() > 300 ? c.substring(0, 300) : c;
    }

    @Override
    public void close() {
        reloadListeners.clear();
    }
}
