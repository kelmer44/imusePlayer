// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 gsanmartin and contributors. Part of MI2 Music Explorer; see LICENSE and THIRD_PARTY.md.
package adventureexplorer.woodtick

import adventureexplorer.audio.imuse.ImuseSong
import adventureexplorer.scripting.ScriptManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/** Needs MONKEY2_TEST_PATH. Drives the real Booty Island songs through the scene's transitions without audio. */
class BootyTransitionsTest {
    private fun songs(): Map<Int, ImuseSong> {
        val path = System.getenv("MONKEY2_TEST_PATH")
        assumeTrue("Set MONKEY2_TEST_PATH to the game folder", path != null)
        val mgr = ScriptManager()
        try {
            mgr.detectGame(path!!)
            return BootyIsland.songIds.associateWith { mgr.loadResource(path, "snd:MONKEY2:$it:ADL_")!!.imuse!! }
        } finally { mgr.close() }
    }

    private fun walk(vararg stops: String, wait: Double = 25.0): Pair<WoodtickEngine, List<String>> {
        BootyIsland.rng = Random(1)
        BootyIsland.random = { 3 }
        val engine = WoodtickEngine(songs())
        engine.post { BootyIsland.enter(this, BootyIsland.byKey[stops[0]]!!) }
        engine.runOffline(3.0)
        for (i in 1 until stops.size) {
            val a = BootyIsland.byKey[stops[i - 1]]!!
            val b = BootyIsland.byKey[stops[i]]!!
            assertTrue("${a.key} → ${b.key} is an exit", b.key in a.exits)
            engine.post { BootyIsland.go(this, a, b) }
            engine.runOffline(wait)
        }
        return engine to engine.state.log
    }

    @Test fun `clicking through rooms faster than the transitions leaves only the last room's song`() {
        BootyIsland.rng = Random(1)
        BootyIsland.random = { 3 }
        val engine = WoodtickEngine(songs())
        val ville = BootyIsland.byKey["ville"]!!
        engine.post { BootyIsland.enter(this, ville) }
        engine.runOffline(3.0)
        var from = ville
        for (key in listOf("antique", "ville", "spitville", "ville", "costume", "ville", "funeral", "ville", "antique", "spitville")) {
            val to = BootyIsland.byKey[key]!!
            val a = from
            engine.post { interrupt(BootyIsland.playing(a)); BootyIsland.go(this, a, to) }
            engine.runOffline(0.2)
            from = to
        }
        engine.runOffline(8.0)
        assertEquals(listOf(118), engine.playingSounds())
    }

    @Test fun `the ville plays the island song and nothing in it changes by itself`() {
        val (engine, log) = walk("ville", wait = 1.0)
        engine.runOffline(120.0)
        assertEquals(listOf(115), engine.playingSounds())
        assertEquals("only the start", listOf("stop all sounds", "start sound 115"), engine.state.log)
        assertTrue(engine.state.triggers.isEmpty())
    }

    @Test fun `leaving the ville for a shop cues the island ending while it fades and the town song starts with its mix`() {
        val (engine, log) = walk("ville", "antique", wait = 6.0)
        println(log.joinToString("\n"))
        assertTrue(log.any { it.startsWith("set hook 1 on sound 115") })
        assertTrue(log.any { it.startsWith("fade out sound 115 over 1500 ms") })
        assertTrue("town song skips its intro with hook 2", log.any { it == "start sound 118 (hook 2 armed)" })
        assertTrue(log.any { it.contains("parts → mix 9: 2,7,9") })
        assertEquals("115 has faded out and stopped", listOf(118), engine.playingSounds())
    }

    @Test fun `Spitville and the costume shop use their own mixes, and walking back fades the town song and restarts the island song`() {
        val (e1, log1) = walk("ville", "spitville", "ville", wait = 6.0)
        assertTrue(log1.any { it.contains("parts → mix 5: 0,1,2,3,4,5,6,9") })
        assertTrue("no hook for Spitville", log1.none { it.startsWith("set hook 1 on sound 115") })
        assertEquals(listOf(115), e1.playingSounds())
        assertEquals("115 started twice", 2, log1.count { it == "start sound 115" })

        val (_, log2) = walk("ville", "costume", wait = 6.0)
        assertTrue(log2.any { it.contains("parts → mix 2: 1,2,3,4,6,9") })
    }

    @Test fun `funeral parlor cross-fades in and the island song returns only after the funeral song finishes`() {
        val (engine, log) = walk("ville", "funeral", "ville", wait = 60.0)
        println(log.joinToString("\n"))
        assertTrue(log.any { it.startsWith("fade out sound 115") })
        assertTrue(log.any { it.startsWith("start sound 116") })
        assertTrue(log.any { it.startsWith("set hook 1 on sound 116") })
        val finished = log.indexOfFirst { it.startsWith("sound 116 finished") }
        val back = log.indexOfLast { it.startsWith("start sound 115") }
        assertTrue("funeral song reached its ending", finished >= 0)
        assertTrue("island song started after the funeral song ended", back > finished)
        assertEquals(listOf(115), engine.playingSounds())
    }
}
