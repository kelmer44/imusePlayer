package adventureexplorer.woodtick

import adventureexplorer.audio.AudioLines
import adventureexplorer.audio.Opl3
import adventureexplorer.audio.imuse.AdlibOplDevice
import adventureexplorer.audio.imuse.ImuseEvent
import adventureexplorer.audio.imuse.ImuseSequencer
import adventureexplorer.audio.imuse.ImuseSong
import adventureexplorer.audio.imuse.ImuseSong.HookKind
import java.util.concurrent.ConcurrentLinkedQueue
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine

/**
 * A small subset of the SCUMM V5 iMUSE command interface (what `soundKludge`
 * and `startSound` do in Monkey Island 2's Woodtick scripts), running AdLib songs
 * through the Nuked OPL emulator. Every song gets its own chip so two songs can
 * overlap for cross-fades and a stopped song's notes keep releasing naturally.
 *
 * The public methods may be called from any thread: they queue the command for the
 * audio thread. Marker triggers (`enqueue_trigger` + `enqueue_command` in the
 * game) run on the audio thread, inside the sequencer's parse, exactly like
 * ScummVM's `handle_marker`.
 */
class WoodtickEngine(private val songs: Map<Int, ImuseSong>) {
    data class Playing(val sound: Int, val track: Int, val beat: Int, val tick: Int, val gain: Double, val ending: Boolean)
    data class Snapshot(val playing: List<Playing>, val triggers: List<String>, val log: List<String>)

    private inner class Voice(val sound: Int, val song: ImuseSong) {
        val chip = Opl3(SAMPLE_RATE)
        val device = AdlibOplDevice(chip, enablePercussion = song.supportsPercussion)
        val seq: ImuseSequencer = ImuseSequencer(song, device) { onEvent(this, it) }
        var gain = 1.0
        var fadeStep = 0.0          // per timer tick
        var stopAtSilence = false
        var tailSamples = -1        // >= 0 once stopped: samples of release tail still to render
        var inEnding = false
        val stopped get() = tailSamples >= 0

        fun stop() {
            if (stopped) return
            seq.stop()
            tailSamples = SAMPLE_RATE * 2
        }
    }

    private class Trigger(val sound: Int, val marker: Int, val label: String, val action: () -> Unit)
    private class Animation(val durationMs: Double, val step: (Double) -> Unit, var elapsedMs: Double = 0.0)

    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private val voices = mutableListOf<Voice>()
    private val triggers = mutableListOf<Trigger>()
    private val animations = mutableListOf<Animation>()
    private val log = ArrayDeque<String>()
    @Volatile private var running = false
    @Volatile private var snapshot = Snapshot(emptyList(), emptyList(), emptyList())
    private var thread: Thread? = null

    val state: Snapshot get() = snapshot

    // ── Commands (queued) ────────────────────────────────────

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            runCatching { loop() }.onFailure { say("Audio failed: ${it.message ?: it.javaClass.simpleName}") }
            running = false
        }, "woodtick-audio").apply { isDaemon = true; start() }
    }

    fun shutdown() {
        running = false
        thread?.join(1500)
        thread = null
    }

    fun post(command: Engine.() -> Unit) { commands += { Engine().command() } }

    /** Audio-thread view of the engine; transitions are written against this. */
    inner class Engine {
        fun isPlaying(sound: Int) = voices.any { it.sound == sound && !it.stopped }

        /** `startSound`: restarts the song from its beginning; [hook] arms a jump hook straight away. */
        fun startSound(sound: Int, hook: Int = 0, gain: Double = 1.0) {
            val song = songs[sound] ?: return say("sound $sound: not available")
            voices.filter { it.sound == sound && !it.stopped }.forEach { it.stop() }
            val v = Voice(sound, song).also { it.gain = gain }
            voices += v
            if (hook != 0) v.seq.armHook(HookKind.JUMP, hook)
            say("start sound $sound" + if (hook != 0) " (hook $hook armed)" else "")
        }

        fun stopSound(sound: Int) {
            voices.filter { it.sound == sound && !it.stopped }.forEach { it.stop(); say("stop sound $sound") }
        }

        fun stopAll() {
            voices.filter { !it.stopped }.forEach { it.stop() }
            triggers.clear(); animations.clear()
            say("stop all sounds")
        }

        /** Parameter fader on volume (`doCommand` 0x10D): fade to silence over [ms], then stop. */
        fun fadeOutAndStop(sound: Int, ms: Int) {
            for (v in voices) if (v.sound == sound && !v.stopped) {
                v.fadeStep = -v.gain / (ms * ImuseSequencer.TIMER_HZ / 1000.0).coerceAtLeast(1.0)
                v.stopAtSilence = true
            }
            say("fade out sound $sound over ${ms} ms")
        }

        fun setHook(sound: Int, hook: Int) {
            voices.firstOrNull { it.sound == sound && !it.stopped }?.seq?.armHook(HookKind.JUMP, hook)
            say("set hook $hook on sound $sound")
        }

        fun jump(sound: Int, track: Int, beat: Int, tick: Int) {
            voices.firstOrNull { it.sound == sound && !it.stopped }?.let {
                it.seq.jumpTo(track, beat, tick)
                it.inEnding = true
                say("jump sound $sound to track $track beat $beat.$tick")
            }
        }

        fun partOnOff(sound: Int, part: Int, on: Boolean) {
            voices.firstOrNull { it.sound == sound && !it.stopped }?.seq?.partOnOff(part, on)
        }

        fun partVolume(sound: Int, part: Int, value: Int) {
            voices.firstOrNull { it.sound == sound && !it.stopped }?.seq?.partVolume(part, value)
        }

        /** `enqueue_trigger` + `enqueue_command`: run [action] once, the next time [sound] emits [marker]. */
        fun onMarker(sound: Int, marker: Int, label: String, action: Engine.() -> Unit) {
            triggers += Trigger(sound, marker, label) { Engine().action() }
        }

        /** `clear_queue`. */
        fun clearTriggers() { triggers.clear() }

        /** Run [step] with progress 0..1 over [ms] of real time (a script loop that steps once per frame). */
        fun animate(ms: Int, step: (Double) -> Unit) {
            animations += Animation(ms.toDouble(), step)
        }

        fun note(text: String) = say(text)
    }

    // ── Audio thread ─────────────────────────────────────────

    private fun say(text: String) {
        synchronized(log) { log.addLast(text); while (log.size > 200) log.removeFirst() }
    }

    private fun onEvent(voice: Voice, e: ImuseEvent) {
        when (e) {
            is ImuseEvent.Marker -> {
                val hit = triggers.filter { it.sound == voice.sound && it.marker == e.id }
                for (t in hit) {
                    triggers.remove(t)
                    say("marker ${e.id} of sound ${voice.sound} at ${e.at} → ${t.label}")
                    t.action()
                }
            }
            is ImuseEvent.Jumped -> if (e.hookId != 0) say("hook ${e.hookId} of sound ${voice.sound}: jump ${e.from} → ${e.to}")
            is ImuseEvent.Ended -> say("sound ${voice.sound} reached the end of its data")
            else -> {}
        }
    }

    private val frame = IntArray(2)
    private var due = 0.0

    /** One 250 Hz timer tick: run commands, animations and sequencers, then hand the tick's samples to [sink]. */
    internal fun tick(sink: (Int, Int) -> Unit) {
        val tickMs = 1000.0 / ImuseSequencer.TIMER_HZ
        while (true) (commands.poll() ?: break)()

        for (a in animations.toList()) {
            a.elapsedMs += tickMs
            a.step((a.elapsedMs / a.durationMs).coerceAtMost(1.0))
            if (a.elapsedMs >= a.durationMs) animations.remove(a)
        }
        for (v in voices.toList()) {
            if (!v.stopped) {
                if (v.fadeStep != 0.0) {
                    v.gain = (v.gain + v.fadeStep).coerceAtLeast(0.0)
                    if (v.gain <= 0.0 && v.stopAtSilence) v.stop()
                }
                if (v.seq.active) v.seq.onTimer()
                v.device.onTimer()
                // A song that played to the end of its data (an ending track) releases its notes naturally.
                if (!v.seq.active) v.stop()
            }
        }

        due += SAMPLE_RATE.toDouble() / ImuseSequencer.TIMER_HZ
        val n = due.toInt()
        due -= n
        val live = voices.toList()
        repeat(n) {
            var l = 0.0
            var r = 0.0
            for (v in live) {
                if (v.stopped && v.tailSamples <= 0) continue
                v.chip.generate(frame)
                l += frame[0] * v.gain
                r += frame[1] * v.gain
            }
            sink((l * MASTER).toInt().coerceIn(-32768, 32767), (r * MASTER).toInt().coerceIn(-32768, 32767))
        }
        for (v in live) if (v.stopped) v.tailSamples -= n
        voices.removeAll { it.stopped && it.tailSamples <= 0 }
    }

    private fun loop() {
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 2, true, false)
        val line: SourceDataLine = AudioLines.sourceLine(format)
        line.open(format, SAMPLE_RATE / 5 * 4)
        line.start()
        val buffer = ByteArray(4096)
        var filled = 0
        var ticks = 0
        try {
            while (running) {
                tick { sl, sr ->
                    buffer[filled++] = sl.toByte(); buffer[filled++] = (sl shr 8).toByte()
                    buffer[filled++] = sr.toByte(); buffer[filled++] = (sr shr 8).toByte()
                    if (filled == buffer.size) { line.write(buffer, 0, filled); filled = 0 }
                }
                if (++ticks % 12 == 0) publish()
            }
        } finally {
            for (v in voices) v.stop()
            line.stop(); line.close()
            publish()
        }
    }

    /** Test hook: run [seconds] of engine time without an audio device; returns the log. */
    internal fun runOffline(seconds: Double, onTick: (Double) -> Unit = {}): List<String> {
        val ticks = (seconds * ImuseSequencer.TIMER_HZ).toInt()
        for (i in 0 until ticks) { tick { _, _ -> }; onTick(i / ImuseSequencer.TIMER_HZ.toDouble()) }
        publish()
        return snapshot.log
    }

    /** Test hook: like [runOffline] but returns the peak level (0..32767) of every 10 ms of mixed output. */
    internal fun renderPeaks(seconds: Double): IntArray {
        val window = SAMPLE_RATE / 100
        val peaks = ArrayList<Int>()
        var count = 0
        var peak = 0
        repeat((seconds * ImuseSequencer.TIMER_HZ).toInt()) {
            tick { l, r ->
                peak = maxOf(peak, kotlin.math.abs(l), kotlin.math.abs(r))
                if (++count == window) { peaks += peak; count = 0; peak = 0 }
            }
        }
        publish()
        return peaks.toIntArray()
    }

    internal fun playingSounds(): List<Int> = voices.filter { !it.stopped }.map { it.sound }

    private fun publish() {
        val playing = voices.filter { !it.stopped }.map {
            val p = it.seq.position
            Playing(it.sound, p.track, p.beat, p.tick, it.gain, it.inEnding)
        }
        snapshot = Snapshot(playing, triggers.map { "sound ${it.sound} marker ${it.marker}: ${it.label}" }, synchronized(log) { log.toList() })
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        /** One chip alone plays at the preview's level; overlapping chips are limited by the clamp. */
        private const val MASTER = 1.0
    }
}
