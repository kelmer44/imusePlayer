// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 gsanmartin and contributors. Part of MI2 Music Explorer; see LICENSE and THIRD_PARTY.md.
package adventureexplorer.woodtick

import kotlin.random.Random

/**
 * Booty Island's town (Monkey Island 2), read out of the game scripts with `tools/scumm5_disasm.py`.
 *
 * - **The ville plays song 115**, the island theme, which carries on from the island map. Nothing the player does in the
 *   ville changes it. Walking into a shop or Spitville ends it (jump hook 1 cues its ending while it fades) and the
 *   **town song 118** takes over; walking back out fades the town song and starts 115 again (script 117,
 *   `117(fromRoom, toRoom)`).
 * - **The town song** is what plays in the shops and Spitville. The game only changes which instrument parts play:
 *   script 120 reads a string of `1`/`0` characters, 9 per "mix" (`(mix - 1) * 9`): characters 0-7 are parts 0-7 and
 *   character 8 is part 9. Spitville uses mix 5, the antique shop 9, the costume shop 2.
 * - **Script 120 also evolves the town song by itself** while you are in a shop (in Spitville it only waits): the first two
 *   times the song reaches its loop end it applies the next mix, chosen by a random walk over the ten mixes (each mix
 *   has a row of letters, one per possible next mix; `Z` starts the fast shuffle of script 122, `S` forgets which mixes
 *   were visited); the third time it swaps to the alternate song 117 (not while the mix is 9 or 10), which hands back to
 *   118 at its own marker 3. Then the count starts again.
 * - The funeral parlor has its own song (116). Entering fades the music for a second while 116 starts. Leaving arms jump
 *   hook 1 on 116 and waits for the song to finish before the next one starts.
 *
 * The player offers buttons for what the game does by chance in the shops: the next mix now, a chosen mix, the song
 * swap, the fast shuffle, and a switch for the automatic evolution.
 */
object BootyIsland : Scene {
    private const val ISLAND = 115
    private const val TOWN = 118
    /** The alternate town song: plays instead of 118 for one pass now and then. */
    private const val ALT = 117
    private const val FUNERAL = 116
    /** The mansion's own song: a long lead-in to the town song (script 120 hands over to 118 at its marker 4). */
    internal const val MANSION = 114

    override val key = "booty"
    override val title = "Booty Island"

    override val rooms = listOf(
        Room("ville", 46, "Ville", ISLAND, listOf("antique", "costume", "funeral", "spitville", "map"), "Island theme (song 115)"),
        Room("spitville", 47, "Spitville", TOWN, listOf("ville"), "Town theme, Spitville mix"),
        Room("antique", 48, "Antique shop", TOWN, listOf("ville"), "Town theme, evolving by itself"),
        Room("costume", 49, "Costume shop", TOWN, listOf("ville"), "Town theme, evolving by itself"),
        Room("funeral", 50, "Funeral parlor", FUNERAL, listOf("ville"), "Funeral parlor theme"),
        Room("map", 45, "Island map", ISLAND, listOf("ville", "grounds"), "Island theme (song 115)"),
        Room("grounds", 53, "Mansion grounds", ISLAND, listOf("map", "entryway"), "Island theme if it was playing, else silence"),
        Room("entryway", 55, "Mansion entryway", MANSION, listOf("grounds"), "Mansion song (114), then the town song"),
    )

    override val layout = mapOf(
        "ville" to (0.50f to 0.38f),
        "antique" to (0.14f to 0.14f),
        "costume" to (0.86f to 0.14f),
        "funeral" to (0.13f to 0.55f),
        "spitville" to (0.87f to 0.55f),
        "map" to (0.28f to 0.85f),
        "grounds" to (0.55f to 0.85f),
        "entryway" to (0.82f to 0.85f),
    )

    /** The rooms' own songs plus the alternate town song, which no room names but the director swaps in. */
    override val songIds: List<Int> get() = rooms.map { it.song }.distinct() + ALT

    /** The game's mix string, 9 characters per mix: parts 0-7, then part 9. */
    private const val MIXES = "111100101011110101001101101111110101111111101001100101001100000000100000001000011000000010"
    private fun parts(n: Int): Set<Int> {
        val m = MIXES.substring((n - 1) * 9, (n - 1) * 9 + 9)
        return buildSet { for (i in 0..7) if (m[i] == '1') add(i); if (m[8] == '1') add(9) }
    }

    /** Script 120's random-walk rows: from mix n, one random letter of row n names the next (`A` = mix 1 ... `J` = mix 10). */
    private val rows = listOf(
        "BBBBCCCCFFZZZEEEI", "AAAACCCCEEEFFIZZZ", "AAAABBBBEEEEFFIZZZ", "CCCCFFZZZEEEI", "CCFFFZZZZIII",
        "GGGGABCEIZ", "HHHGGGG", "SSSRRRR", "JAAAABBBBCCCCDDDDEEEE", "HHHHSSS",
    )

    private val roomMixes = mapOf("spitville" to 5, "antique" to 9, "costume" to 2)
    private val shops = setOf("antique", "costume")
    /** Rooms where script 120 runs the walk and the song swap: the shops, and the mansion once its own song has handed over. */
    private val directed = shops + "entryway"

    /** The fast shuffle (script 122) steps every 360 jiffies, 6 s, through mixes 1-5. */
    private const val SHUFFLE_MS = 6000
    /** Script 117 starts the town song a frame after it cues the island song's ending (a frame is 4/60 s). */
    private const val HANDOVER_MS = 70
    private const val FADE_TO_TOWN_MS = 1500   // 90 ticks (shops)
    private const val FADE_MS = 1000           // 60 ticks

    // ── Director state (written on the audio thread, read by the UI) ──────────────────────

    /** The mix the town song is playing now (1-10). */
    @Volatile var mix = 1; private set
    /** The town song that is playing now: [TOWN] or [ALT]. */
    @Volatile var townSong = TOWN; private set
    /** Script 120's random walk runs (the game always has it on). */
    @Volatile var auto = true; private set
    /** Script 122's fast shuffle is running. */
    @Volatile var shuffling = false; private set
    @Volatile internal var here: Room = rooms[0]; private set
    /** The mansion song 114 is still playing; the town song takes over at its end. */
    @Volatile var intro = false; private set
    /** A mix picked while the mansion song plays; the town song starts with it. */
    private var pendingMix = 0
    /** How many loop ends the walk has applied since the town song (re)started; the third is the swap (`L3`). */
    @Volatile private var walks = 0
    private val visited = BooleanArray(11)
    private val shuffleUsed = BooleanArray(6)

    /** Rolls the mix a restarted town song begins with (`getRandomNr(2) + 1`, so 1-3); replaceable in tests. */
    internal var random: () -> Int = { Random.nextInt(1, 4) }
    internal var rng: Random = Random.Default

    internal val inShop get() = here.key in directed
    internal val inTown get() = here.song == TOWN || here.key == "entryway"

    // ── Controls (the town song's rooms) ──────────────────────────────────────────────────

    private val townRooms = listOf("antique", "costume", "spitville", "entryway")

    internal fun autoToggle(room: String) = Toggle(
        room, "Let the music evolve by itself", "Stop the music evolving",
        "Script 120's random walk, which the game always runs in the shops.",
        start = { auto = true; arm(it) }, stop = { auto = false; if (!intro) it.clearTriggers() },
        shortOff = "Auto", shortOn = "Auto ✓", isOn = { auto },
    )

    internal fun shuffleToggle(room: String) = Toggle(
        room, "Fast shuffle", "Stop the fast shuffle",
        "Script 122: a random mix from 1 to 5 every 6 seconds.",
        start = { startShuffle(it) }, stop = { shuffling = false; arm(it) },
        shortOff = "Shuffle", shortOn = "Shuffling…", isOn = { shuffling },
    )

    internal fun mixActions(room: String) = listOf(
        Action(
            room, "Next mix now", "The next step of the random walk, without waiting for the loop end.", "Next mix",
        ) { e -> nextWalkStep(allowShuffle = false)?.let { applyMix(e, it) } },
        Action(
            room, "Swap to the alternate song", "Song 117 takes over at the next loop end (the game does it every third).",
            "Swap song",
        ) { e -> if (townSong == TOWN) swapToAlt(e, button = true) else e.note("already in the alternate song; it hands back by itself") },
    )

    internal fun mixChips(room: String) = Chips(
        room, "Instrument mix", "The ten arrangements script 120 walks between.",
        options = (1..10).map { it.toString() },
        selected = { mix },
        pick = { e, i -> visited[i + 1] = true; applyMix(e, i + 1) },
    )

    override val toggles = directed.map { autoToggle(it) } + townRooms.map { shuffleToggle(it) }
    override val actions = townRooms.flatMap { mixActions(it) } + Action(
        "entryway", "Skip to the town song", "Jump the mansion song to its last bars, where script 120 hands over to the town song.",
        "Skip intro",
    ) { e -> if (intro) e.jump(MANSION, 0, 288, 0) else e.note("the town song is already playing") }
    override val chips = townRooms.map { mixChips(it) }

    // ── Starting a song ───────────────────────────────────────────────────────────────────

    override fun enter(e: WoodtickEngine.Engine, room: Room) {
        e.stopAll()
        // A fresh start is the game's own state: script 120 runs, the fast shuffle does not.
        auto = true
        shuffling = false
        intro = false
        mansionHeard = false
        begin(e, room, skipIntro = false)
    }

    private fun begin(e: WoodtickEngine.Engine, room: Room, skipIntro: Boolean) {
        here = room
        when (room.song) {
            MANSION -> startMansion(e, room)
            FUNERAL -> e.startSound(FUNERAL)   // script 117 switches part 9 off only when story flag bit 90 is set; it is not
            ISLAND -> e.startSound(ISLAND)
            else -> startTown(e, room, skipIntro)
        }
    }

    /** A fresh town song for [room]: its mix goes on once the song has settled, and the director arms itself in the shops. */
    private fun startTown(e: WoodtickEngine.Engine, room: Room, skipIntro: Boolean) {
        townSong = TOWN
        walks = 0
        visited.fill(false); visited[1] = true   // script 120 starts with bit 693 (mix 1) set
        // Hook 2 of song 118 skips its intro: script 117 arms it in the same frame for the shops.
        e.startSound(TOWN, hook = if (skipIntro) 2 else 0)
        val n = roomMixes.getValue(room.key)
        e.after(MIX_DELAY_MS) { applyMix(this, n) }
        if (room.key in shops) arm(e)
    }

    private const val MIX_DELAY_MS = 200

    private fun applyMix(e: WoodtickEngine.Engine, n: Int) {
        if (intro) {
            // The mansion song has no mixes: remember the choice for the town song that takes over at its end.
            mix = n; pendingMix = n
            return e.note("mix $n chosen; the mansion song is still playing, the town song starts with it at its end")
        }
        mix = n
        e.applyParts(townSong, parts(n), "mix $n")
    }

    // ── The mansion (entryway ENCD script 224 + script 120's first branch) ───────────────────

    /** Bit 764: set the first time the entryway starts the song, which then plays from its beginning; later it seeks. */
    @Volatile internal var mansionHeard = false

    /**
     * Entering the entryway: song 114 starts (from the start the first time, then from a random beat 21-120), and
     * 115, 119 and 113 stop. 114 hands over to the town song at its marker 4 (beat 292.36) and stops at marker 5; the
     * town song then gets a random mix 1-3 and the walk of script 120 carries on as in a shop.
     */
    internal fun startMansion(e: WoodtickEngine.Engine, room: Room) {
        here = room
        e.stopSound(ISLAND)
        townSong = TOWN
        walks = 0
        intro = true
        pendingMix = 0
        e.startSound(MANSION)
        if (mansionHeard) {
            val beat = rng.nextInt(21, 121)
            e.jump(MANSION, 0, beat, 0)
            e.note("script 120 seeks the mansion song to beat $beat")
        }
        mansionHeard = true
        e.onMarker(MANSION, 4, "the town song starts under the end of the mansion song") {
            intro = false
            visited.fill(false); visited[1] = true
            startSound(TOWN)
            val n = pendingMix.takeIf { it != 0 } ?: random()
            pendingMix = 0
            after(MIX_DELAY_MS) { applyMix(this, n) }
            arm(this)
        }
        e.onMarker(MANSION, 5, "the mansion song stops") { stopSound(MANSION) }
    }

    /** Leaving the entryway (its exit script): 114, 118 and 117 all fade over 60 ticks. */
    internal fun leaveMansion(e: WoodtickEngine.Engine, to: Room) {
        shuffling = false
        intro = false
        for (s in listOf(MANSION, TOWN, ALT)) e.fadeOutAndStop(s, FADE_MS)
        here = to
    }

    /**
     * The island map and the grounds. Script 116 on arriving at the map: if the island song is playing it stays and part hook 2
     * of part 0 is armed, otherwise it starts (coming back from the mansion, where it was stopped). Script 117 does the same
     * on the way into the ville. The grounds change nothing: the island song carries on if it was playing, else it is silent.
     */
    private fun islandWalk(e: WoodtickEngine.Engine, to: Room) {
        here = to
        if (to.key == "grounds") return
        if (e.isPlaying(ISLAND)) { if (to.key == "map") e.setPartHook(ISLAND, 2, 0) }
        else e.startSound(ISLAND)
    }

    // ── Script 120: the walk and the song swap ────────────────────────────────────────────

    /** Arms the director for the next loop: at marker 2 (beat 136, where the game's check at beat 134 acts) it walks or swaps. */
    private fun arm(e: WoodtickEngine.Engine) {
        if (!auto || shuffling || !inShop || townSong != TOWN) return
        e.onMarker(TOWN, 2, "loop end: next step of script 120") { window(this) }
    }

    private fun window(e: WoodtickEngine.Engine) {
        if (!auto || shuffling || !inShop || townSong != TOWN) return
        if (walks < 2 || mix == 9 || mix == 10) {
            walks++
            val next = nextWalkStep(allowShuffle = true)
            if (next == null) e.onMarker(TOWN, 3, "fast shuffle begins") { startShuffle(this) }
            else e.onMarker(TOWN, 3, "instrument mix $next (random walk)") { applyMix(this, next); arm(this) }
        } else swapToAlt(e, button = false)
    }

    private fun swapToAlt(e: WoodtickEngine.Engine, button: Boolean) {
        if (townSong == ALT || intro) return
        if (button) {
            e.onMarker(TOWN, 2, "alternate song 117 (button)") { swapToAlt(this, button = false) }
            return
        }
        townSong = ALT
        walks = 0
        e.startSound(ALT)
        armHandoff(e)
    }

    /** 118 stops at its next marker 5, and 117 hands back to a fresh 118 at its own marker 3 (with a new mix 1-3). */
    private fun armHandoff(e: WoodtickEngine.Engine) {
        if (e.isPlaying(TOWN)) e.onMarker(TOWN, 5, "end of the main song") { stopSound(TOWN) }
        e.onMarker(ALT, 3, "back to the main song") {
            townSong = TOWN
            walks = 0
            visited.fill(false); visited[1] = true
            startSound(TOWN)
            stopSound(ALT)
            val n = if (here.key == "spitville") roomMixes.getValue("spitville") else random()
            after(MIX_DELAY_MS) { applyMix(this, n) }
            arm(this)
        }
    }

    /**
     * One step of script 120's random walk from the current mix: pick a letter of its row; `A`-`J` name the next mix
     * (skipped if it was already visited or is the current one), `S` forgets the visited mixes, `R` (the game lets the
     * music die out) is skipped here, and `Z` is returned as null when [allowShuffle] (it starts script 122).
     */
    private fun nextWalkStep(allowShuffle: Boolean): Int? {
        val row = rows[mix - 1]
        repeat(200) {
            when (val c = row[rng.nextInt(row.length)]) {
                'Z' -> if (allowShuffle) return null
                'S' -> visited.fill(false)
                'R' -> {}
                else -> {
                    val n = c - 'A' + 1
                    if (n != mix && !visited[n]) { visited[n] = true; return n }
                }
            }
        }
        visited.fill(false)   // every neighbour was visited: start the walk over
        return row.firstOrNull { it in 'A'..'J' && it - 'A' + 1 != mix }?.let { it - 'A' + 1 } ?: (mix % 10 + 1)
    }

    // ── Script 122: the fast shuffle ──────────────────────────────────────────────────────

    private fun startShuffle(e: WoodtickEngine.Engine) {
        if (shuffling || intro || townSong != TOWN || !inTown) return
        shuffling = true
        shuffleUsed.fill(false)
        e.clearTriggers()
        e.note("fast shuffle (script 122): a random mix from 1 to 5 every 6 s")
        shuffleStep(e)
    }

    private fun shuffleStep(e: WoodtickEngine.Engine) {
        if (!shuffling) return
        val free = (1..5).filter { !shuffleUsed[it] }.ifEmpty { shuffleUsed.fill(false); (1..5).toList() }
        val n = free[rng.nextInt(free.size)]
        shuffleUsed[n] = true
        applyMix(e, n)
        e.after(SHUFFLE_MS) { shuffleStep(this) }
    }

    // ── Walking between rooms (script 117) ────────────────────────────────────────────────

    /** What is playing now, for fades. */
    private val current: Int get() = when (here.song) { TOWN -> townSong; else -> here.song }

    override fun playing(room: Room): Set<Int> = if (room.song == TOWN || room.song == MANSION) setOf(MANSION, TOWN, ALT) else setOf(room.song)

    override fun go(e: WoodtickEngine.Engine, from: Room, to: Room) {
        e.clearTriggers()       // [272]
        shuffling = false
        when {
            to.key == "entryway" -> return startMansion(e, to)
            from.key == "entryway" -> return leaveMansion(e, to)
            to.key == "map" || to.key == "grounds" || (from.key == "map" && to.key == "ville") -> return islandWalk(e, to)
        }
        val old = current
        when {
            to.song == FUNERAL -> {
                // L1 == 50: 60-tick fades, the funeral song starts straight away.
                e.fadeOutAndStop(old, FADE_MS)
                if (old == ALT && e.isPlaying(TOWN)) e.fadeOutAndStop(TOWN, FADE_MS)
                begin(e, to, skipIntro = false)
            }
            from.song == FUNERAL -> {
                // L0 == 50: arm jump hook 1 on 116 and wait until it has finished playing.
                if (!e.isPlaying(FUNERAL)) { begin(e, to, skipIntro = false); return }
                e.setHook(FUNERAL, 1)
                e.note("${from.title} → ${to.title}: waiting for the funeral song to finish its ending")
                e.onEnd(FUNERAL, "start ${to.title}'s song") { begin(this, to, skipIntro = false) }
            }
            from.song == ISLAND && to.song == TOWN -> {
                // L1 == 47/48/49 with the island song playing: hook 1 cues its ending while it fades (90 ticks for the shops,
                // 60 for Spitville), and the town song starts a frame later.
                val shop = to.key in shops
                if (shop) e.setHook(ISLAND, 1)
                e.fadeOutAndStop(ISLAND, if (shop) FADE_TO_TOWN_MS else FADE_MS)
                e.after(HANDOVER_MS) { begin(this, to, skipIntro = shop) }
            }
            from.song == TOWN && to.song == ISLAND -> {
                // L0 == 47/48/49, L1 == 46: both town songs fade over 60 ticks, script 120 stops, the island song starts afresh.
                e.fadeOutAndStop(old, FADE_MS)
                if (old == ALT && e.isPlaying(TOWN)) e.fadeOutAndStop(TOWN, FADE_MS)
                begin(e, to, skipIntro = false)
            }
            else -> begin(e, to, skipIntro = false)
        }
    }
}
