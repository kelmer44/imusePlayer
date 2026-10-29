package adventureexplorer.woodtick

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import javax.swing.UIManager

/** Monkey Island 2 Music Explorer (Woodtick, Booty Island). Optional first argument (or MONKEY2_PATH): the Monkey Island 2 folder. */
fun main(args: Array<String>) {
    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (_: Exception) {}
    val path = args.firstOrNull() ?: System.getenv("MONKEY2_PATH")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Monkey Island 2 Music Explorer",
            icon = painterResource("icons/fedora.png"),
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
        ) {
            WoodtickApp(path)
        }
    }
}
