// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 gsanmartin and contributors. Part of MI2 Music Explorer; see LICENSE and THIRD_PARTY.md.
package adventureexplorer.woodtick

/** A room on a scene's map: the SCUMM room number, the song it plays, where you can go from it. */
data class Room(
    val key: String,
    val scummRoom: Int,
    val title: String,
    val song: Int,
    val exits: List<String>,
    /** What the music does here, for the UI. */
    val note: String,
    /** SCUMM rooms whose pictures are attached to the right of this one (the swamp continues into the voodoo hut). */
    val appended: List<Int> = emptyList(),
)

/** A switch a room offers that changes the music without leaving it (talking in the laundry, the ville's auto-evolve). */
data class Toggle(
    val roomKey: String,
    val startLabel: String,
    val stopLabel: String,
    val description: String,
    val start: (WoodtickEngine.Engine) -> Unit,
    val stop: (WoodtickEngine.Engine) -> Unit,
    /** Short texts for the small button on the room's picture. */
    val shortOff: String = "Talk",
    val shortOn: String = "Talking…",
    /** When the model itself knows whether the switch is on, the UI follows it instead of remembering clicks. */
    val isOn: (() -> Boolean)? = null,
)

/** A one-shot action a room offers (a new instrument mix, a song change). */
data class Action(
    val roomKey: String,
    val label: String,
    val description: String,
    val short: String,
    val run: (WoodtickEngine.Engine) -> Unit,
)

/** A row of numbered choices a room offers (the ville's ten instrument mixes). */
data class Chips(
    val roomKey: String,
    val title: String,
    val description: String,
    val options: List<String>,
    val selected: () -> Int,
    val pick: (WoodtickEngine.Engine, Int) -> Unit,
)

/** One instrument layer of a song and whether it is playing right now. */
data class Layer(val label: String, val on: Boolean)

/**
 * A room whose music follows where the player stands along it (the swamp): a picture [width] pixels wide,
 * a position the music reacts to, and the layers that are playing at that position.
 */
class Scrub(
    val width: Int,
    /** Positions where the game's script changes the music, with a label for each. */
    val marks: List<Pair<Int, String>>,
    /** Pixels per second when the character walks by itself. */
    val speed: Int,
    /** The player walked from [from] to [to]; run the layer changes in between, in order. */
    val move: (WoodtickEngine.Engine, Int, Int) -> Unit,
    val layers: () -> List<Layer>,
)

/** A group of rooms whose music the game's scripts tie together (Woodtick, Booty Island's town). */
interface Scene {
    val key: String
    val title: String
    val rooms: List<Room>
    /** Where each room sits on the map, as fractions of the map area (centre of the tile). */
    val layout: Map<String, Pair<Float, Float>>
    val toggles: List<Toggle> get() = emptyList()
    val actions: List<Action> get() = emptyList()
    val chips: List<Chips> get() = emptyList()
    val scrub: Scrub? get() = null

    val byKey: Map<String, Room> get() = rooms.associateBy { it.key }
    val songIds: List<Int> get() = rooms.map { it.song }.distinct()

    /** The first thing that plays when the player picks a starting room. */
    fun enter(e: WoodtickEngine.Engine, room: Room)

    /** Walk from [from] to [to]: the music change the game's scripts make. */
    fun go(e: WoodtickEngine.Engine, from: Room, to: Room)
}

object Scenes {
    val all: List<Scene> = listOf(Woodtick, BootyIsland, Swamp)
    val songIds: List<Int> = all.flatMap { it.songIds }.distinct()
}
