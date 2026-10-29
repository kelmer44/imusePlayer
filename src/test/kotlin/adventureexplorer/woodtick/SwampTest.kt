// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 gsanmartin and contributors. Part of MI2 Music Explorer; see LICENSE and THIRD_PARTY.md.
package adventureexplorer.woodtick

import adventureexplorer.audio.imuse.ImuseSong
import adventureexplorer.scripting.ScriptManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Needs MONKEY2_TEST_PATH. */
class SwampTest {
    private fun song(): ImuseSong {
        val path = System.getenv("MONKEY2_TEST_PATH")
        assumeTrue("Set MONKEY2_TEST_PATH to the game folder", path != null)
        val mgr = ScriptManager()
        try {
            mgr.detectGame(path!!)
            return mgr.loadResource(path, "snd:MONKEY2:109:ADL_")!!.imuse!!
        } finally { mgr.close() }
    }

    private fun engine(song: ImuseSong) = WoodtickEngine(mapOf(109 to song)).also { e ->
        e.post { Swamp.enter(this, Swamp.rooms.first()) }
    }

    private fun on() = Swamp.scrub.layers().map { it.on }

    @Test fun `walking right adds the coffin, part 11, part 2, the skull and then the hut band, and walking back removes them`() {
        val e = engine(song())
        e.runOffline(1.0)
        // layers: base, coffin part 12, part 11, part 2, skull part 0, hut parts 9/10/6
        assertEquals(listOf(true, false, false, false, false, false), on())
        e.post { Swamp.move(this, 0, 200) }; e.runOffline(0.5)
        assertEquals("coffin (at the sign)", listOf(true, true, false, false, false, false), on())
        e.post { Swamp.move(this, 200, 300) }; e.runOffline(0.5)
        assertEquals(listOf(true, true, true, false, false, false), on())
        e.post { Swamp.move(this, 300, 600) }; e.runOffline(0.5)
        assertEquals(listOf(true, true, true, true, false, false), on())
        e.post { Swamp.move(this, 600, 700) }; e.runOffline(0.5)
        assertEquals("skull", listOf(true, true, true, true, true, false), on())
        e.post { Swamp.move(this, 700, 900) }; e.runOffline(0.5)
        assertEquals("inside the hut, before its X 210", listOf(true, true, true, true, true, false), on())
        e.post { Swamp.move(this, 900, 1100) }; e.runOffline(0.5)
        assertEquals("inside the hut, past its X 210", listOf(true, true, true, true, true, true), on())
        e.post { Swamp.move(this, 1100, 950) }; e.runOffline(0.5)
        assertEquals("back at the hut entrance: 9, 10, 6 off, the rest stays", listOf(true, true, true, true, true, false), on())
        e.post { Swamp.move(this, 950, 400) }; e.runOffline(0.5)
        assertEquals("out of the skull and below 470", listOf(true, true, true, false, false, false), on())
        e.post { Swamp.move(this, 400, 50) }; e.runOffline(0.5)
        assertEquals(listOf(true, false, false, false, false, false), on())
        println(e.state.log.joinToString("\n"))
    }

    @Test fun `jumping straight across switches everything on the way`() {
        val e = engine(song())
        e.runOffline(1.0)
        e.post { Swamp.move(this, 0, 1200) }; e.runOffline(0.5)
        assertEquals(listOf(true, true, true, true, true, true), on())
    }

    @Test fun `the swamp gets fuller as you walk right`() {
        val song = song()
        fun level(to: Int): Double {
            val e = engine(song)
            e.post { Swamp.move(this, 0, to) }
            return e.renderRms(70.0, 10000).average()
        }
        val left = level(100); val coffin = level(200); val mid = level(400); val right = level(560); val skull = level(750); val hut = level(1150)
        println("swamp mean level: left=${left.toInt()} coffin=${coffin.toInt()} middle=${mid.toInt()} right=${right.toInt()} skull=${skull.toInt()} hut=${hut.toInt()}")
        assertTrue("the hut band (parts 9, 10, 6) fills it further", hut > skull * 1.02)
        assertTrue("the skull adds the lead voice: clearly the fullest", skull > right * 1.05)
        assertTrue("part 11 is a very quiet layer: middle stays within 1% of left", mid >= left * 0.99)
        assertTrue("right clearly fuller than left (part 2 adds notes)", right > left * 1.03)
    }
}
