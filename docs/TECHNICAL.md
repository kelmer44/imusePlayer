# Technical notes

How the music behaviour in MI2 Music Explorer was found, what it does, and what it leaves out. Everything here was
read out of the game's own scripts and songs with the tools in `tools/` and `./gradlew` helpers listed at the end.

## How the player works

- **iMUSE sequencer** (`audio/imuse/ImuseSequencer.kt`): a Kotlin port of ScummVM's iMUSE player. It runs a song's
  MIDI tracks and handles its SysEx: loops, jump hooks, part on/off, markers, instruments.
- **AdLib driver and OPL** (`AdlibOplDevice.kt`, `Opl3.kt`): nine OPL2 voices shared between the parts, rendered by a
  port of Nuked OPL3.
- **Engine** (`woodtick/WoodtickEngine.kt`): runs the sequencers on a 250 Hz timer. Each song gets its own OPL chip, so
  two songs can overlap and a stopped song's notes keep releasing. It offers the small part of the game's iMUSE command
  set the scripts use: start and stop a sound, set a hook, jump, fade, part on/off, and one-shot triggers that fire on
  a song's marker (the game's `enqueue_trigger` / `enqueue_command`) or when a song finishes.
- **Scenes** (`woodtick/*Model.kt`): each scene is a list of rooms plus the music rules the game's scripts apply
  between them. `Woodtick`, `BootyIsland` and `Swamp` implement `Scene`.
- **Settling delay:** a song's own setup data switches parts on or off just after it starts. Part commands sent in the
  first half second are held until then, otherwise they would be overwritten.

## Reading the scripts

`tools/scumm5_disasm.py` is a small SCUMM v5 script reader (the opcode table is generated from ScummVM's
`script_v5.cpp`). It decodes about 99% of Monkey Island 2's scripts (a few dozen of roughly 3,300 stop early on an opcode it does not model) and prints the music calls:

- `startSound` / `stopSound`: start or stop a sound resource.
- `soundKludge [command, ...]`: iMUSE commands. The command number is `256 * param + cmd`, for example `268` is
  "set hook" (`doCommand` 0x10C), `267` is part on/off, `278` part volume, `269` fade volume, `270` / `271` queue a
  marker trigger and its commands, `272` clear the queue, `[-1]` runs what has been queued.
- Local scripts are numbered 200 and up inside a room; verb scripts belong to objects.

Conditional jumps are easy to misread: in SCUMM bytecode the operand order is reversed, and `notEqualZero` falls
through when the value is non-zero. The notes below take that into account.

Songs start with only some of their parts on (the SysEx at the start of each song sets that). The scripts layer the
rest in. That is why a song "naturally" sounds thinner than all of its parts together.

## Woodtick

| Room | SCUMM room | Song |
|---|---|---|
| Woodtick street | 7 | 101 |
| Cartographer | 8 | 104 |
| Bar (and Grill, which has no song of its own) | 9, 11 | 103 |
| Inn | 12 | 152 |
| Largo's room | 13 | 182 |
| Laundry | 14 | 110 |
| Woodshop | 15 | 111 |

- **Global script 96** is `96(fromRoom, toRoom)`. Leaving a room queues two marker triggers on the song being left:
  - *marker 0* either jumps the street song straight to its ending (track 5, beat 4.400; the street song has a marker
    every 4 beats), or arms jump hook 1 on the other songs (their marker 0 comes every 8 beats, just before the hook),
    which leads to an ending track;
  - *marker 1* sits inside every ending track. It starts the new song (`startSound` of the destination). The old song is
    **not** stopped: it plays out the rest of its ending while the new one begins, which is what makes the change
    seamless.
  - The street song starts with jump hook 7 armed, which skips to its arrival section.
- **Inn and Largo's room** cross-fade instead: a 60-tick fade-out on the old song while the new one starts.
- **Laundry, local script 225** (the "Talk to Men of Low Moral Fiber" button): parts 3, 9 and 10 are switched on at
  volume 0 and raised over 127 script frames (about 8 s) while part 1 drops from 127 to 84; the reverse runs when
  the conversation ends. A script frame is 4/60 s.

## Booty Island

Global scripts 117 (`117(fromRoom, toRoom)`, the room-to-room music script) and 116 (the same for the island map), plus
120 and 122 (the town song's director).

- **The ville plays song 115**, the island theme (228 beats, with a short and a long ending track). It carries on from the
  island map; **nothing the player does in the ville changes it**. Script 117 only starts it when it is not already
  playing.
- **Shops and Spitville play the town song 118** (and its alternate, 117). Going from the ville into a shop cues 115's
  ending (jump hook 1, which jumps to its short ending track at the next 4-beat point) while it fades over 90 ticks, and
  starts 118 a frame later with its own hook 2, which skips the town song's intro. Spitville fades 115 over 60 ticks
  with no hook. Walking back out fades the town song over 60 ticks and starts 115 afresh.
- **Instrument mixes.** Script 120 reads a 90-character string of `1` / `0`, **9 characters per mix**
  (`(mix - 1) * 9`): characters 0-7 are parts 0-7 and character 8 is part 9. Spitville is mix 5, the antique shop 9, the
  costume shop 2; a town song that restarts rolls a random mix 1-3.
- **The town song evolves by itself in the shops** (in Spitville the script only waits). The song has a marker 2 at beat
  136.36, a marker 3 at its loop end (148.4) and a marker 5 every 30 beats. About two beats before the end, script 120's check
  (it runs while the song's position is past beat 134) acts, and the count `L3` decides what:
  - the first two times: pick the next mix by a random walk and queue it on marker 3, so it comes in at the loop end. Each
    mix has a row of letters (one per possible next mix, `A` = mix 1 ... `J` = mix 10; for example mix 8 is `SSSRRRR`).
    Mixes already visited and the current one are skipped. `Z` starts script 122, `S` forgets the visited mixes, and `R`
    lets the music die out (the player skips it);
  - the third time: swap to the alternate song 117 (not while the mix is 9 or 10, which keep walking). 117 starts at marker 2,
    118 stops at the next marker 5, and 117 hands back to a fresh 118 at its marker 3 (beat 144.4). The count restarts.
- **Script 122** (the fast shuffle) applies a random mix from 1 to 5, not repeating within a cycle, every 360 jiffies (6 s).
- The same director also runs in the mansion scenes and the island map, which are not scenes of the player.
- **Funeral parlor** has its own song **116**. Entering fades the music over a second while 116 starts. Leaving arms jump
  hook 1 on 116, waits for it to play out its ending, then the next room's song starts.
- Part 9 of the funeral song is switched off only when a story flag (bit 90) is set; it is not in the player.
- The ville's own check `bit610` (set when you arrive from the island) is what keeps the island song on in the ville;
  without it script 117 would start the town song and its director instead.

The player offers buttons for what the game does by chance in the shops: the next mix now, a chosen mix, the song swap,
the fast shuffle, and a switch for the automatic evolution.

## Swamp (room 20) and the voodoo hut (room 21)

Song **109** starts with parts 1 and 4 only. The room is 792 px wide, the hut 456 px; the player attaches the hut's
picture to the right of the swamp's, so the walk is one panorama of 1248 px.

| Where | Part | Source in the game |
|---|---|---|
| X 165, at the sign | 12 | stepping on the coffin (local script 203; script 204 switches it off on the shore) |
| X 270 | 11 | local script 216, which reads `getActorX` every frame |
| X 470 | 2 | script 216 (goes off again at 470 and below) |
| X 640, the skull's mouth | 0, the lead | room 21's entry script (its exit script switches it off) |
| X 1002 (hut X 210) | 9, 10, 6, plus 0, 11, 12 forced on | the hut's local script 212, every frame: past hut X 210 all six are on; at 210 or less 9, 10 and 6 go off again |

- Entering the hut does not fade the swamp song: the swamp's exit script only fades it when the next room is *not* 21.
- The coffin and the skull are events in the game, not positions. The player turns them into thresholds along the walk;
  they are easy to move (`Swamp.COFFIN_X`, `SKULL_X`).
- Parts 11 and 12 are very quiet instruments; parts 2, 0 and the hut group are the audible steps.
- The game switches part 0 off when you leave the hut; the player keeps it tied to the skull threshold instead.

## Not reproduced

- Woodtick: the cartographer's first-visit variation (jump hook 3 and a part hook, gated on story flags); the inn's
  part 1 / part 3 morph (script 151) when walking to Largo's room.
- Booty Island: the island map and the mansion, which use the same director with songs 114 and 115; the walk's `R` letter
  (the town music dying out); the first-visit variations of the island theme (arming part hooks on arrival); Spitville's
  contest, which restarts the town song afterwards but changes no layers.
- Swamp: the voodoo lady's first-visit cutscene (room 21 script 206), which has its own music.
- Only the AdLib (OPL) renderer is supported. No MT-32 or other MIDI output.

## Tests and helper tools

```bash
# Run every transition and layer change on the real songs, without audio output
MONKEY2_TEST_PATH=/path/to/MONKEY2 ./gradlew test --tests 'adventureexplorer.woodtick.*'

# Print a room's music and iMUSE calls, or one global script
python3 -I tools/scumm5_disasm.py /path/to/MONKEY2 room 20
python3 -I tools/scumm5_disasm.py /path/to/MONKEY2 global 96

# A song's hooks, markers and loops; a room's object positions; notes per channel
./gradlew songdump   --args="/path/to/MONKEY2 101 104"
./gradlew layerdump  --args="/path/to/MONKEY2 20"
./gradlew notecounts --args="/path/to/MONKEY2 109"

# Regenerate the README screenshots, or the app icons from a logo image
./gradlew woodtickshot --args="/path/to/MONKEY2 docs/screenshots"
tools/make_icons.sh path/to/logo.png
```

## Release builds

`.github/workflows/release.yml` builds the macOS `.dmg` and Windows `.msi` on every push to `main` and replaces the
**Latest** release; the `latest` tag moves to that commit. The installer version is `1.0.<run number>`
(`-PappVersion=` in `build.gradle.kts`). The installers are not code-signed.
