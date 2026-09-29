package io.github.youpl.onedonjon.deluxe;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.util.Msg;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Menus joueur / administrateur servis par DeluxeMenus. OneDonjon genere deux fichiers de menu
 * ({@value #PLAY_MENU}, {@value #ADMIN_MENU}) dans {@code DeluxeMenus/gui_menus/} a chaque changement de donjon.
 * Les menus ne font qu'executer des commandes {@code /donjon ...} : les droits y sont reverifies.
 *
 * <p>DeluxeMenus ne sait pas saisir du texte ni paginer des listes dynamiques : l'edition detaillee d'un
 * donjon (salles, vagues, pieges...) reste dans les menus internes, ouverts depuis le menu admin.
 */
public final class DeluxeMenusBridge {

    public static final String PLAY_MENU = "onedonjon_play";
    public static final String ADMIN_MENU = "onedonjon_admin";
    static final String MARKER = "Genere par OneDonjon";
    private static final int MAX_ITEMS = 45;

    private final OneDonjonPlugin plugin;
    private boolean pending;

    public DeluxeMenusBridge(OneDonjonPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        var p = Bukkit.getPluginManager().getPlugin("DeluxeMenus");
        return p != null && p.isEnabled() && plugin.core().settingBool("deluxemenus.enabled", true);
    }

    /** Ouvre un menu DeluxeMenus ; renvoie false s'il faut utiliser le menu interne. */
    public boolean open(Player p, String menu) {
        if (!available()) return false;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "deluxemenus open " + menu + " " + p.getName());
        return true;
    }

    /** Regroupe les demandes d'export d'un meme tick (chaque sauvegarde de donjon en declenche une). */
    public void requestExport() {
        if (pending) return;
        pending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pending = false;
            export();
        });
    }

    public void export() {
        if (!available()) return;
        var dm = Bukkit.getPluginManager().getPlugin("DeluxeMenus");
        File dir = new File(dm.getDataFolder(), "gui_menus");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("DeluxeMenus : dossier gui_menus introuvable.");
            return;
        }
        boolean changed = write(new File(dir, PLAY_MENU + ".yml"), playMenu(plugin.storage().all()));
        changed |= write(new File(dir, ADMIN_MENU + ".yml"), adminMenu(plugin.storage().all()));
        if (changed) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "deluxemenus reload");
    }

    /** Ecrit le fichier s'il a change. N'ecrase jamais un fichier qui n'est pas le notre. */
    private boolean write(File f, YamlConfiguration y) {
        String content = y.saveToString();
        try {
            if (f.exists()) {
                String old = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                if (!old.contains(MARKER)) {
                    plugin.getLogger().warning("DeluxeMenus : " + f.getName() + " existe et n'a pas ete genere par OneDonjon, non modifie.");
                    return false;
                }
                if (old.equals(content)) return false;
            }
            Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "DeluxeMenus : ecriture de " + f.getName() + " impossible", e);
            return false;
        }
    }

    // ------------------------------------------------------------ generation (pure, testee)

    /** Texte brut sur une ligne, sans balises ni caracteres pouvant etre interpretes par DeluxeMenus (& % [ ]). */
    static String plain(String mm) {
        String t = PlainTextComponentSerializer.plainText().serialize(Msg.mm(mm));
        t = t.replaceAll("[\\p{Cntrl}]", " ").replaceAll("[&%\\[\\]{}]", "").trim();
        if (t.length() > 40) t = t.substring(0, 40);
        return t.isEmpty() ? "?" : t;
    }

    static YamlConfiguration base(String title, String permission, int size) {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(MARKER + " - ne pas modifier : ce fichier est reecrit a chaque changement."));
        y.set("menu_title", "&6&l" + title);
        y.set("size", size);
        y.set("open_requirement.requirements.perm.type", "has permission");
        y.set("open_requirement.requirements.perm.permission", permission);
        y.set("open_requirement.deny_commands", List.of("[message] &cPermission manquante."));
        return y;
    }

    public static YamlConfiguration playMenu(Collection<Dungeon> dungeons) {
        YamlConfiguration y = base("Donjons", "onedonjon.play", 54);
        int slot = 0;
        for (Dungeon d : dungeons) {
            if (!d.enabled || !d.problems().isEmpty()) continue;
            if (slot >= MAX_ITEMS) break;
            String key = "dungeon_" + slot;
            y.set("items." + key + ".material", "BOOK");
            y.set("items." + key + ".slot", slot);
            y.set("items." + key + ".display_name", "&6" + plain(d.displayName));
            y.set("items." + key + ".lore", List.of("&7Salles : &f" + d.rooms.size(),
                    "&7Joueurs : &f" + d.minPlayers + "-" + d.maxPlayers, "&7Vies : &f" + d.lives, "", "&aClic : creer / rejoindre le groupe"));
            // L'id est valide par [a-zA-Z0-9_-] a la creation et au chargement : pas d'injection dans la commande.
            y.set("items." + key + ".left_click_commands", List.of("[player] donjon play " + d.id, "[close]"));
            slot++;
        }
        closeItem(y);
        return y;
    }

    public static YamlConfiguration adminMenu(Collection<Dungeon> dungeons) {
        YamlConfiguration y = base("OneDonjon - Administration", "onedonjon.admin", 54);
        int slot = 0;
        for (Dungeon d : dungeons) {
            if (slot >= MAX_ITEMS) break;
            List<String> pb = d.problems();
            String key = "dungeon_" + slot;
            y.set("items." + key + ".material", d.enabled ? "WRITABLE_BOOK" : "BOOK");
            y.set("items." + key + ".slot", slot);
            y.set("items." + key + ".display_name", "&e" + plain(d.displayName) + " &8(" + d.id + ")");
            List<String> lore = new ArrayList<>();
            lore.add(d.enabled ? "&aActif" : "&cDesactive");
            lore.add("&7Salles : &f" + d.rooms.size());
            lore.add(pb.isEmpty() ? "&aPret" : "&c" + pb.size() + " probleme(s)");
            lore.add("");
            lore.add("&eClic gauche : editer");
            lore.add("&eClic droit : tester (solo)");
            y.set("items." + key + ".lore", lore);
            y.set("items." + key + ".left_click_commands", List.of("[player] donjon edit " + d.id, "[close]"));
            y.set("items." + key + ".right_click_commands", List.of("[player] donjon start " + d.id, "[close]"));
            slot++;
        }
        y.set("items.editor.material", "COMPARATOR");
        y.set("items.editor.slot", 49);
        y.set("items.editor.display_name", "&bEditeur complet");
        y.set("items.editor.lore", List.of("&7Creer un donjon, integrations,", "&7baguette de selection."));
        y.set("items.editor.left_click_commands", List.of("[player] donjon editor", "[close]"));
        closeItem(y);
        return y;
    }

    private static void closeItem(YamlConfiguration y) {
        y.set("items.close.material", "BARRIER");
        y.set("items.close.slot", 53);
        y.set("items.close.display_name", "&cFermer");
        y.set("items.close.left_click_commands", List.of("[close]"));
    }
}
