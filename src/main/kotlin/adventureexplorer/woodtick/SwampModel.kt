// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 gsanmartin and contributors. Part of MI2 Music Explorer; see LICENSE and THIRD_PARTY.md.
package adventureexplorer.woodtick

/**
 * The Scabb Island swamp (room 20), read out of the game scripts with `tools/scumm5_disasm.py`.
 *
 * The swamp song (109, `V586`) starts with only parts 1 and 4 playing; the song's own setup data switches the
 * other parts off, and the scripts layer them in:
 *  - Local script 216 reads the ego's X position every frame: part 11 comes in at X 270, part 2 at X 470
 *    (`getActorX`, then `[267, song, part, on/off]`).
 *  - Local script 203 (stepping on the coffin) switches part 12 on, script 204 (back on the shore) off.
 *  - Room 21's entry script switches part 0, the lead voice, on as you enter the skull; its exit script off.
 *  - Going into the voodoo hut (room 21) does not fade the swamp song: the swamp's exit script only fades it when the
 *    next room is not 21. Inside the hut, local script 212 reads the ego's X every frame: past X 210 parts 0, 11, 12,
 *    9, 10 and 6 are all on; back at 210 or less, parts 9, 10 and 6 go off again.
 *
 * The coffin and the skull are events in the game, not positions; here they are thresholds along the walk:
 * the coffin at the sign ([COFFIN_X]) and the skull at its mouth ([SKULL_X], the skull object spans X 624-688).
 * The hut's picture is attached to the right of the swamp's, so the walk continues into it ([HUT_X] onwards).
 */
object Swamp : Scene {
    const val SONG = 109

    override val key = "swamp"
    override val title = "Swamp"
    override val rooms = listOf(
        Room("swamp", 20, "Swamp", SONG, emptyList(), "Swamp theme, more instruments as you walk right, on into the voodoo hut", appended = listOf(21)),
    )
    override val layout = mapOf("swamp" to (0.5f to 0.5f))

    const val COFFIN_X = 165
    private const val PART_11_X = 270
    private const val PART_2_X = 470
    private const val SKULL_X = 640
    private const val ROOM_WIDTH = 792
    /** Where the voodoo hut (room 21, 456 px wide) starts on the joined picture. */
    private const val HUT_X = ROOM_WIDTH
    private const val HUT_WIDTH = 456
    /** Script 212 compares the ego's X inside the hut with 210. */
    private const val HUT_BAND_X = HUT_X + 210

    /** One border along the walk: the parts it switches when crossed going right ([enter]) and back left ([leave]). */
    private class Border(
        val x: Int, val label: String, val note: String,
        val enter: List<Pair<Int, Boolean>>, val leave: List<Pair<Int, Boolean>>, val beyond: (Int) -> Boolean,
    ) {
        constructor(x: Int, part: Int, label: String, note: String, beyond: (Int) -> Boolean) :
            this(x, label, note, listOf(part to true), listOf(part to false), beyond)
        @Volatile var on = false
    }

    // Script 216 switches part 11 on from X 270 and part 2 on above X 470 (so it goes off again at 470 and below).
    private val borders = listOf(
        Border(COFFIN_X, 12, "Part 12 (coffin, from X $COFFIN_X)", "stepped on the coffin") { it >= COFFIN_X },
        Border(PART_11_X, 11, "Part 11 (from X $PART_11_X)", "past X $PART_11_X") { it >= PART_11_X },
        Border(PART_2_X, 2, "Part 2 (from X $PART_2_X)", "past X $PART_2_X") { it > PART_2_X },
        Border(SKULL_X, 0, "Part 0, the lead (skull, from X $SKULL_X)", "entered the skull") { it >= SKULL_X },
        // Script 212: past X 210 inside the hut all six parts are on; at 210 or less 9, 10 and 6 go off again.
        Border(
            HUT_BAND_X, "Parts 9, 10 and 6 (inside the hut, past its X 210)", "deep inside the hut",
            enter = listOf(0 to true, 11 to true, 12 to true, 9 to true, 10 to true, 6 to true),
            leave = listOf(0 to true, 11 to true, 12 to true, 9 to false, 10 to false, 6 to false),
        ) { it > HUT_BAND_X },
    )

    override val scrub = Scrub(
        width = ROOM_WIDTH + HUT_WIDTH,
        marks = listOf(
            COFFIN_X to "coffin · part 12",
            PART_11_X to "part 11",
            PART_2_X to "part 2",
            SKULL_X to "skull · part 0",
            HUT_X to "voodoo hut (same song)",
            HUT_BAND_X to "hut · parts 9, 10, 6",
        ),
        speed = 70,
        move = { e, from, to -> move(e, from, to) },
        layers = {
            listOf(Layer("Parts 1 and 4 (the song's base)", true)) + borders.map { Layer(it.label, it.on) }
        },
    )

    override fun enter(e: WoodtickEngine.Engine, room: Room) {
        e.stopAll()
        for (b in borders) b.on = false
        e.startSound(SONG)
        e.note("swamp: parts 1 and 4 play; the rest start switched off")
    }

    /** Never used: the swamp has a single room. */
    override fun go(e: WoodtickEngine.Engine, from: Room, to: Room) {}

    /** The character walks from [from] to [to]; every border on the way switches its part, in walking order. */
    fun move(e: WoodtickEngine.Engine, from: Int, to: Int) {
        val crossed = borders.filter { it.beyond(from) != it.beyond(to) }
        for (b in if (from <= to) crossed.sortedBy { it.x } else crossed.sortedByDescending { it.x }) {
            val now = b.beyond(to)
            if (b.on == now) continue
            b.on = now
            val changes = if (now) b.enter else b.leave
            for ((part, on) in changes) e.partOnOff(SONG, part, on)
            e.note("swamp ${if (now) b.note else "back before: ${b.note}"}: " + changes.joinToString(", ") { "part ${it.first} ${if (it.second) "on" else "off"}" })
        }
    }
}
