package adventureexplorer.woodtick

import adventureexplorer.audio.imuse.ImuseSong
import adventureexplorer.scripting.ScriptManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

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

    @Test fun `shops and Spitville keep the town song and switch its parts`() {
        BootyIsland.random = { 3 }
        val (engine, log) = walk("ville", "antique", "ville", "costume", "ville", "spitville", "ville", wait = 2.0)
        println(log.joinToString("\n"))
        assertEquals("never restarted", 1, log.count { it.startsWith("start sound 118") })
        assertEquals(listOf(118), engine.playingSounds())
        assertTrue(log.any { it.contains("parts → Antique shop (group 9): 2,7,9") })
        assertTrue(log.any { it.contains("parts → Costume shop (group 2): 1,2,3,4,6,9") })
        assertTrue(log.any { it.contains("parts → Spitville (group 5): 0,1,2,3,4,5,6,9") })
        assertTrue(log.any { it.contains("parts → Ville (group 3): 2,3,5,6,9") })
    }

    @Test fun `funeral parlor cross-fades in and the town song returns only after the funeral song finishes`() {
        val (engine, log) = walk("ville", "funeral", "ville", wait = 60.0)
        println(log.joinToString("\n"))
        assertTrue(log.any { it.startsWith("fade out sound 118") })
        assertTrue(log.any { it.startsWith("start sound 116") })
        assertTrue(log.any { it.startsWith("set hook 1 on sound 116") })
        val finished = log.indexOfFirst { it.startsWith("sound 116 finished") }
        val townAgain = log.indexOfLast { it.startsWith("start sound 118") }
        assertTrue("funeral song reached its ending", finished >= 0)
        assertTrue("town song started after the funeral song ended", townAgain > finished)
        assertEquals(listOf(118), engine.playingSounds())
    }
}
