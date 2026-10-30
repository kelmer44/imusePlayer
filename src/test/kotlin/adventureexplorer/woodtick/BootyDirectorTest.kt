// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 gsanmartin and contributors. Part of MI2 Music Explorer; see LICENSE and THIRD_PARTY.md.
package adventureexplorer.woodtick

import adventureexplorer.audio.imuse.ImuseSong
import adventureexplorer.scripting.ScriptManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/** Needs MONKEY2_TEST_PATH. Runs script 120's town director on the real songs, without audio. */
class BootyDirectorTest {
    private val ville = BootyIsland.byKey.getValue("ville")
    private val antique = BootyIsland.byKey.getValue("antique")
    private val costume = BootyIsland.byKey.getValue("costume")
    private val spit = BootyIsland.byKey.getValue("spitville")

    private fun songs(): Map<Int, ImuseSong> {
        val path = System.getenv("MONKEY2_TEST_PATH")
        assumeTrue("Set MONKEY2_TEST_PATH to the game folder", path != null)
        val mgr = ScriptManager()
        try {
            mgr.detectGame(path!!)
            return BootyIsland.songIds.associateWith { mgr.loadResource(path, "snd:MONKEY2:$it:ADL_")!!.imuse!! }
        } finally { mgr.close() }
    }

    private fun engine(room: Room = costume, seed: Int = 7): WoodtickEngine {
        BootyIsland.random = { 3 }
        BootyIsland.rng = Random(seed)
        val e = WoodtickEngine(songs())
        e.post { BootyIsland.enter(this, room) }
        return e
    }

    private fun mixesIn(log: List<String>) = log.filter { it.startsWith("sound 118 parts → mix ") }.map { it.substringAfter("→ mix ").substringBefore(":").toInt() }
    private fun action(room: String, label: String) = BootyIsland.actions.first { it.roomKey == room && it.label == label }
    private fun toggle(room: String, label: String) = BootyIsland.toggles.first { it.roomKey == room && it.startLabel == label }

    @Test fun `a shop evolves by itself with two mix changes at loop ends and then the alternate song, which hands back`() {
        val e = engine(costume)
        e.runOffline(330.0)
        val log = e.state.log
        println(log.filter { it.contains("mix") || it.contains("sound 117") || it.contains("main song") || it.startsWith("start") }.joinToString("\n"))
        val mixes = mixesIn(log)
        assertEquals("costume shop's own mix first", 2, mixes.first())
        assertTrue("walk steps happened: $mixes", log.count { it.contains("instrument mix") } >= 2)
        val firstSwap = log.indexOfFirst { it.startsWith("start sound 117") }
        assertTrue("alternate song took over", firstSwap > 0)
        assertEquals("exactly two walk steps before the first swap", 2, log.take(firstSwap).count { it.contains("instrument mix") })
        assertTrue("and handed back with a fresh 118", log.drop(firstSwap).any { it.startsWith("start sound 118") })
        assertTrue("the fresh 118 rolls mix 1-3", log.drop(firstSwap).any { it.contains("parts → mix 3:") })
    }

    @Test fun `the walk only goes where the current mix's row points`() {
        val rows = listOf(
            "BBBBCCCCFFZZZEEEI", "AAAACCCCEEEFFIZZZ", "AAAABBBBEEEEFFIZZZ", "CCCCFFZZZEEEI", "CCFFFZZZZIII",
            "GGGGABCEIZ", "HHHGGGG", "SSSRRRR", "JAAAABBBBCCCCDDDDEEEE", "HHHHSSS",
        )
        for (seed in 1..4) {
            val e = engine(antique, seed)
            e.runOffline(200.0)
            // A `Z` in a row starts the fast shuffle (script 122), which is not a walk step: check only up to there.
            val log = e.state.log
            val shuffleAt = log.indexOfFirst { it.startsWith("fast shuffle") }.let { if (it < 0) log.size else it }
            val mixes = mixesIn(log.take(shuffleAt))
            for ((a, b) in mixes.zipWithNext()) {
                // after a swap a fresh song rolls mix 3, which is not a walk step; every other pair is
                val ok = rows[a - 1].any { it in 'A'..'J' && it - 'A' + 1 == b }
                assertTrue("seed $seed: $a → $b is not in row $a (${rows[a - 1]}); mixes $mixes", ok || b == 3)
            }
        }
    }

    @Test fun `Spitville applies its mix and then waits with no walk and no swap`() {
        val e = engine(spit)
        e.runOffline(200.0)
        assertEquals(listOf(5), mixesIn(e.state.log))
        assertFalse(e.state.log.any { it.startsWith("start sound 117") })
        assertTrue(e.state.triggers.isEmpty())
    }

    @Test fun `with auto switched off nothing changes by itself`() {
        val e = engine(costume)
        e.runOffline(2.0)
        e.post { toggle("costume", "Let the music evolve by itself").stop(this) }
        e.runOffline(200.0)
        val log = e.state.log
        assertFalse(log.any { it.startsWith("start sound 117") })
        assertEquals("only the first mix", 1, mixesIn(log).size)
        assertTrue("no walk step waiting", e.state.triggers.isEmpty())
    }

    @Test fun `the mix chips and the next-mix button change the mix at once`() {
        val e = engine(antique)
        e.runOffline(2.0)
        e.post { BootyIsland.chips.first { it.roomKey == "antique" }.pick(this, 5) }; e.runOffline(0.3)   // mix 6
        assertEquals(6, BootyIsland.mix)
        assertTrue(e.state.log.any { it.contains("parts → mix 6: 2,3,6,9") })
        e.post { action("antique", "Next mix now").run(this) }; e.runOffline(0.3)
        assertTrue("the walk moved on to a letter of row 6 (GGGGABCEIZ): ${BootyIsland.mix}", "GGGGABCEI".any { it - 'A' + 1 == BootyIsland.mix })
    }

    @Test fun `the swap button waits for the loop end and the alternate song hands back`() {
        val e = engine(antique)
        e.runOffline(2.0)
        e.post { toggle("antique", "Let the music evolve by itself").stop(this) }
        e.post { action("antique", "Swap to the alternate song").run(this) }; e.runOffline(0.5)
        assertEquals("not yet: it waits for marker 2", listOf(118), e.playingSounds())
        assertTrue(e.state.triggers.any { it.contains("alternate song 117") })
        e.runOffline(120.0)
        val log = e.state.log
        assertTrue(log.any { it.startsWith("start sound 117") })
        assertTrue(log.any { it.contains("back to the main song") })
        assertEquals(listOf(118), e.playingSounds())
    }

    @Test fun `the fast shuffle steps through mixes 1 to 5 every six seconds`() {
        val e = engine(costume)
        e.runOffline(2.0)
        e.post { toggle("costume", "Fast shuffle").start(this) }
        e.runOffline(32.0)
        val shuffled = mixesIn(e.state.log).drop(1)
        println("shuffle: $shuffled")
        assertTrue("about one per 6 s: $shuffled", shuffled.size in 5..7)
        assertTrue(shuffled.all { it in 1..5 })
        assertEquals("no repeats within a cycle of five", shuffled.take(5).toSet().size, shuffled.take(5).size)
        assertTrue(BootyIsland.shuffling)
        e.post { toggle("costume", "Fast shuffle").stop(this) }; e.runOffline(1.0)
        assertFalse(BootyIsland.shuffling)
    }

    @Test fun `walking back to the ville during the alternate song leaves only the island song`() {
        val e = engine(costume)
        e.runOffline(2.0)
        var t = 0.0
        while (BootyIsland.townSong != 117 && t < 300) { e.runOffline(1.0); t += 1.0 }
        assertEquals(117, BootyIsland.townSong)
        e.post { BootyIsland.go(this, costume, ville) }
        e.runOffline(8.0)
        assertEquals(listOf(115), e.playingSounds())
    }
}
