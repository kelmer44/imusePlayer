# Woodtick Music Explorer

A small app on top of Adventure Explorer's iMUSE sequencer and Nuked OPL emulator. It shows the
Woodtick rooms of *Monkey Island 2* as a map; click a connected room and the music changes
the way the game's own scripts change it (wait for the next marker, take the hook to the ending
track, start the next song). It reads your own game files, so none are shipped.

Rooms: `woodtick`, `cartograp`, `bar`, `grill`, `inn`, `largos`, `laundry`, `woodshop`.
Needs the **AdLib (DOS)** version, i.e. `MONKEY2.000` + `MONKEY2.001` with `ADL ` music.

```bash
./gradlew runWoodtick -Pgame="/path/to/MONKEY2"      # run from source (game folder optional)
./gradlew packageDmg                      # macOS installer
./gradlew packageMsi                      # Windows installer (build on Windows, needs WiX)
```

## What the scripts do (read with `tools/scumm5_disasm.py`)

| Room | SCUMM room | Song |
|---|---|---|
| Woodtick street | 7 | 101 |
| Cartographer | 8 | 104 |
| Bar (and Grill, which has no song of its own) | 9, 11 | 103 |
| Inn | 12 | 152 |
| Largo's room | 13 | 182 |
| Laundry | 14 | 110 |
| Woodshop | 15 | 111 |

- Global script 96 is `96(fromRoom, toRoom)`. It queues two marker triggers on the song being left,
  then the new song. Marker 0 either jumps straight to the street song's ending
  (track 5, beat 4.400) or arms jump hook 1 on the other songs; marker 1 (end of an ending track)
  stops the old song and starts the new one. The street song starts with hook 7 armed.
- Inn ↔ Largo's room cross-fade (60-tick fade-out on the old song).
- Laundry local script 225: while talking, parts 3, 9, 10 fade in and part 1 drops to 84.

`tools/scumm5_disasm.py <MONKEY2 folder> room <n>` prints a room's music/iMUSE calls;
`./gradlew songdump --args="<folder> 101 104"` lists a song's hooks, markers and loops;
`MONKEY2_TEST_PATH=<folder> ./gradlew test --tests 'adventureexplorer.woodtick.*'` runs every
transition on the real songs without audio.

## Not reproduced

- Cartographer's first-visit variation (hook 3 / part 3 hook, gated on story flags).
- The inn's part 1 / part 3 morph (script 151) when you walk to Largo's room.
- Only the AdLib (OPL) renderer; no MT-32 / MIDI output.
