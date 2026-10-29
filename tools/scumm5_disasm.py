#!/usr/bin/env python3
"""Minimal SCUMM v5 script scanner for Monkey Island 2 (dev tool, not used at runtime).

Decodes the v5 opcode stream just far enough to stay in sync and reports every
music/iMUSE call: startMusic / startSound (sound id), stopSound, soundKludge
(iMUSE commands), plus startScript / loadRoom so the call graph can be followed.

usage (run with -I):
  scumm5_disasm.py <MONKEY2 folder> rooms                list rooms
  scumm5_disasm.py <MONKEY2 folder> room <n>             music calls in every script of a room
  scumm5_disasm.py <MONKEY2 folder> global <n>           music calls in one global script
  scumm5_disasm.py <MONKEY2 folder> find <sound>         every script that starts/stops/kludges a sound
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scumm5_opcodes import OPCODES  # noqa: E402

KEY = 0x69


def load(path):
    return bytes(b ^ KEY for b in open(path, 'rb').read())


def blocks(data, start, end):
    p = start
    while p + 8 <= end:
        tag = data[p:p + 4].decode('latin1')
        size = struct.unpack('>I', data[p + 4:p + 8])[0]
        if size < 8:
            break
        yield tag, p, size
        p += size


class Game:
    def __init__(self, folder):
        names = {n.lower(): n for n in os.listdir(folder)}
        self.idx = load(os.path.join(folder, names['monkey2.000']))
        self.res = load(os.path.join(folder, names['monkey2.001']))
        self.lflf = {}       # room -> (offset, size) of its LFLF block
        self.names = {}
        self.script_loc = {}  # global script id -> (room, SCRP offset)
        self._index()

    def _index(self):
        tag, p, size = next(blocks(self.res, 0, len(self.res)))
        assert tag == 'LECF', tag
        loff, lflfs = None, {}
        for t, q, s in blocks(self.res, p + 8, p + size):
            if t == 'LOFF':
                n = self.res[q + 8]
                loff = {self.res[q + 9 + i * 5]: struct.unpack('<I', self.res[q + 10 + i * 5:q + 14 + i * 5])[0] for i in range(n)}
            elif t == 'LFLF':
                lflfs[q] = s
        for room, off in loff.items():
            self.lflf[room] = (off - 8, lflfs.get(off - 8, 0))  # LOFF points at the ROOM block inside the LFLF
        for t, p0, s in blocks(self.idx, 0, len(self.idx)):
            if t == 'RNAM':
                q = p0 + 8
                while q + 10 <= p0 + s and self.idx[q] != 0:
                    self.names[self.idx[q]] = bytes(b ^ 0xFF for b in self.idx[q + 1:q + 10]).split(b'\0')[0].decode('latin1')
                    q += 10
            elif t == 'DSCR':
                n = struct.unpack('<H', self.idx[p0 + 8:p0 + 10])[0]
                rn = p0 + 10
                roomof = self.idx[rn:rn + n]
                offs = struct.unpack('<%dI' % n, self.idx[rn + n:rn + n * 5])
                for sid in range(n):
                    if roomof[sid] in self.lflf and offs[sid] != 0xFFFFFFFF:
                        self.script_loc[sid] = (roomof[sid], self.lflf[roomof[sid]][0] + 8 + offs[sid])

    def room_scripts(self, room):
        base, size = self.lflf[room]
        for t, p, s in blocks(self.res, base + 8, base + size):
            if t != 'ROOM':
                continue
            for t2, p2, s2 in blocks(self.res, p + 8, p + s):
                if t2 in ('ENCD', 'EXCD'):
                    yield t2, p2 + 8, p2 + s2
                elif t2 == 'LSCR':
                    yield 'LSCR %d' % self.res[p2 + 8], p2 + 9, p2 + s2
                elif t2 == 'OBCD':
                    yield from self._obcd(p2, s2)

    def _obcd(self, p, s):
        objid, name = 0, ''
        for t, q, sz in blocks(self.res, p + 8, p + s):
            if t == 'CDHD':
                objid = struct.unpack('<H', self.res[q + 8:q + 10])[0]
            elif t == 'OBNA':
                name = self.res[q + 8:q + sz].split(b'\0')[0].decode('latin1')
        for t, q, sz in blocks(self.res, p + 8, p + s):
            if t == 'VERB':
                v = q + 8
                while self.res[v] != 0:
                    off = struct.unpack('<H', self.res[v + 1:v + 3])[0]
                    # offsets are relative to the VERB block start (verified: zero desyncs over all 2285 entries)
                    yield 'OBJ %d "%s" verb %d' % (objid, name, self.res[v]), q + off, p + s
                    v += 3

    def global_script(self, sid):
        room, off = self.script_loc[sid]
        size = struct.unpack('>I', self.res[off + 4:off + 8])[0]
        return room, off + 8, off + size


class Stop(Exception):
    pass


class Dis:
    def __init__(self, data, pos, end, trace=False):
        self.d, self.p, self.end = data, pos, end
        self.base = pos
        self.cur = []
        self.trace = trace
        self.lines = []

    # -- primitives
    def b(self):
        v = self.d[self.p]; self.p += 1; return v

    def w(self):
        v = struct.unpack('<H', self.d[self.p:self.p + 2])[0]; self.p += 2; return v

    def ws(self):
        v = struct.unpack('<h', self.d[self.p:self.p + 2])[0]; self.p += 2; return v

    @staticmethod
    def vname(v):
        v &= ~0x2000
        if v & 0x8000: return 'bit%d' % (v & 0x7FFF)
        if v & 0x4000: return 'L%d' % (v & 0xF)
        return 'V%d' % v

    def var(self):
        v = self.w()
        txt = self.vname(v)
        if v & 0x2000:
            a = self.w()
            txt += '[%s]' % self.vname(a) if a & 0x2000 else '[%d]' % (a & 0xFFF)
        return txt

    def pb(self, op, mask):
        v = self.var() if op & mask else self.b()
        self.cur.append(v); return v

    def pw(self, op, mask):
        v = self.var() if op & mask else self.ws()
        self.cur.append(v); return v

    def vararg(self):
        items = []
        while True:
            o = self.b()
            if o == 0xFF:
                return items
            items.append(self.pw(o, 0x80))

    def cstring(self, esc=True):
        while True:
            c = self.b()
            if c == 0:
                return
            if esc and c == 0xFF:
                c2 = self.b()
                if c2 not in (1, 2, 3, 8):
                    self.p += 2

    def jump(self):
        off = self.ws()
        self.jt = self.p + off
        self.cur.append('->%04x' % (self.jt - self.base))

    # -- main loop
    def run(self):
        events = []
        try:
            while self.p < self.end:
                at = self.p
                op = self.b()
                name = OPCODES[op]
                self.cur = []
                r = getattr(self, 'o_' + name, self.o_unknown)(op)
                if self.trace:
                    self.lines.append('%04x  %-16s %s' % (at - self.base, name, ' '.join(map(str, self.cur)) if not r else d_fmt(r)))
                if r is not None:
                    events.append((at - 0, r))
        except Stop:
            pass
        except (IndexError, struct.error, KeyError) as e:
            events.append((self.p, ('desync', repr(e))))
        return events

    def o_unknown(self, op):
        raise KeyError('unknown opcode handler %s (0x%02X)' % (OPCODES[op], op))

    # -- opcodes (layout from script_v5.cpp)
    def o_stopObjectCode(self, op): raise Stop()
    def o_putActor(self, op): self.pb(op, 0x80); self.pw(op, 0x40); self.pw(op, 0x20)
    def o_startMusic(self, op): return ('startMusic', self.pb(op, 0x80))
    def o_startSound(self, op): return ('startSound', self.pb(op, 0x80))
    def o_stopMusic(self, op): return ('stopMusic',)
    def o_stopSound(self, op): return ('stopSound', self.pb(op, 0x80))
    def o_isSoundRunning(self, op): self.var(); self.pb(op, 0x80)
    def o_soundKludge(self, op): return ('soundKludge', self.vararg())
    def o_getActorRoom(self, op): self.var(); self.pb(op, 0x80)
    def o_getActorElevation(self, op): self.var(); self.pb(op, 0x80)
    def o_getActorCostume(self, op): self.var(); self.pb(op, 0x80)
    def o_getActorFacing(self, op): self.var(); self.pb(op, 0x80)
    def o_getActorMoving(self, op): self.var(); self.pb(op, 0x80)
    def o_getActorScale(self, op): self.var(); self.pb(op, 0x80)
    def o_getActorWalkBox(self, op): self.var(); self.pb(op, 0x80)
    def o_getActorWidth(self, op): self.var(); self.pb(op, 0x80)
    def o_getActorX(self, op): self.var(); self.pw(op, 0x80)
    def o_getActorY(self, op): self.var(); self.pw(op, 0x80)
    def o_getAnimCounter(self, op): self.var(); self.pb(op, 0x80)
    def o_getInventoryCount(self, op): self.var(); self.pb(op, 0x80)
    def o_getObjectOwner(self, op): self.cur.append(self.var()); self.pw(op, 0x80)
    def o_getObjectState(self, op): self.var(); self.pw(op, 0x80)
    def o_getRandomNr(self, op): self.var(); self.pb(op, 0x80)
    def o_isScriptRunning(self, op): self.cur.append(self.var()); self.pb(op, 0x80)
    def o_getStringWidth(self, op): self.var(); self.pb(op, 0x80)
    def o_getVerbEntrypoint(self, op): self.var(); self.pw(op, 0x80); self.pw(op, 0x40)
    def o_getDist(self, op): self.var(); self.pw(op, 0x80); self.pw(op, 0x40)
    def o_getClosestObjActor(self, op): self.var(); self.pw(op, 0x80)
    def o_actorFromPos(self, op): self.var(); self.pw(op, 0x80); self.pw(op, 0x40)
    def o_findObject(self, op): self.var(); self.pb(op, 0x80); self.pb(op, 0x40)
    def o_findInventory(self, op): self.var(); self.pb(op, 0x80); self.pb(op, 0x40)
    def o_getActorWalkBoxX(self, op): self.var(); self.pb(op, 0x80)

    def _cmp(self, op): self.cur.append(self.var()); self.cur.append(OPCODES[op][2:]); self.pw(op, 0x80); self.jump()
    o_isGreaterEqual = o_isNotEqual = o_isEqual = o_isGreater = o_isLess = o_isLessEqual = _cmp

    def o_equalZero(self, op): self.cur.append(self.var()); self.jump()
    o_notEqualZero = o_equalZero
    def o_isActorInBox(self, op): self.pb(op, 0x80); self.pb(op, 0x40); self.jump()
    def o_jumpRelative(self, op): self.jump()

    def o_ifClassOfIs(self, op):
        self.pw(op, 0x80)
        while True:
            o = self.b()
            if o == 0xFF:
                break
            self.pw(o, 0x80)
        self.jump()

    def o_drawObject(self, op):
        self.pw(op, 0x80)
        sub = self.b()
        s = sub & 0x1F
        if s == 1: self.pw(sub, 0x80); self.pw(sub, 0x40)
        elif s == 2: self.pw(sub, 0x80)
        elif s == 0x1F: pass
        else: raise KeyError('drawObject sub %d' % s)

    def o_ifState(self, op): self.pw(op, 0x80); self.pb(op, 0x40); self.jump()
    o_ifNotState = o_ifState
    def o_setState(self, op): self.pw(op, 0x80); self.pb(op, 0x40)
    def o_faceActor(self, op): self.pb(op, 0x80); self.pw(op, 0x40)
    def o_animateActor(self, op): self.pb(op, 0x80); self.pb(op, 0x40)
    def o_panCameraTo(self, op): self.pw(op, 0x80)
    def o_setCameraAt(self, op): self.pw(op, 0x80)
    def o_actorFollowCamera(self, op): self.pb(op, 0x80)
    def o_freezeScripts(self, op): self.pb(op, 0x80)
    def o_stopScript(self, op): self.pb(op, 0x80)
    def o_stopObjectScript(self, op): self.pw(op, 0x80)
    def o_debug(self, op): self.pw(op, 0x80)
    def o_lights(self, op): self.pb(op, 0x80); self.b(); self.b()
    def o_loadRoom(self, op): return ('loadRoom', self.pb(op, 0x80))
    def o_loadRoomWithEgo(self, op):
        obj = self.pw(op, 0x80); room = self.pb(op, 0x40); self.ws(); self.ws()
        return ('loadRoomWithEgo', room, obj)
    def o_pickupObjectOld(self, op): self.pw(op, 0x80); self.pb(op, 0x40)
    def o_pickupObject(self, op): self.pw(op, 0x80); self.pb(op, 0x40)
    def o_putActorAtObject(self, op): self.pb(op, 0x80); self.pw(op, 0x40)
    def o_putActorInRoom(self, op): self.pb(op, 0x80); self.pb(op, 0x40)
    def o_walkActorTo(self, op): self.pb(op, 0x80); self.pw(op, 0x40); self.pw(op, 0x20)
    def o_walkActorToActor(self, op): self.pb(op, 0x80); self.pb(op, 0x40); self.b()
    def o_walkActorToObject(self, op): self.pb(op, 0x80); self.pw(op, 0x40)
    def o_setOwnerOf(self, op): self.pw(op, 0x80); self.pb(op, 0x40)
    def o_drawBox(self, op):
        self.pw(op, 0x80); self.pw(op, 0x40)
        o = self.b()
        self.pw(o, 0x80); self.pw(o, 0x40); self.pb(o, 0x20)
    def o_doSentence(self, op):
        verb = self.pb(op, 0x80)
        if verb == 0xFE:
            return
        self.pw(op, 0x40); self.pw(op, 0x20)
    def o_delay(self, op): self.p += 3
    def o_delayVariable(self, op): self.var()
    def o_breakHere(self, op): pass
    def o_dummy(self, op): pass
    def o_endCutscene(self, op): pass
    def o_beginOverride(self, op): self.b()
    def o_cutscene(self, op): self.vararg()
    def o_pseudoRoom(self, op):
        self.b()
        while self.b() != 0:
            pass
    def o_printEgo(self, op): self._print()
    def o_print(self, op): self.pb(op, 0x80); self._print()

    def _print(self):
        while True:
            o = self.b()
            if o == 0xFF:
                return
            s = o & 0xF
            if s == 0: self.pw(o, 0x80); self.pw(o, 0x40)
            elif s == 1: self.pb(o, 0x80)
            elif s == 2: self.pw(o, 0x80)
            elif s == 3: self.pw(o, 0x80); self.pw(o, 0x40)
            elif s in (4, 6) and s == 6: self.pw(o, 0x80)
            elif s in (4, 7): pass
            elif s == 8: self.pw(o, 0x80); self.pw(o, 0x40)
            elif s == 15: self.cstring(); return
            else: raise KeyError('print sub %d' % s)

    def o_startScript(self, op):
        sid = self.pb(op, 0x80); args = self.vararg()
        return ('startScript', sid, args)
    def o_chainScript(self, op):
        sid = self.pb(op, 0x80); args = self.vararg()
        return ('startScript', sid, args)
    def o_startObject(self, op): self.pw(op, 0x80); self.pb(op, 0x40); self.vararg()
    def o_move(self, op): self.cur.append(self.var()); self.pw(op, 0x80)
    def _arith(self, op): self.var(); self.pw(op, 0x80)
    o_add = o_subtract = o_multiply = o_divide = o_and = o_or = _arith
    def o_increment(self, op): self.var()
    o_decrement = o_increment
    def o_setVarRange(self, op):
        self.var(); n = self.b()
        for _ in range(n):
            self.ws() if op & 0x80 else self.b()

    def o_expression(self, op):
        self.cur.append(self.var() + ' =')
        while True:
            o = self.b()
            if o == 0xFF:
                return
            s = o & 0x1F
            if s == 1: self.pw(o, 0x80)
            elif s in (2, 3, 4, 5): self.cur.append('+-*/'[s - 2])
            elif s == 6:
                sub = self.b()
                getattr(self, 'o_' + OPCODES[sub], self.o_unknown)(sub)
            else: raise KeyError('expression sub %d' % s)

    def o_setClass(self, op):
        self.pw(op, 0x80)
        while True:
            o = self.b()
            if o == 0xFF: return
            self.pw(o, 0x80)

    def o_actorOps(self, op):
        self.pb(op, 0x80)
        while True:
            o = self.b()
            if o == 0xFF: return
            s = o & 0x1F
            one_b = {1, 3, 4, 6, 12, 14, 16, 19, 22, 23}
            if s == 0: self.pb(o, 0x80)
            elif s in one_b: self.pb(o, 0x80)
            elif s in (2, 5): self.pb(o, 0x80); self.pb(o, 0x40)
            elif s == 7: self.pb(o, 0x80); self.pb(o, 0x40); self.pb(o, 0x20)
            elif s in (8, 10, 18, 20, 21): pass
            elif s == 9: self.pw(o, 0x80)
            elif s == 11: self.pb(o, 0x80); self.pb(o, 0x40)
            elif s == 13: self.cstring(esc=False)
            elif s == 17: self.pb(o, 0x80)
            else: raise KeyError('actorOps sub %d' % s)

    def o_cursorCommand(self, op):
        o = self.b(); s = o & 0x1F
        if s in (1, 2, 3, 4, 5, 6, 7, 8): pass
        elif s == 10: self.pb(o, 0x80); self.pb(o, 0x40)
        elif s == 11: self.pb(o, 0x80); self.pb(o, 0x40); self.pb(o, 0x20)
        elif s in (12, 13): self.pb(o, 0x80)
        elif s == 14: self.vararg()
        else: raise KeyError('cursorCommand %d' % s)

    def o_matrixOps(self, op):
        o = self.b(); s = o & 0x1F
        if s in (1, 2, 3): self.pb(o, 0x80); self.pb(o, 0x40)
        elif s == 4: pass
        else: raise KeyError('matrixOps %d' % s)

    def o_resourceRoutines(self, op):
        o = self.b()
        if o != 17:
            self.pb(o, 0x80)
        s = o & 0x3F
        if s == 20: self.pw(o, 0x40)
        elif s == 36 or s == 37: self.pb(o, 0x40); self.b() if s == 36 else None

    def o_roomOps(self, op):
        o = self.b(); s = o & 0x1F
        if s in (1, 2, 3): self.pw(o, 0x80); self.pw(o, 0x40)
        elif s == 4:
            self.pw(o, 0x80); self.pw(o, 0x40); self.pw(o, 0x20)
            o2 = self.b(); self.pb(o2, 0x80)
        elif s in (5, 6): pass
        elif s == 7:
            self.pb(o, 0x80); self.pb(o, 0x40)
            o2 = self.b(); self.pb(o2, 0x80); self.pb(o2, 0x40)
            o3 = self.b(); self.pb(o3, 0x40)
        elif s == 8: self.pb(o, 0x80); self.pb(o, 0x40); self.pb(o, 0x20)
        elif s == 9: self.pb(o, 0x80); self.pb(o, 0x40)
        elif s == 10: self.pw(o, 0x80)
        elif s in (11, 12):
            self.pw(o, 0x80); self.pw(o, 0x40); self.pw(o, 0x20)
            o2 = self.b(); self.pb(o2, 0x80); self.pb(o2, 0x40)
        elif s in (13, 14): self.pb(o, 0x80); self.cstring(esc=False)
        elif s == 15:
            self.pb(o, 0x80)
            o2 = self.b(); self.pb(o2, 0x80); self.pb(o2, 0x40)
            o3 = self.b(); self.pb(o3, 0x80)
        elif s == 16: self.pb(o, 0x80); self.pb(o, 0x40)
        else: raise KeyError('roomOps %d' % s)

    def o_saveRestoreVerbs(self, op):
        o = self.b(); self.pb(o, 0x80); self.pb(o, 0x40); self.pb(o, 0x20)

    def o_setObjectName(self, op): self.pw(op, 0x80); self.cstring(esc=False)
    def o_stringOps(self, op):
        o = self.b(); s = o & 0x1F
        if s == 1: self.pb(o, 0x80); self.cstring()
        elif s == 2: self.pb(o, 0x80); self.pb(o, 0x40)
        elif s == 3: self.pb(o, 0x80); self.pb(o, 0x40); self.pb(o, 0x20)
        elif s == 4: self.var(); self.pb(o, 0x80); self.pb(o, 0x40)
        elif s == 5: self.pb(o, 0x80); self.pb(o, 0x40)
        else: raise KeyError('stringOps %d' % s)

    def o_systemOps(self, op): self.b()

    def o_verbOps(self, op):
        self.pb(op, 0x80)
        while True:
            o = self.b()
            if o == 0xFF: return
            s = o & 0x1F
            if s == 1: self.pw(o, 0x80)
            elif s == 2: self.cstring(esc=False)
            elif s in (3, 4, 16, 18, 23): self.pb(o, 0x80)
            elif s == 5: self.pw(o, 0x80); self.pw(o, 0x40)
            elif s in (6, 7, 8, 9, 17, 19): pass
            elif s == 20: self.pw(o, 0x80)
            elif s == 22: self.pw(o, 0x80); self.pb(o, 0x40)
            else: raise KeyError('verbOps %d' % s)

    def o_wait(self, op):
        o = self.b(); s = o & 0x1F
        if s == 1: self.pb(o, 0x80)
        elif s in (2, 3, 4): pass
        else: raise KeyError('wait %d' % s)


def d_fmt(r):
    return ' '.join(str(x) for x in r)


def scan(game, label, start, end):
    return Dis(game.res, start, end).run()


def fmt(ev):
    if ev[0] == 'desync':
        return 'DESYNC ' + ev[1]
    return ' '.join(str(x) for x in ev)


def music_events(events):
    return [(at, e) for at, e in events if e[0] in ('startMusic', 'startSound', 'stopMusic', 'stopSound', 'soundKludge',
                                                    'startScript', 'loadRoom', 'loadRoomWithEgo', 'desync')]


def main(argv):
    game = Game(argv[1])
    cmd = argv[2]
    if cmd == 'rooms':
        for r in sorted(game.lflf):
            print(r, game.names.get(r, '?'))
    elif cmd == 'room':
        room = int(argv[3])
        print('room', room, game.names.get(room))
        for label, s, e in game.room_scripts(room):
            ev = music_events(scan(game, label, s, e))
            if ev:
                print('  [%s]' % label)
                for at, x in ev:
                    print('     %05x  %s' % (at - s, fmt(x)))
    elif cmd == 'global':
        sid = int(argv[3])
        room, s, e = game.global_script(sid)
        print('global script', sid, 'in room', room)
        for at, x in music_events(scan(game, 'g', s, e)):
            print('     %05x  %s' % (at - s, fmt(x)))
    elif cmd == 'find':
        want = int(argv[3])
        for room in sorted(game.lflf):
            for label, s, e in game.room_scripts(room):
                for at, x in scan(game, label, s, e):
                    if x[0] in ('startMusic', 'startSound', 'stopSound') and x[1] == want:
                        print('room %d (%s) [%s] %05x %s' % (room, game.names.get(room), label, at - s, fmt(x)))
        for sid in sorted(game.script_loc):
            room, s, e = game.global_script(sid)
            for at, x in scan(game, 'g', s, e):
                if x[0] in ('startMusic', 'startSound', 'stopSound') and x[1] == want:
                    print('global %d (room %d) %05x %s' % (sid, room, at - s, fmt(x)))


if __name__ == '__main__':
    main(sys.argv)
