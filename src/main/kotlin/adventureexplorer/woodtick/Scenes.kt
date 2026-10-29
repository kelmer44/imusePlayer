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
)

/** A button a room can offer that changes the music without leaving it (e.g. talking to characters). */
data class Toggle(
    val roomKey: String,
    val startLabel: String,
    val stopLabel: String,
    val description: String,
    val start: (WoodtickEngine.Engine) -> Unit,
    val stop: (WoodtickEngine.Engine) -> Unit,
)

/** A group of rooms whose music the game's scripts tie together (Woodtick, Booty Island's town). */
interface Scene {
    val key: String
    val title: String
    val rooms: List<Room>
    /** Where each room sits on the map, as fractions of the map area (centre of the tile). */
    val layout: Map<String, Pair<Float, Float>>
    val toggles: List<Toggle> get() = emptyList()

    val byKey: Map<String, Room> get() = rooms.associateBy { it.key }
    val songIds: List<Int> get() = rooms.map { it.song }.distinct()

    /** The first thing that plays when the player picks a starting room. */
    fun enter(e: WoodtickEngine.Engine, room: Room)

    /** Walk from [from] to [to]: the music change the game's scripts make. */
    fun go(e: WoodtickEngine.Engine, from: Room, to: Room)
}

object Scenes {
    val all: List<Scene> = listOf(Woodtick, BootyIsland)
    val songIds: List<Int> = all.flatMap { it.songIds }.distinct()
}
