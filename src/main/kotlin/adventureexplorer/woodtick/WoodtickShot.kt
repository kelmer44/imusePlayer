// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 gsanmartin and contributors. Part of MI2 Music Explorer; see LICENSE and THIRD_PARTY.md.
package adventureexplorer.woodtick

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Dev tool: render the README screenshots without a window. Each one plays the real songs offscreen for a few
 * seconds into a transition, so the side panel shows genuine state. args: <gamePath> <outputDir>
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val game = args[0]
    val out = File(args[1]).also { it.mkdirs() }
    val assets = Mi2Loader.load(game) { }

    fun shot(name: String, scene: Scene, room: Room?, x: Float = 0f, toggleOn: Boolean = false, script: WoodtickEngine.() -> Unit) {
        val engine = WoodtickEngine(assets.songs)
        engine.script()
        val image = ImageComposeScene(1280, 800, Density(1f)) {
            AppFrame { Explorer(assets, engine, game, scene, room, x, toggleOn) }
        }.let { composeScene ->
            composeScene.render()
            composeScene.render().also { composeScene.close() }
        }
        File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("wrote $name.png")
    }

    val street = Woodtick.byKey.getValue("woodtick")
    val cartographer = Woodtick.byKey.getValue("cartograp")
    shot("woodtick-transition", Woodtick, cartographer) {
        post { Woodtick.enter(this, street) }; runOffline(3.0)
        post { Woodtick.go(this, street, cartographer) }; runOffline(0.4)
    }

    val laundry = Woodtick.byKey.getValue("laundry")
    shot("woodtick-laundry", Woodtick, laundry, toggleOn = true) {
        post { Woodtick.enter(this, laundry) }; runOffline(2.0)
        post { Woodtick.talkStart(this) }; runOffline(5.0)
    }

    val ville = BootyIsland.byKey.getValue("ville")
    val antique = BootyIsland.byKey.getValue("antique")
    BootyIsland.random = { 3 }
    shot("booty-island", BootyIsland, antique) {
        post { BootyIsland.enter(this, ville) }; runOffline(2.0)
        post { BootyIsland.go(this, ville, antique) }; runOffline(1.0)
    }

    shot("swamp", Swamp, Swamp.rooms.first(), x = 1100f) {
        post { Swamp.enter(this, Swamp.rooms.first()) }; runOffline(1.0)
        post { Swamp.move(this, 0, 1100) }; runOffline(1.0)
    }
}
