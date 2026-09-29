package io.github.youpl.onedonjon.command;

import java.util.ArrayList;
import java.util.List;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.gui.MainMenu;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.run.DungeonRun;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class DonjonCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("play", "launch", "leave", "list", "status");
    private static final List<String> ADMIN_SUBS = List.of("menu", "start", "stop", "wand", "reload");

    private final OneDonjonPlugin plugin;

    public DonjonCommand(OneDonjonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? (s.hasPermission("onedonjon.admin") ? "menu" : "list") : a[0].toLowerCase();
        boolean admin = ADMIN_SUBS.contains(sub);
        if (admin && !s.hasPermission("onedonjon.admin")) {
            Msg.error(s, "Permission manquante.");
            return true;
        }
        if (!admin && !s.hasPermission("onedonjon.play")) {
            Msg.error(s, "Permission manquante.");
            return true;
        }
        if (sub.equals("reload")) {
            plugin.reload();
            Msg.send(s, "Configuration et donjons recharges.");
            return true;
        }
        if (sub.equals("list")) {
            list(s);
            return true;
        }
        if (!(s instanceof Player p)) {
            Msg.error(s, "Commande reservee aux joueurs.");
            return true;
        }
        switch (sub) {
            case "menu" -> MainMenu.of(plugin, p).open();
            case "wand" -> {
                p.getInventory().addItem(plugin.selections().wand());
                Msg.send(p, "Baguette donnee : clic gauche = coin 1, clic droit = coin 2.");
            }
            case "play" -> withDungeon(p, a, d -> plugin.runs().play(p, d));
            case "start" -> withDungeon(p, a, d -> plugin.runs().start(p, d));
            case "launch" -> result(p, plugin.runs().launch(p));
            case "leave" -> {
                DungeonRun r = plugin.runs().of(p);
                if (r == null) Msg.error(p, "Vous n'etes dans aucun donjon.");
                else r.remove(p, true);
            }
            case "stop" -> {
                DungeonRun r = a.length > 1 ? plugin.runs().of(a[1].toLowerCase()) : plugin.runs().of(p);
                if (r == null) Msg.error(p, "Aucune partie correspondante.");
                else r.end(false, "Arrete par un administrateur.");
            }
            case "status" -> {
                DungeonRun r = plugin.runs().of(p);
                Msg.send(p, r == null ? "Vous n'etes dans aucun donjon." : r.status());
            }
            default -> Msg.error(p, "Sous-commandes : " + String.join(", ", PLAYER_SUBS) + (p.hasPermission("onedonjon.admin")
                    ? ", " + String.join(", ", ADMIN_SUBS) : ""));
        }
        return true;
    }

    private void withDungeon(Player p, String[] a, java.util.function.Function<Dungeon, String> action) {
        if (a.length < 2) {
            Msg.error(p, "Usage : /donjon " + a[0] + " <id>");
            return;
        }
        Dungeon d = plugin.storage().get(a[1]);
        if (d == null) {
            Msg.error(p, "Donjon inconnu : " + Msg.escape(a[1]));
            return;
        }
        result(p, action.apply(d));
    }

    private void result(Player p, String error) {
        if (error != null) Msg.error(p, error);
    }

    private void list(CommandSender s) {
        Msg.send(s, "Donjons disponibles :");
        for (Dungeon d : plugin.storage().all()) {
            if (!d.enabled && !s.hasPermission("onedonjon.admin")) continue;
            boolean ready = d.problems().isEmpty();
            s.sendMessage(Msg.mm("<dark_gray> - <gold>" + Msg.escape(d.displayName) + " <gray>(" + d.id + ") "
                    + (ready && d.enabled ? "<green>jouable" : "<red>indisponible")));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(PLAYER_SUBS);
            if (s.hasPermission("onedonjon.admin")) out.addAll(ADMIN_SUBS);
        } else if (a.length == 2 && List.of("play", "start", "stop").contains(a[0].toLowerCase())) {
            plugin.storage().all().forEach(d -> out.add(d.id));
        }
        String prefix = a[a.length - 1].toLowerCase();
        out.removeIf(o -> !o.toLowerCase().startsWith(prefix));
        return out;
    }
}
