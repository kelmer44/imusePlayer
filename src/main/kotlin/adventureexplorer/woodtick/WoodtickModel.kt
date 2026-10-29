// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 gsanmartin and contributors. Part of MI2 Music Explorer; see LICENSE and THIRD_PARTY.md.
package adventureexplorer.woodtick

/**
 * Woodtick (Monkey Island 2) as the game's scripts drive its music.
 *
 * Everything here was read out of the game data with `tools/scumm5_disasm.py`:
 * - Each room's song is a sound resource number (the `startSound` calls in the room scripts).
 * - Global script 96 is called as `96(fromRoom, toRoom)` whenever the player walks between
 *   Woodtick rooms. It queues two marker triggers on the song being left and then the
 *   destination song start; see [Woodtick.go].
 * - Laundry local script 225 is the "talk to the characters" layer (parts 3, 9 and 10 fade in).
 */
object Woodtick : Scene {
    override val key = "woodtick"
    override val title = "Woodtick"
    // Songs: 101 street, 103 bar & grill, 104 cartographer, 110 laundry, 111 woodshop, 152 inn, 182 Largo's room.
    override val rooms = listOf(
        Room("woodtick", 7, "Woodtick", 101, listOf("cartograp", "bar", "inn", "laundry", "woodshop"), "Street theme"),
        Room("cartograp", 8, "Cartographer", 104, listOf("woodtick"), "Cartographer theme"),
        Room("bar", 9, "Bar", 103, listOf("woodtick", "grill"), "Bar theme"),
        Room("grill", 11, "Grill", 103, listOf("bar"), "Keeps the bar theme"),
        Room("inn", 12, "Inn", 152, listOf("woodtick", "largos"), "Inn theme"),
        Room("largos", 13, "Largo's room", 182, listOf("inn"), "Largo's room theme"),
        Room("laundry", 14, "Laundry", 110, listOf("woodtick"), "Laundry theme; talking adds an instrument layer"),
        Room("woodshop", 15, "Woodshop", 111, listOf("woodtick"), "Woodshop theme"),
    )

    override val layout = mapOf(
        "woodtick" to (0.50f to 0.46f),
        "cartograp" to (0.20f to 0.16f),
        "woodshop" to (0.80f to 0.16f),
        "laundry" to (0.13f to 0.60f),
        "bar" to (0.87f to 0.52f),
        "grill" to (0.87f to 0.86f),
        "inn" to (0.50f to 0.86f),
        "largos" to (0.20f to 0.86f),
    )

    override val toggles = listOf(
        Toggle(
            "laundry", "Talk to Men of Low Moral Fiber", "Stop talking to Men of Low Moral Fiber",
            "Adds the conversation layer: parts 3, 9 and 10 fade in while part 1 drops, as in the game's laundry script.",
            start = { talkStart(it) }, stop = { talkEnd(it) },
        ),
    )

    /** Songs that end by arming jump hook 1: they hit marker 0 every 8 beats, then take the hook to an ending track. */
    private val hookEndSongs = setOf(103, 104, 110, 111, 152)

    private const val STREET = 101
    /** The street song's ending: track 5 beat 4.400, found in global script 96 as `V100 = 4 + 1`. */
    private const val STREET_ENDING_TRACK = 5
    /** Jump hook 7 of the street song: the arrival section script 96 arms when you come back to the street. */
    private const val STREET_ARRIVAL_HOOK = 7

    /** The first thing that plays when the player picks a starting room. */
    override fun enter(e: WoodtickEngine.Engine, room: Room) {
        e.stopAll()
        e.startSound(room.song, hook = if (room.song == STREET) STREET_ARRIVAL_HOOK else 0)
    }

    /**
     * Script 96, `from` → `to`: let the song being left reach its ending, then start the next song.
     *
     * Song being left (marker 0 trigger):
     *  - street (101): marker 0 comes every 4 beats; jump to the ending (track 5, beat 4.400).
     *  - bar, cartographer, laundry, woodshop, inn: marker 0 comes every 8 beats right before jump hook 1,
     *    which leads to the ending track; the trigger arms hook 1.
     * Marker 1 (inside every ending track): start the new song while the old one plays out its ending.
     * The new song starts plain, except the street, which starts with hook 7 armed.
     *
     * Inn and Largo's room cross-fade instead (room 13 scripts: 60-tick volume fade on the old song).
     */
    override fun go(e: WoodtickEngine.Engine, from: Room, to: Room) {
        if (from.song == to.song) { e.note("${to.title}: keeps playing sound ${to.song}"); return }
        e.clearTriggers()
        val old = from.song
        val next = to.song
        val arrivalHook = if (next == STREET) STREET_ARRIVAL_HOOK else 0

        if (!e.isPlaying(old)) {
            // Script 96 for the bar: when the bar song is not running, the street song just starts.
            e.startSound(next, hook = arrivalHook)
            return
        }
        if (old == 182 || (old == 152 && next == 182)) {
            e.fadeOutAndStop(old, 1000)
            e.startSound(next, hook = arrivalHook)
            return
        }
        when {
            old == STREET -> e.onMarker(old, 0, "jump to the ending (track $STREET_ENDING_TRACK, beat 4.400)") {
                jump(old, STREET_ENDING_TRACK, 4, 400)
            }
            old in hookEndSongs -> e.onMarker(old, 0, "arm jump hook 1 (to the ending)") { setHook(old, 1) }
            else -> { e.stopSound(old); e.startSound(next, hook = arrivalHook); return }
        }
        // The game's queued commands here are `9 T` and `8 T` (stop and start the NEW song T). The old song is
        // left alone: it plays out the rest of its ending track while the new song starts underneath it.
        e.onMarker(old, 1, "start sound $next" + (if (arrivalHook != 0) " with hook $arrivalHook" else "") + " (sound $old finishes its ending)") {
            startSound(next, hook = arrivalHook)
        }
        e.note("${from.title} → ${to.title}: waiting for the ${if (old == STREET) "next marker" else "next hook point"} in sound $old")
    }

    // ── Laundry: "talk to the characters" (local script 225 of room 14) ─────────────────────

    const val LAUNDRY_SONG = 110
    private val talkParts = intArrayOf(3, 9, 10)
    /** One script frame is 4 jiffies of 1/60 s. */
    private const val FRAME_MS = 4000.0 / 60

    /** Script 225(1): turn the layer on and raise parts 3/9/10 over 127 frames while part 1 sinks to 84. */
    fun talkStart(e: WoodtickEngine.Engine) {
        if (!e.isPlaying(LAUNDRY_SONG)) return
        for (p in talkParts) { e.partOnOff(LAUNDRY_SONG, p, true); e.partVolume(LAUNDRY_SONG, p, 0) }
        e.note("talking: parts 3, 9, 10 fade in")
        e.animate((127 * FRAME_MS).toInt()) { t ->
            val l5 = (t * 127).toInt()
            for (p in talkParts) e.partVolume(LAUNDRY_SONG, p, l5)
            e.partVolume(LAUNDRY_SONG, 1, 127 - l5 / 3)
        }
    }

    /** Script 225(2): lower the layer two steps per frame, restore part 1, then switch the parts off. */
    fun talkEnd(e: WoodtickEngine.Engine) {
        if (!e.isPlaying(LAUNDRY_SONG)) return
        e.note("talking over: parts 3, 9, 10 fade out")
        e.animate((64 * FRAME_MS).toInt()) { t ->
            val l5 = (127 - t * 127).toInt().coerceAtLeast(0)
            for (p in talkParts) e.partVolume(LAUNDRY_SONG, p, l5)
            e.partVolume(LAUNDRY_SONG, 1, minOf(127, 84 + (127 - l5)))
            if (t >= 1.0) {
                for (p in talkParts) e.partOnOff(LAUNDRY_SONG, p, false)
                e.partVolume(LAUNDRY_SONG, 1, 127)
            }
        }
    }
}
