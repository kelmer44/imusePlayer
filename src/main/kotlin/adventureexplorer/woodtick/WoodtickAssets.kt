package adventureexplorer.woodtick

import adventureexplorer.audio.imuse.ImuseSong
import adventureexplorer.model.ResourceNode
import adventureexplorer.scripting.ScriptManager
import java.awt.image.BufferedImage
import java.io.File

/** Songs and room pictures read from the player's own Monkey Island 2 files. */
class Mi2Assets(val songs: Map<Int, ImuseSong>, val images: Map<String, BufferedImage>)

object Mi2Loader {
    /** True if [path] looks like a Monkey Island 2 folder (the game's index and resource files). */
    fun looksLikeMonkey2(path: String): Boolean {
        val names = File(path).list()?.map { it.lowercase() } ?: return false
        return "monkey2.000" in names && "monkey2.001" in names
    }

    fun load(path: String, progress: (String) -> Unit): Mi2Assets {
        require(looksLikeMonkey2(path)) { "That folder has no MONKEY2.000 / MONKEY2.001" }
        val mgr = ScriptManager()
        try {
            progress("Reading the game files…")
            val det = mgr.detectGame(path) ?: error("Adventure Explorer could not identify this folder as a SCUMM game")
            val base = findBaseName(det.resources) ?: error("No rooms found in the game data")

            val songs = LinkedHashMap<Int, ImuseSong>()
            for (id in Scenes.songIds) {
                progress("Loading song $id…")
                val song = mgr.loadResource(path, "snd:$base:$id:ADL_")?.imuse
                    ?: error("Sound $id has no AdLib music in this copy of the game (only the AdLib version is supported)")
                songs[id] = song
            }

            val images = LinkedHashMap<String, BufferedImage>()
            for (room in Scenes.all.flatMap { it.rooms }) {
                progress("Loading the ${room.title} picture…")
                // The street's composite shows unplaced door objects as magenta patches; its plain background is cleaner.
                val ids = if (room.key == "woodtick") listOf("bg", "comp") else listOf("comp", "bg")
                val data = ids.firstNotNullOfOrNull { mgr.loadResource(path, "$it:$base:${room.scummRoom}") }
                var image = data?.image ?: error("Room ${room.scummRoom} (${room.title}) has no picture")
                for (extra in room.appended) {
                    val more = listOf("comp", "bg").firstNotNullOfOrNull { mgr.loadResource(path, "$it:$base:$extra") }?.image
                        ?: error("Room $extra has no picture")
                    image = joinRight(image, more)
                }
                images[room.key] = image
            }
            return Mi2Assets(songs, images)
        } finally {
            mgr.close()
        }
    }

    /** [a] with [b] attached to its right edge, on one canvas as tall as the taller of the two. */
    private fun joinRight(a: BufferedImage, b: BufferedImage): BufferedImage {
        val out = BufferedImage(a.width + b.width, maxOf(a.height, b.height), BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try { g.drawImage(a, 0, 0, null); g.drawImage(b, a.width, 0, null) } finally { g.dispose() }
        return out
    }

    /** The game's resource prefix (`MONKEY2`), taken from the first room id. */
    private fun findBaseName(nodes: List<ResourceNode>): String? {
        for (n in nodes) {
            Regex("^room:([^:]+):").find(n.id)?.let { return it.groupValues[1] }
            findBaseName(n.children)?.let { return it }
        }
        return null
    }
}
