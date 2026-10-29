package adventureexplorer.woodtick

import adventureexplorer.audio.imuse.ImuseSong
import adventureexplorer.scripting.ScriptManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Needs MONKEY2_TEST_PATH. Drives the real MI2 songs through the Woodtick transitions without audio output. */
class WoodtickTransitionsTest {
    private fun songs(): Map<Int, ImuseSong> {
        val path = System.getenv("MONKEY2_TEST_PATH")
        assumeTrue("Set MONKEY2_TEST_PATH to the game folder", path != null)
        val mgr = ScriptManager()
        try {
            mgr.detectGame(path!!)
            return Woodtick.songIds.associateWith { mgr.loadResource(path, "snd:MONKEY2:$it:ADL_")!!.imuse!! }
        } finally { mgr.close() }
    }

    private fun trip(vararg stops: String): Pair<WoodtickEngine, List<String>> {
        val engine = WoodtickEngine(songs())
        var t = 0.0
        val first = Woodtick.byKey[stops[0]]!!
        engine.post { Woodtick.enter(this, first) }
        engine.runOffline(3.0)
        for (i in 1 until stops.size) {
            val a = Woodtick.byKey[stops[i - 1]]!!
            val b = Woodtick.byKey[stops[i]]!!
            assertTrue("${a.key} → ${b.key} is an exit", b.key in a.exits)
            engine.post { Woodtick.go(this, a, b) }
            engine.runOffline(20.0)
        }
        return engine to engine.state.log
    }

    @Test fun `street to every neighbour and back ends the old song and starts the new one`() {
        for (room in listOf("cartograp", "bar", "inn", "laundry", "woodshop")) {
            val (engine, log) = trip("woodtick", room, "woodtick")
            println("== woodtick → $room → woodtick\n" + log.joinToString("\n"))
            assertEquals("back on the street song", listOf(101), engine.playingSounds())
            val dest = Woodtick.byKey[room]!!.song
            assertTrue("started $dest", log.any { it == "start sound $dest" })
            assertTrue("marker ending seen for 101", log.any { it.contains("marker 0 of sound 101") })
        }
    }

    @Test fun `bar to grill keeps the bar song and leaving through the bar returns to the street`() {
        val (engine, log) = trip("woodtick", "bar", "grill", "bar", "woodtick")
        println(log.joinToString("\n"))
        assertEquals(listOf(101), engine.playingSounds())
        assertTrue(log.any { it.contains("keeps playing sound 103") })
    }

    @Test fun `inn to Largo cross-fades`() {
        val (engine, log) = trip("woodtick", "inn", "largos", "inn")
        println(log.joinToString("\n"))
        assertEquals(listOf(152), engine.playingSounds())
        assertTrue(log.any { it.startsWith("fade out sound 152") })
        assertTrue(log.any { it.startsWith("fade out sound 182") })
    }

    /** The music must not drop out around a transition: longest silent stretch from the first marker to 4 s after the new song starts. */
    @Test fun `transitions have no audible gap`() {
        val songs = songs()
        for ((from, to) in listOf("woodtick" to "cartograp", "woodtick" to "inn", "bar" to "woodtick", "laundry" to "woodtick", "woodtick" to "woodshop")) {
            val engine = WoodtickEngine(songs)
            val a = Woodtick.byKey[from]!!
            val b = Woodtick.byKey[to]!!
            engine.post { Woodtick.enter(this, a) }
            engine.renderPeaks(6.0)
            engine.post { Woodtick.go(this, a, b) }
            val peaks = engine.renderPeaks(14.0)
            val started = engine.state.log.indexOfFirst { it.startsWith("start sound ${b.song}") && !it.contains("finishes") }
            assertTrue("$from → $to started the new song", started >= 0)
            var longest = 0; var run = 0
            for (p in peaks) { if (p < 300) { run++; longest = maxOf(longest, run) } else run = 0 }
            println("$from → $to: longest silence ${longest * 10} ms")
            assertTrue("$from → $to: silence of ${longest * 10} ms", longest <= 40)
        }
    }
}
