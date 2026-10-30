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

Global scripts 117 (`117(fromRoom, toRoom)`) and 120 (a layer director that runs while you are in town).

- **Ville, Spitville and both shops** share song **118**. The game never restarts it when you walk between them; script 120
  only switches parts when the ego's room changes. It reads a 90-character string of `1` / `0`, **9 characters per
  group** (`(group - 1) * 9`): characters 0-7 are parts 0-7 and character 8 is part 9.
  - Ville: a random group 1-3, rolled when the town song starts.
  - Spitville: group 5. Antique shop: group 9. Costume shop: group 2.
- **Funeral parlor** has its own song **116**. Entering fades 118 out over a second while 116 starts. Leaving arms jump
  hook 1 on 116, waits for it to play out its ending, then starts 118.
- Part 9 of the funeral song is switched off only when a story flag (bit 90) is set; it is not in the player.

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
- Booty Island: script 120's adaptive layer (it also cycles between songs 114, 117 and 118 and adds layers when you pass
  objects); the island's outdoor theme (115), because the island map is not a scene.
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
