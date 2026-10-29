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

/** Dev tool: notes per MIDI channel for each track of a song. args: <gamePath> <soundId> */
object NoteCounts {
    @JvmStatic fun main(args: Array<String>) {
        val mgr = ScriptManager()
        mgr.detectGame(args[0])
        val song = mgr.loadResource(args[0], "snd:MONKEY2:${args[1]}:ADL_")!!.imuse!!
        for ((i, track) in song.tracks.withIndex()) {
            val counts = sortedMapOf<Int, Int>()
            for (e in track) if (e is ImuseSong.Event.Channel && (e.status and 0xF0) == 0x90 && e.data2 > 0) counts.merge(e.status and 0x0F, 1, Int::plus)
            println("track $i: ${counts.entries.joinToString { "ch${it.key}=${it.value}" }}")
            if (i == 0) for (e in track) if (e is ImuseSong.Event.Channel && (e.status and 0xF0) == 0xB0 && e.data1 == 7)
                println("   vol  tick ${e.tick} ch${e.status and 0x0F} = ${e.data2}")
        }
        mgr.close()
    }
}

/** Dev tool: list the object layers (name, position, size) of a room composite. args: <gamePath> <room> */
object LayerDump {
    @JvmStatic fun main(args: Array<String>) {
        val mgr = ScriptManager()
        mgr.detectGame(args[0])
        val data = mgr.loadResource(args[0], "comp:MONKEY2:${args[1]}")!!
        for (l in data.layers.orEmpty()) println("LAYER ${l.name}  x=${l.x} y=${l.y} w=${l.image.width} h=${l.image.height} visible=${l.visible}")
        mgr.close()
    }
}
