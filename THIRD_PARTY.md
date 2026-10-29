# Third-party code and credits

MI2 Music Explorer is free software, released under the **GNU General Public License, version 3 or (at your
option) any later version** (see `LICENSE`). It contains ports of, and is derived from, the projects below.
Their copyright notices stay in the source files; do not remove them.

## ScummVM (GPL-2.0-or-later / GPL-3.0-or-later)

Copyright (C) the ScummVM Team. https://www.scummvm.org — https://github.com/scummvm/scummvm

| File | Derived from |
|---|---|
| `audio/imuse/ImuseSequencer.kt`, `ImuseSong.kt` | iMUSE player, parts and SysEx handler: `engines/scumm/imuse/imuse_player.cpp`, `imuse_part.cpp`, `sysex_scumm.cpp` (GPLv2+), plus the timing and jump parts of `MidiParser` |
| `audio/imuse/AdlibOplDevice.kt` | `MidiDriver_ADLIB`, `audio/adlib.cpp` (GPLv2+) |
| `audio/ScummAdPlayer.kt` | `Player_AD`, `engines/scumm/players/player_ad.cpp` (GPLv3+) |
| `audio/CmfOplDevice.kt` | register layout of `engines/igor/midi.cpp` (GPLv3+) |
| `audio/ImuseBundleCodecs.kt` | `BundleCodecs::decompressCodec`, `engines/scumm/imuse_digi/dimuse_codecs.cpp` |
| `audio/GobAdlDecoder.kt` | Gob `AdLib` class, `engines/gob/sound/adlib.cpp` (GPLv2+) |
| `audio/LastExpressAdpcm.kt`, `audio/GmfToMidiConverter.kt`, `video/CoktelVideoDecoder.kt`, `models/GrimModelDecoder.kt` | format references: Last Express mixer, AGOS GMF parser, Coktel video decoder, Grim model loader |
| `tools/scumm5_opcodes.py`, `tools/scumm5_disasm.py` | opcode table and operand layouts of `engines/scumm/script_v5.cpp`; the iMUSE command meanings of `engines/scumm/imuse/imuse.cpp` |

The Woodtick, Booty Island and swamp music logic (`woodtick/` package) was written by reading the games'
scripts with that disassembler; it is part of this GPL work too.

## Nuked OPL3 (LGPL-2.1-or-later)

`audio/Opl3.kt` is a Kotlin port of Nuked OPL3 1.8. Copyright (C) 2013-2018 Alexey Khokholov (Nuke.YKT).
https://github.com/nukeykt/Nuked-OPL3. Used and distributed here under the terms of the GPL, which the LGPL
permits.

## Libraries (not modified, linked at build time)

| Library | Licence |
|---|---|
| Kotlin standard library, kotlinx.coroutines | Apache-2.0 |
| JetBrains Compose Multiplatform (Desktop, Material), Skiko / Skia | Apache-2.0 / BSD-3-Clause |
| LuaJ (`luaj-jse`) | MIT |
| JLayer (`javazoom:jlayer`) | LGPL |

Apache-2.0 is compatible with GPL version 3, which is why the project is GPL-3.0-or-later and not GPL-2.0-only.

## Not covered by the GPL

- **Game data.** None of Monkey Island 2's files are included. The program reads the player's own copy.
- **Names and artwork.** *Monkey Island*, *LucasArts* and the logo used as the application icon belong to their
  respective owners. The icon is not part of the GPL-licensed work and is shown only to identify the game's era;
  replace it before any public release.

## Source

The complete corresponding source for any binary of this program is the repository it was built from. Anyone
who receives a build is entitled to it under the GPL.
