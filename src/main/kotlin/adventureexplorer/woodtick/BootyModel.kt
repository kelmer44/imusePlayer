package adventureexplorer.woodtick

/**
 * Booty Island's town (Monkey Island 2), read out of the game scripts with `tools/scumm5_disasm.py`.
 *
 * - Ville, Spitville, the antique shop and the costume shop share one song (118): the game only changes
 *   which instrument parts play. Script 120 (running while you are in town) switches the parts whenever the
 *   ego's room changes, using a string of `1`/`0` characters, 9 per group (`V100 = (group - 1) * 9`): characters
 *   0-7 are parts 0-7 and character 8 is part 9. Spitville uses group 5, the antique shop 9, the costume shop 2;
 *   when the town song starts the game rolls a random group 1-3 for the ville.
 * - The funeral parlor has its own song (116). Script 117 `117(fromRoom, toRoom)` cross-fades into it with
 *   60-tick fades; leaving it arms jump hook 1 on 116 and waits for the song to finish before the town song starts.
 */
object BootyIsland : Scene {
    override val key = "booty"
    override val title = "Booty Island"

    override val rooms = listOf(
        Room("ville", 46, "Ville", 118, listOf("antique", "costume", "funeral", "spitville"), "Town theme"),
        Room("spitville", 47, "Spitville", 118, listOf("ville"), "Town theme, Spitville mix"),
        Room("antique", 48, "Antique shop", 118, listOf("ville"), "Town theme, antique shop mix"),
        Room("costume", 49, "Costume shop", 118, listOf("ville"), "Town theme, costume shop mix"),
        Room("funeral", 50, "Funeral parlor", 116, listOf("ville"), "Funeral parlor theme"),
    )

    override val layout = mapOf(
        "ville" to (0.50f to 0.46f),
        "antique" to (0.18f to 0.17f),
        "costume" to (0.82f to 0.17f),
        "spitville" to (0.85f to 0.76f),
        "funeral" to (0.15f to 0.76f),
    )

    private const val TOWN = 118
    private const val FUNERAL = 116

    /** The game's mask string, 9 characters per group: parts 0-7, then part 9. */
    private const val MASKS = "111100101011110101001101101111110101111111101001100101001100000000100000001000011000000010"
    private fun group(n: Int): Set<Int> {
        val m = MASKS.substring((n - 1) * 9, (n - 1) * 9 + 9)
        return buildSet { for (i in 0..7) if (m[i] == '1') add(i); if (m[8] == '1') add(9) }
    }

    private val shopGroups = mapOf("spitville" to 5, "antique" to 9, "costume" to 2)

    /** The ville's group, rolled 1-3 when the town song starts (`getRandomNr(2) + 1` in script 120). */
    internal var random: () -> Int = { kotlin.random.Random.nextInt(1, 4) }
    @Volatile internal var villeGroup = 1

    private fun groupFor(room: Room): Int = shopGroups[room.key] ?: villeGroup

    /** The song starts with three parts switched off by its own hooks at beat 1.110, so the mask goes on a few frames later. */
    private const val MASK_DELAY_MS = 400

    private fun startTown(e: WoodtickEngine.Engine, room: Room) {
        villeGroup = random()
        e.startSound(TOWN)
        e.after(MASK_DELAY_MS) { applyParts(TOWN, group(groupFor(room)), "${room.title} (group ${groupFor(room)})") }
    }

    override fun enter(e: WoodtickEngine.Engine, room: Room) {
        e.stopAll()
        if (room.song == FUNERAL) startFuneral(e) else startTown(e, room)
    }

    private fun startFuneral(e: WoodtickEngine.Engine) {
        // Script 117 switches part 9 off only when story flag bit 90 is set; it is not, so the song plays as is.
        e.startSound(FUNERAL)
    }

    override fun go(e: WoodtickEngine.Engine, from: Room, to: Room) {
        e.clearTriggers()
        when {
            to.song == FUNERAL -> {
                // Script 117, L1 == 50: 60-tick fade of the town song, the funeral song starts straight away.
                e.fadeOutAndStop(TOWN, 1000)
                startFuneral(e)
            }
            from.song == FUNERAL -> {
                // Script 117, L0 == 50: arm jump hook 1 on 116 and wait until it has finished playing.
                if (!e.isPlaying(FUNERAL)) { startTown(e, to); return }
                e.setHook(FUNERAL, 1)
                e.note("${from.title} → ${to.title}: waiting for the funeral song to finish its ending")
                e.onEnd(FUNERAL, "start the town song") { startTown(this, to) }
            }
            !e.isPlaying(TOWN) -> startTown(e, to)
            else -> {
                // Script 120: the town song keeps playing; only the parts change when the ego changes room.
                e.applyParts(TOWN, group(groupFor(to)), "${to.title} (group ${groupFor(to)})")
            }
        }
    }
}
