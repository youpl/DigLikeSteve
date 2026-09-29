package io.github.youpl.onedonjon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.youpl.onedonjon.model.Action;
import io.github.youpl.onedonjon.model.Cuboid;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.model.ItemRef;
import io.github.youpl.onedonjon.model.MobSpec;
import io.github.youpl.onedonjon.model.NpcSpec;
import io.github.youpl.onedonjon.model.Pos;
import io.github.youpl.onedonjon.model.Puzzle;
import io.github.youpl.onedonjon.model.Room;
import io.github.youpl.onedonjon.model.SpawnPoint;
import io.github.youpl.onedonjon.model.Trap;
import io.github.youpl.onedonjon.model.Wave;
import io.github.youpl.onedonjon.run.ActionRunner;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ModelTest {

    private static Dungeon sample() {
        Dungeon d = new Dungeon("crypte");
        d.displayName = "<red>Crypte";
        d.start = new Pos("world", 1, 64, 2, 90f, 0f);
        d.exit = new Pos("world", 0, 70, 0, 0f, 0f);
        d.lives = 5;
        d.timeLimitSeconds = 600;
        d.rewards.add(new Action(Action.Type.ITEM, "NEXO ruby 2"));
        d.onStart.add(new Action(Action.Type.TYPEWRITER, "intro:start"));

        Room r = new Room();
        r.id = "room1";
        r.name = "Entree";
        r.region = new Cuboid("world", 0, 60, 0, 10, 70, 10);
        r.door = new Cuboid("world", 10, 60, 4, 10, 63, 6);
        r.checkpoint = new Pos("world", 2, 64, 2, 0f, 0f);
        r.spawnPoints.add(new SpawnPoint("sp1", new Pos("world", 5, 64, 5, 0f, 0f)));

        Wave w = new Wave();
        w.delayTicks = 100;
        MobSpec m = new MobSpec();
        m.source = MobSpec.Source.MYTHICMOBS;
        m.type = "SkeletalKnight";
        m.count = 3;
        m.health = 80;
        m.damageMultiplier = 1.5;
        m.effects.add("SPEED:2");
        m.model = "knight";
        w.mobs.add(m);
        r.waves.add(w);

        MobSpec boss = new MobSpec();
        boss.bossBar = true;
        boss.health = 500;
        boss.onDeath.add(new Action(Action.Type.MESSAGE, "<green>Bravo"));
        r.boss = boss;

        Trap t = new Trap();
        t.type = Trap.Type.POTION_ZONE;
        t.pos = new Pos("world", 3, 64, 3, 0f, 0f);
        t.effect = "POISON:2";
        r.traps.add(t);

        Puzzle z = new Puzzle();
        z.type = Puzzle.Type.KEY;
        z.blocks.add("world,4,64,4");
        z.key = new ItemRef(ItemRef.Source.MMOITEMS, "MISC:KEY", 1);
        r.puzzles.add(z);

        NpcSpec n = new NpcSpec();
        n.pos = new Pos("world", 1, 64, 1, 0f, 0f);
        n.onClick.add(new Action(Action.Type.TYPEWRITER, "dialogue:gardien"));
        r.npcs.add(n);

        d.rooms.add(r);
        return d;
    }

    @Test
    void dungeonSurvivesYamlRoundTrip() throws Exception {
        Dungeon d = sample();
        String yaml = d.toYaml().saveToString();
        Dungeon back = Dungeon.fromYaml(YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)));

        assertEquals("crypte", back.id);
        assertEquals("<red>Crypte", back.displayName);
        assertEquals(5, back.lives);
        assertEquals(600, back.timeLimitSeconds);
        assertEquals(d.start, back.start);
        assertEquals(2, back.onStart.size() + back.rewards.size());

        Room r = back.rooms.get(0);
        assertEquals(new Cuboid("world", 0, 60, 0, 10, 70, 10), r.region);
        assertEquals(new Cuboid("world", 10, 60, 4, 10, 63, 6), r.door);
        assertEquals("sp1", r.spawnPoints.get(0).id());

        MobSpec m = r.waves.get(0).mobs.get(0);
        assertEquals(MobSpec.Source.MYTHICMOBS, m.source);
        assertEquals("SkeletalKnight", m.type);
        assertEquals(3, m.count);
        assertEquals(80.0, m.health);
        assertEquals(1.5, m.damageMultiplier);
        assertEquals("SPEED:2", m.effects.get(0));
        assertEquals("knight", m.model);
        assertEquals(100, r.waves.get(0).delayTicks);

        assertNotNull(r.boss);
        assertTrue(r.boss.bossBar);
        assertEquals("<green>Bravo", r.boss.onDeath.get(0).value());
        assertEquals("POISON:2", r.traps.get(0).effect);
        assertEquals(Trap.Type.POTION_ZONE, r.traps.get(0).type);
        assertEquals(new ItemRef(ItemRef.Source.MMOITEMS, "MISC:KEY", 1), r.puzzles.get(0).key);
        assertEquals("dialogue:gardien", r.npcs.get(0).onClick.get(0).value());
    }

    @Test
    void actionValuesMayContainColons() {
        var y = new YamlConfiguration();
        Action.saveList(y, "a", java.util.List.of(new Action(Action.Type.TYPEWRITER, "quest:intro:step1")));
        var loaded = Action.loadList(y, "a");
        assertEquals("quest:intro:step1", loaded.get(0).value());
    }

    @Test
    void unknownActionTypesAreIgnored() {
        var y = new YamlConfiguration();
        y.set("a", java.util.List.of("NOPE:x", "MESSAGE:ok", "garbage"));
        var loaded = Action.loadList(y, "a");
        assertEquals(1, loaded.size());
        assertEquals("ok", loaded.get(0).value());
    }

    @Test
    void itemActionParsing() {
        assertEquals(new ItemRef(ItemRef.Source.NEXO, "ruby_sword", 1), ActionRunner.parseItem("nexo ruby_sword"));
        assertEquals(new ItemRef(ItemRef.Source.MMOITEMS, "SWORD:CUTLASS", 3), ActionRunner.parseItem("MMOITEMS SWORD:CUTLASS 3"));
        assertNull(ActionRunner.parseItem("nexo"));
        assertNull(ActionRunner.parseItem("bogus x 1"));
        assertNull(ActionRunner.parseItem("vanilla DIAMOND many"));
    }

    @Test
    void problemsAreReported() {
        Dungeon d = new Dungeon("vide");
        assertEquals(2, d.problems().size());

        Room r = new Room();
        r.waves.add(new Wave());
        assertTrue(r.problems().stream().anyMatch(s -> s.contains("Aucune zone")));
        assertTrue(r.problems().stream().anyMatch(s -> s.contains("point d'apparition")));

        assertTrue(sample().problems().isEmpty());
    }

    @Test
    void cuboidVolumeAndValidIds() {
        assertEquals(27, new Cuboid("w", 0, 0, 0, 2, 2, 2).volume());
        assertTrue(DungeonStorage.validId("crypte_2-b"));
        assertFalse(DungeonStorage.validId("../etc"));
        assertFalse(DungeonStorage.validId(""));
    }
}
