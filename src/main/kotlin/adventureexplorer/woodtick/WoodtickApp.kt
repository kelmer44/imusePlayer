package adventureexplorer.woodtick

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser

private val Night = Color(0xFF12100E)
private val Panel = Color(0xFF1C1814)
private val Rule = Color(0xFF3A3129)
private val Ink = Color(0xFFEDE4D3)
private val Muted = Color(0xFF9A8F7E)
private val Rum = Color(0xFFE0A23B)
private val Lagoon = Color(0xFF58C4B4)

private sealed interface Screen {
    object Setup : Screen
    data class Loading(val message: String) : Screen
    data class Failed(val message: String) : Screen
    class Ready(val assets: Mi2Assets, val engine: WoodtickEngine) : Screen
}

private val prefs: Preferences get() = Preferences.userRoot().node("woodtick-music-explorer")

@Composable
fun WoodtickApp(initialPath: String? = null) {
    var screen by remember { mutableStateOf<Screen>(Screen.Setup) }
    var gamePath by remember { mutableStateOf(initialPath ?: prefs.get("gamePath", null)) }
    var loadRequest by remember { mutableStateOf(gamePath?.takeIf { Mi2Loader.looksLikeMonkey2(it) }) }

    LaunchedEffect(loadRequest) {
        val path = loadRequest ?: return@LaunchedEffect
        (screen as? Screen.Ready)?.engine?.shutdown()
        screen = Screen.Loading("Starting…")
        val result = withContext(Dispatchers.IO) {
            runCatching { Mi2Loader.load(path) { msg -> screen = Screen.Loading(msg) } }
        }
        screen = result.fold(
            onSuccess = {
                prefs.put("gamePath", path)
                Screen.Ready(it, WoodtickEngine(it.songs).also { e -> e.start() })
            },
            onFailure = { Screen.Failed(it.message ?: it.javaClass.simpleName) },
        )
    }

    MaterialTheme(colors = darkColors(primary = Rum, background = Night, surface = Panel, onSurface = Ink, onBackground = Ink)) {
        Box(Modifier.fillMaxSize().background(Night)) {
            when (val s = screen) {
                Screen.Setup -> Message("Monkey Island 2 Music Explorer", "Choose your Monkey Island 2 folder (the one with MONKEY2.000 and MONKEY2.001). The game files stay on your computer; nothing is copied.") {
                    ChooseFolderButton(gamePath) { gamePath = it; loadRequest = it }
                }
                is Screen.Loading -> Message("Loading", s.message) {}
                is Screen.Failed -> Message("Could not load the game", s.message) {
                    ChooseFolderButton(gamePath) { gamePath = it; loadRequest = it }
                }
                is Screen.Ready -> Explorer(s.assets, s.engine, gamePath.orEmpty(), changeGame = { gamePath = it; loadRequest = it })
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Muted, fontSize = 15.sp, modifier = Modifier.width(520.dp))
        Spacer(Modifier.height(24.dp))
        action()
    }
}

@Composable
private fun ChooseFolderButton(current: String?, onChosen: (String) -> Unit) {
    var problem by remember { mutableStateOf<String?>(null) }
    Button(onClick = {
        val chooser = JFileChooser(current?.let(::File)).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose the Monkey Island 2 folder"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val dir = chooser.selectedFile.absolutePath
            if (Mi2Loader.looksLikeMonkey2(dir)) { problem = null; onChosen(dir) }
            else problem = "That folder does not contain MONKEY2.000 and MONKEY2.001."
        }
    }, colors = ButtonDefaults.buttonColors(backgroundColor = Rum, contentColor = Night)) { Text("Choose folder…") }
    problem?.let { Text(it, color = Color(0xFFE57373), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
}

// ── Main screen ──────────────────────────────────────────────────────────────

@Composable
internal fun Explorer(
    assets: Mi2Assets, engine: WoodtickEngine, path: String,
    startScene: Scene = Scenes.all.first(), startAt: Room? = null, startX: Float = 0f, changeGame: (String) -> Unit = {},
) {
    var scene by remember { mutableStateOf(startScene) }
    var current by remember { mutableStateOf(startAt) }
    var activeToggle by remember { mutableStateOf<Toggle?>(null) }
    var state by remember { mutableStateOf(engine.state) }
    var posX by remember { mutableStateOf(startX) }
    var walking by remember { mutableStateOf(0) }
    val bitmaps = remember(assets) { assets.images.mapValues { it.value.toComposeImageBitmap() } }

    LaunchedEffect(engine) { while (true) { state = engine.state; delay(80) } }

    fun endToggle() {
        activeToggle?.let { t -> engine.post { t.stop(this) } }
        activeToggle = null
    }

    fun visit(room: Room) {
        val from = current
        endToggle()
        current = room
        if (from == null) engine.post { scene.enter(this, room) }
        else engine.post { scene.go(this, from, room) }
    }

    fun toggleRoom(toggle: Toggle?) {
        toggle ?: return
        if (activeToggle == toggle) endToggle() else { activeToggle = toggle; engine.post { toggle.start(this) } }
    }

    fun moveTo(target: Float) {
        val sc = scene.scrub ?: return
        val to = target.coerceIn(0f, sc.width.toFloat())
        val from = posX
        posX = to
        if (current == null) {
            val room = scene.rooms.first()
            current = room
            engine.post { scene.enter(this, room); sc.move(this, 0, to.toInt()) }
        } else engine.post { sc.move(this, from.toInt(), to.toInt()) }
    }

    LaunchedEffect(walking, scene) {
        val sc = scene.scrub ?: return@LaunchedEffect
        while (walking != 0) {
            delay(50)
            val next = posX + walking * sc.speed * 0.05f
            moveTo(next)
            if (next <= 0f || next >= sc.width) walking = 0
        }
    }

    fun silence() {
        walking = 0
        posX = 0f
        activeToggle = null
        engine.post { stopAll() }
        current = null
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
            Header(path, scene, onScene = { if (it != scene) { silence(); scene = it } }, onStop = ::silence, onChange = changeGame)
            Spacer(Modifier.height(12.dp))
            val hint = when {
                scene.scrub != null -> "Drag along the swamp, use the slider, or let the character walk. Instruments join where the game's script adds them."
                current == null -> "Click a room to start its music."
                else -> "Click a connected room to walk there. The music changes the way the game does it."
            }
            Text(hint, color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            val toggle = scene.toggles.firstOrNull { it.roomKey == current?.key }
            val toggleOn = toggle != null && activeToggle == toggle
            val scrub = scene.scrub
            if (scrub != null) {
                ScrubView(scrub, bitmaps.getValue(scene.rooms.first().key), posX, walking, state, onX = { walking = 0; moveTo(it) }, onWalk = { walking = it }, modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                Map(scene, current, bitmaps, toggle, toggleOn, onToggle = { toggleRoom(toggle) }, onVisit = ::visit, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
        val sideToggle = scene.toggles.firstOrNull { it.roomKey == current?.key }
        Side(current, sideToggle, sideToggle != null && activeToggle == sideToggle, state, onToggle = { toggleRoom(sideToggle) })
    }
}

@Composable
private fun ScrubView(
    scrub: Scrub, image: ImageBitmap, x: Float, walking: Int, @Suppress("UNUSED_PARAMETER") state: WoodtickEngine.Snapshot,
    onX: (Float) -> Unit, onWalk: (Int) -> Unit, modifier: Modifier,
) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Panel).border(1.dp, Rule, RoundedCornerShape(10.dp)).padding(16.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height).clip(RoundedCornerShape(8.dp)).border(1.dp, Rule, RoundedCornerShape(8.dp))
                .pointerInput(scrub) { detectTapGestures { onX(it.x / size.width * scrub.width) } }
                .pointerInput(scrub) { detectHorizontalDragGestures { change, _ -> onX(change.position.x / size.width * scrub.width) } },
        ) {
            val w = maxWidth
            Image(image, "Swamp", contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
            for ((i, mark) in scrub.marks.withIndex()) {
                val (mx, label) = mark
                Box(Modifier.offset(x = w * (mx.toFloat() / scrub.width)).width(2.dp).fillMaxHeight().background(Rum.copy(alpha = 0.8f)))
                Text(
                    "X $mx · $label", color = Rum, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    // Stagger neighbouring labels so they do not overlap
                    modifier = Modifier.offset(x = w * (mx.toFloat() / scrub.width) + 6.dp, y = 6.dp + 22.dp * (i % 2)).background(Night.copy(alpha = 0.7f)).padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            // The character: a marker that follows the position
            Box(Modifier.offset(x = w * (x / scrub.width) - 1.dp).width(3.dp).fillMaxHeight().background(Lagoon))
            Box(Modifier.align(Alignment.BottomStart).offset(x = w * (x / scrub.width) - 8.dp, y = (-8).dp).size(16.dp).clip(RoundedCornerShape(8.dp)).background(Lagoon).border(2.dp, Night, RoundedCornerShape(8.dp)))
        }
        Spacer(Modifier.height(12.dp))
        Slider(
            value = x, onValueChange = onX, valueRange = 0f..scrub.width.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Lagoon, activeTrackColor = Lagoon, inactiveTrackColor = Rule),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onWalk(if (walking == -1) 0 else -1) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = if (walking == -1) Lagoon else Ink)) { Text(if (walking == -1) "Pause" else "Walk left") }
            OutlinedButton(onClick = { onWalk(if (walking == 1) 0 else 1) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = if (walking == 1) Lagoon else Ink)) { Text(if (walking == 1) "Pause" else "Walk right") }
            Spacer(Modifier.weight(1f))
            Text("X ${x.toInt()} of ${scrub.width}", color = Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(14.dp))
        Label("Instrument layers")
        for (layer in scrub.layers()) {
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (layer.on) Lagoon else Rule))
                Spacer(Modifier.width(10.dp))
                Text(layer.label, color = if (layer.on) Ink else Muted, fontSize = 14.sp)
                Text(if (layer.on) "  playing" else "  off", color = if (layer.on) Lagoon else Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Header(path: String, scene: Scene, onScene: (Scene) -> Unit, onStop: () -> Unit, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Monkey Island 2 Music Explorer", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("iMUSE · AdLib (OPL) · ${File(path).name}", color = Muted, fontSize = 13.sp)
        }
        OutlinedButton(onClick = onStop, colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) { Text("Stop music") }
        Spacer(Modifier.width(8.dp))
        var problem by remember { mutableStateOf(false) }
        OutlinedButton(onClick = {
            val chooser = JFileChooser(File(path)).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Choose the Monkey Island 2 folder" }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val dir = chooser.selectedFile.absolutePath
                if (Mi2Loader.looksLikeMonkey2(dir)) onChange(dir) else problem = true
            }
        }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) { Text(if (problem) "Not a MI2 folder" else "Game folder…") }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SCENE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        for (s in Scenes.all) {
            val on = s == scene
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (on) Rum else Color.Transparent)
                    .border(1.dp, if (on) Rum else Rule, RoundedCornerShape(20.dp))
                    .clickable { onScene(s) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) { Text(s.title, color = if (on) Night else Ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun Map(
    scene: Scene, current: Room?, bitmaps: kotlin.collections.Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    toggle: Toggle?, toggleOn: Boolean, onToggle: () -> Unit, onVisit: (Room) -> Unit, modifier: Modifier,
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(10.dp)).background(Panel).border(1.dp, Rule, RoundedCornerShape(10.dp))) {
        val w = maxWidth
        val h = maxHeight
        fun tileWidth(room: Room): Dp = if (room.key == scene.rooms.first().key) (w * 0.52f).coerceIn(300.dp, 720.dp) else (w * 0.20f).coerceIn(150.dp, 280.dp)
        fun tileHeight(room: Room): Dp {
            val b = bitmaps[room.key]!!
            return tileWidth(room) * b.height / b.width
        }

        Canvas(Modifier.fillMaxSize()) {
            val drawn = mutableSetOf<Pair<String, String>>()
            for (room in scene.rooms) for (exit in room.exits) {
                val key = listOf(room.key, exit).sorted().let { it[0] to it[1] }
                if (!drawn.add(key)) continue
                val (ax, ay) = scene.layout[room.key]!!
                val (bx, by) = scene.layout[exit]!!
                val live = current != null && ((current.key == room.key && exit in current.exits) || (current.key == exit && room.key in current.exits))
                drawLine(
                    color = if (live) Rum else Rule,
                    start = Offset(size.width * ax, size.height * ay),
                    end = Offset(size.width * bx, size.height * by),
                    strokeWidth = if (live) 3.dp.toPx() else 1.5.dp.toPx(),
                )
            }
        }

        for (room in scene.rooms) {
            val (fx, fy) = scene.layout[room.key]!!
            val tw = tileWidth(room)
            val th = tileHeight(room)
            val here = current?.key == room.key
            val reachable = current == null || room.key in current.exits
            val shape = RoundedCornerShape(8.dp)
            Column(
                Modifier.offset(x = w * fx - tw / 2, y = h * fy - (th + 22.dp) / 2).width(tw),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(tw, th).clip(shape)
                        .border(if (here) 3.dp else 1.dp, if (here) Rum else if (reachable) Lagoon else Rule, shape)
                        .clickable(enabled = reachable && !here) { onVisit(room) },
                ) {
                    Image(bitmaps[room.key]!!, room.title, contentScale = ContentScale.Crop, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
                    if (!reachable) Box(Modifier.fillMaxSize().background(Night.copy(alpha = 0.72f)))
                    if (here && toggle != null) {
                        // Small button on the picture itself: the same switch as the side panel's.
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (toggleOn) Lagoon else Night.copy(alpha = 0.85f))
                                .border(1.dp, if (toggleOn) Lagoon else Rum, RoundedCornerShape(14.dp))
                                .clickable { onToggle() }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(if (toggleOn) "Talking…" else "Talk", color = if (toggleOn) Night else Rum, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(
                    room.title, color = if (here) Rum else if (reachable) Ink else Muted, fontSize = 13.sp,
                    fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Side(current: Room?, toggle: Toggle?, toggleOn: Boolean, state: WoodtickEngine.Snapshot, onToggle: () -> Unit) {
    Column(Modifier.width(360.dp).fillMaxHeight().background(Panel).border(1.dp, Rule).padding(18.dp)) {
        Label("In this room")
        Text(current?.title ?: "—", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(current?.note ?: "Nothing playing yet", color = Muted, fontSize = 13.sp)
        if (toggle != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(backgroundColor = if (toggleOn) Lagoon else Rum, contentColor = Night)) {
                Text(if (toggleOn) toggle.stopLabel else toggle.startLabel)
            }
            Text(toggle.description, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(20.dp))
        Label("Playing")
        if (state.playing.isEmpty()) Text("Silence", color = Muted, fontSize = 13.sp)
        for (p in state.playing) {
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sound ${p.sound}", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp))
                Text("track ${p.track} · beat ${p.beat}.${p.tick.toString().padStart(3, '0')}", color = Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                if (p.ending) Text("  ending", color = Rum, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Label("Waiting for")
        if (state.triggers.isEmpty()) Text("No transition pending", color = Muted, fontSize = 13.sp)
        for (t in state.triggers) Text(t, color = Rum, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))

        Spacer(Modifier.height(20.dp))
        Label("Music log")
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(state.log.asReversed()) { Text(it, color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp)) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))
}
