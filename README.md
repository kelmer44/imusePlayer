# Monkey Island 2 Music Explorer

A small app on top of Adventure Explorer's iMUSE sequencer and Nuked OPL emulator. It shows the
Woodtick rooms of *Monkey Island 2* as a map; click a connected room and the music changes
the way the game's own scripts change it (wait for the next marker, take the hook to the ending
track, start the next song). It reads your own game files, so none are shipped.

Scenes are switched with the pills under the title. **Woodtick** rooms: `woodtick`, `cartograp`, `bar`, `grill`, `inn`, `largos`, `laundry`, `woodshop`.
Needs the **AdLib (DOS)** version, i.e. `MONKEY2.000` + `MONKEY2.001` with `ADL ` music.

```bash
./gradlew runWoodtick -Pgame="/path/to/MONKEY2"      # run from source (game folder optional)
./gradlew packageDmg                      # macOS installer
./gradlew packageMsi                      # Windows installer (build on Windows, needs WiX)
```

**Booty Island** rooms: `ville` (46), `spitvile` (47), `antique` (48), `costume-s` (49), `funeral-p` (50).

## Booty Island (global scripts 117 and 120)

- Ville, Spitville and both shops share song **118**; the game never restarts it when you walk between them,
  it only switches instrument parts. Script 120 reads a 90-character `1`/`0` string, 8 characters per group:
  ville group 3, Spitville 5, antique 9, costume 2 (parts 0-7).
- The funeral parlor has its own song **116**. Entering cross-fades (1 s fade of 118). Leaving arms jump hook 1
  on 116, waits for it to play out its ending, then starts 118.
- Not reproduced: script 120's adaptive layer (it also cycles between songs 114/117/118 and adds layers
  when you pass objects), and Booty Island's outdoor theme (115) because the island map isn't a scene yet.

## What the scripts do (Woodtick) (read with `tools/scumm5_disasm.py`)

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
