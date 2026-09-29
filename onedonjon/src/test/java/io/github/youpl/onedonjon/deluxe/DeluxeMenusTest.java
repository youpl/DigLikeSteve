package io.github.youpl.onedonjon.deluxe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.github.youpl.onedonjon.model.Cuboid;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.model.Pos;
import io.github.youpl.onedonjon.model.Room;
import org.junit.jupiter.api.Test;

class DeluxeMenusTest {

    private static Dungeon ready(String id, String name) {
        Dungeon d = new Dungeon(id);
        d.displayName = name;
        d.start = new Pos("w", 0, 64, 0, 0f, 0f);
        Room r = new Room();
        r.region = new Cuboid("w", 0, 0, 0, 1, 1, 1);
        d.rooms.add(r);
        return d;
    }

    @Test
    void playMenuListsOnlyPlayableDungeons() {
        Dungeon ok = ready("crypte", "<red>Crypte");
        Dungeon disabled = ready("off", "Off");
        disabled.enabled = false;
        Dungeon broken = new Dungeon("broken");

        var y = DeluxeMenusBridge.playMenu(List.of(ok, disabled, broken));
        assertEquals("[player] donjon play crypte", y.getStringList("items.dungeon_0.left_click_commands").get(0));
        assertNull(y.getConfigurationSection("items.dungeon_1"));
        assertEquals("onedonjon.play", y.getString("open_requirement.requirements.perm.permission"));
        assertTrue(y.saveToString().contains(DeluxeMenusBridge.MARKER));
    }

    @Test
    void displayNamesCannotInjectPlaceholdersOrCommands() {
        Dungeon d = ready("x", "<red>Boss &c%player_ip% [console] {a}");
        var y = DeluxeMenusBridge.adminMenu(List.of(d));
        String name = y.getString("items.dungeon_0.display_name");
        assertFalse(name.contains("%"));
        assertFalse(name.contains("[console]"));
        assertFalse(name.contains("{"));
        assertEquals(1, y.getStringList("items.dungeon_0.left_click_commands").stream().filter(s -> s.startsWith("[player]")).count());
        assertEquals("onedonjon.admin", y.getString("open_requirement.requirements.perm.permission"));
    }

    @Test
    void menuSizeIsBounded() {
        java.util.List<Dungeon> many = new java.util.ArrayList<>();
        for (int i = 0; i < 80; i++) many.add(ready("d" + i, "D" + i));
        var y = DeluxeMenusBridge.playMenu(many);
        assertTrue(y.getConfigurationSection("items.dungeon_44") != null);
        assertNull(y.getConfigurationSection("items.dungeon_45"));
    }
}
