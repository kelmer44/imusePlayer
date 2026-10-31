// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 gsanmartin and contributors. Part of MI2 Music Explorer; see LICENSE and THIRD_PARTY.md.
package adventureexplorer.woodtick

import adventureexplorer.audio.imuse.ImuseSong
import adventureexplorer.scripting.ScriptManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/** Needs MONKEY2_TEST_PATH. The island map and the mansion entryway of the Booty Island scene, on the real songs. */
class MansionTest {
    private fun engine(): WoodtickEngine {
        val path = System.getenv("MONKEY2_TEST_PATH")
        assumeTrue("Set MONKEY2_TEST_PATH to the game folder", path != null)
        val mgr = ScriptManager()
        try {
            mgr.detectGame(path!!)
            val songs: Map<Int, ImuseSong> = BootyIsland.songIds.associateWith { mgr.loadResource(path, "snd:MONKEY2:$it:ADL_")!!.imuse!! }
            BootyIsland.rng = Random(3)
            BootyIsland.random = { 2 }
            return WoodtickEngine(songs)
        } finally { mgr.close() }
    }

    private fun room(k: String) = BootyIsland.byKey.getValue(k)

    private fun EngineRef.walk(vararg keys: String, seconds: Double = 2.0) {
        var from = room(keys[0])
        for (k in keys.drop(1)) {
            val a = from; val b = room(k)
            assertTrue("${a.key} → ${b.key} is an exit", b.key in a.exits)
            engine.post { BootyIsland.go(this, a, b) }
            engine.runOffline(seconds)
            from = b
        }
    }

    private class EngineRef(val engine: WoodtickEngine)

    @Test fun `the way to the entryway keeps the island song, then the mansion song hands over to the town song`() {
        val e = engine(); val r = EngineRef(e)
        e.post { BootyIsland.enter(this, room("ville")) }
        e.runOffline(3.0)
        r.walk("ville", "map", "grounds")
        assertEquals(listOf(115), e.playingSounds())
        r.walk("grounds", "entryway")
        assertEquals("115 stops, 114 starts", listOf(114), e.playingSounds())
        assertTrue(BootyIsland.intro)
        e.runOffline(400.0)
        assertFalse(BootyIsland.intro)
        val now = e.playingSounds()
        assertTrue("the town song took over: $now", now.isNotEmpty() && now.all { it == 118 || it == 117 })
        r.walk("entryway", "grounds", seconds = 3.0)
        assertTrue("the grounds are silent", e.playingSounds().isEmpty())
        r.walk("grounds", "map", seconds = 3.0)
        assertEquals(listOf(115), e.playingSounds())
    }

    @Test fun `after the hand-off the entryway evolves like a shop`() {
        val e = engine()
        e.post { BootyIsland.enter(this, room("entryway")) }
        e.runOffline(700.0)
        assertTrue(e.state.log.any { it.contains("instrument mix") })
    }

    @Test fun `the second visit to the entryway seeks into the song`() {
        val e = engine(); val r = EngineRef(e)
        e.post { BootyIsland.enter(this, room("entryway")) }
        e.runOffline(1.0)
        assertFalse(e.state.log.any { it.startsWith("script 120 seeks") })
        r.walk("entryway", "grounds", "entryway", seconds = 1.0)
        assertTrue(e.state.log.any { it.startsWith("script 120 seeks the mansion song to beat") })
    }

    @Test fun `clicking quickly along the way to the entryway leaves one song`() {
        val e = engine()
        e.post { BootyIsland.enter(this, room("ville")) }
        e.runOffline(2.0)
        var from = room("ville")
        for (k in listOf("map", "grounds", "entryway", "grounds", "map", "ville", "antique", "ville", "map", "grounds", "entryway")) {
            val a = from; val b = room(k)
            e.post { interrupt(BootyIsland.playing(a)); BootyIsland.go(this, a, b) }
            e.runOffline(0.3)
            from = b
        }
        e.runOffline(6.0)
        assertEquals(listOf(114), e.playingSounds())
    }

    @Test fun `a mix picked during the mansion song and the skip button carry over to the town song`() {
        val e = engine()
        e.post { BootyIsland.enter(this, room("entryway")) }
        e.runOffline(2.0)
        e.post { BootyIsland.chips.first { it.roomKey == "entryway" }.pick(this, 6) }   // mix 7
        e.post { BootyIsland.actions.first { it.roomKey == "entryway" && it.short == "Skip intro" }.run(this) }
        e.runOffline(20.0)
        assertFalse(BootyIsland.intro)
        assertEquals(7, BootyIsland.mix)
        assertTrue(e.state.log.any { it.startsWith("sound 118 parts → mix 7") })
    }
}
