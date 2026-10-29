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

## Swamp (room 20)

Song 109 starts with only parts 1 and 4 playing; its own setup data switches the other parts off, and the
scripts layer them in as you walk right (the room is 792 px wide):

| Where | Part | Source |
|---|---|---|
| X 165, at the sign | 12 | stepping on the coffin (local script 203; script 204 switches it off on the shore) |
| X 270 | 11 | local script 216 (`getActorX`) |
| X 470 | 2 | local script 216 (goes off again at 470 and below) |
| X 640, the skull's mouth | 0, the lead | room 21's entry script (its exit script switches it off) |

| X 1002 (hut X 210) | 9, 10, 6 (and 0, 11, 12 forced on) | the voodoo hut's local script 212, every frame: past hut X 210 all six are on, at 210 or less 9, 10 and 6 go off again |

The walk continues into the voodoo hut (room 21, 456 px wide), whose picture is attached to the right of the swamp's
at X 792. The same song carries on inside: the swamp's exit script only fades it out when the next room is *not* 21,
and the hut's entry script (part 0 on) and script 212 then keep layering parts into it. Leaving the hut switches
part 0 off again in the game; the scene keeps part 0 tied to the skull threshold instead.

The coffin and the skull are events in the game; the scene turns them into thresholds along the walk, and they are
easy to move (`Swamp.COFFIN_X`, `SKULL_X`). Parts 11 and 12 are very quiet instruments; parts 2 and 0 are the
audible steps. Not reproduced: the voodoo lady's first-visit cutscene (room 21 script 206), which has its own music.

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

## Downloads

Every commit to the main branch builds the installers and publishes them on the **Latest** release
(`.github/workflows/release.yml`): `MI2-Music-Explorer-macos-arm64.dmg` (Apple Silicon) and
`MI2-Music-Explorer-windows.msi`. The `latest` tag always points at the commit they were built from. The installers
are not code-signed, so macOS and Windows warn on first launch.

## Licence

Copyright (C) 2026 gsanmartin and contributors.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See `LICENSE`.

It is built on ScummVM's iMUSE and AdLib code and on Nuked OPL3; see `THIRD_PARTY.md` for the full list and the
licences of the libraries. The game's data files are not included.
