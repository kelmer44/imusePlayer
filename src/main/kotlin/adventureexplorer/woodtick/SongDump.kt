package adventureexplorer.woodtick

import adventureexplorer.audio.imuse.ImuseSong
import adventureexplorer.scripting.ScriptManager

/** Dev tool: print tracks, hooks, markers and loops of MI2 iMUSE songs. args: <gamePath> <soundId>... */
fun main(args: Array<String>) {
    val path = args[0]
    val mgr = ScriptManager()
    mgr.detectGame(path)
    for (id in args.drop(1)) {
        val song = mgr.loadResource(path, "snd:MONKEY2:$id:ADL_")?.imuse
        if (song == null) { println("sound $id: not found"); continue }
        println("== sound $id  tracks=${song.tracks.size} ppqn=${song.ppqn} start=${song.start}")
        val markers = song.catalogue.filterIsInstance<ImuseSong.CatalogueEntry.Marker>().groupBy { it.at.track to it.id }
        for ((k, v) in markers) println("  MARKER id=${k.second} track ${k.first}: ${v.size}x first ${v.first().at} last ${v.last().at}")
        for (e in song.catalogue) when (e) {
            is ImuseSong.CatalogueEntry.Hook -> println("  ${e.at}  HOOK ${e.kind} id=${e.id} ch=${e.channel} ${e.detail}")
            is ImuseSong.CatalogueEntry.Loop -> println("  ${e.at}  LOOP x${e.count} ${e.from} -> ${e.to}")
            else -> {}
        }
    }
    mgr.close()
}
