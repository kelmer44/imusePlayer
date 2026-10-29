package adventureexplorer.woodtick

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

/** Dev tool: render the main screen to a PNG without a window. args: <gamePath> <out.png> [roomKey] */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val assets = Mi2Loader.load(args[0]) { }
    val room = args.getOrNull(2)?.let { Woodtick.byKey[it] }
    val scene = ImageComposeScene(1280, 800, Density(1f)) {
        androidx.compose.material.MaterialTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF12100E))) { Explorer(assets, WoodtickEngine(assets.songs), args[0], room) } }
    }
    scene.render()
    val img = scene.render()
    File(args[1]).writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote ${args[1]}")
}
