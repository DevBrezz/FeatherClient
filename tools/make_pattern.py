#!/usr/bin/env python3
"""Buat baris patterns.txt dari libminecraftpe.so MILIKMU (ambil dari APK Minecraft yang kamu install).

  python3 make_pattern.py libminecraftpe.so 0x1A2B3C --name fov [--len 24] [--base 0x0] [--adjust 0]
                          [--pos-offset 0x4B0] [--min 0 --max 999999999]

Alamat = vaddr fungsi di disassembler (IDA/Ghidra) dikurangi image base. Instruksi PC-relative
(B/BL/ADR/ADRP/LDR literal/CBZ/TBZ/B.cond) otomatis di-wildcard. Script juga menghitung
berapa kali pattern muncul: harus tepat 1.
"""
import argparse, re, struct, sys

def pc_relative(w):
    return ((w & 0x7C000000) == 0x14000000 or (w & 0x1F000000) == 0x10000000 or
            (w & 0x3B000000) == 0x18000000 or (w & 0x7E000000) == 0x34000000 or
            (w & 0x7E000000) == 0x36000000 or (w & 0xFF000010) == 0x54000000)

def make_tokens(code):
    toks = []
    for i in range(0, len(code) - len(code) % 4, 4):
        w = struct.unpack_from('<I', code, i)[0]
        toks += ['??'] * 4 if pc_relative(w) else ['%02X' % b for b in code[i:i + 4]]
    return toks

def vaddr_to_off(d, va):
    if d[:4] != b'\x7fELF' or d[4] != 2: sys.exit('Bukan ELF64')
    phoff = struct.unpack_from('<Q', d, 0x20)[0]
    phentsize, phnum = struct.unpack_from('<HH', d, 0x36)
    for i in range(phnum):
        t, fl, off, v, _, fsz, msz, _ = struct.unpack_from('<IIQQQQQQ', d, phoff + i * phentsize)
        if t == 1 and v <= va < v + fsz: return off + (va - v)
    sys.exit('Alamat tidak ada di segmen PT_LOAD')

def count(d, toks):
    rx = b''.join(b'.' if t == '??' else re.escape(bytes([int(t, 16)])) for t in toks)
    return len(re.findall(rx, d, re.DOTALL))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('so'); ap.add_argument('addr'); ap.add_argument('--name', default='fov')
    ap.add_argument('--len', type=int, default=24); ap.add_argument('--base', default='0')
    ap.add_argument('--adjust', type=int, default=0); ap.add_argument('--pos-offset')
    ap.add_argument('--min', default='0'); ap.add_argument('--max', default='999999999')
    a = ap.parse_args()
    d = open(a.so, 'rb').read()
    va = int(a.addr, 0) - int(a.base, 0)
    off = vaddr_to_off(d, va)
    toks = make_tokens(d[off:off + a.len])
    n = count(d, toks)
    extra = (f' adjust={a.adjust}' if a.adjust else '') + (f' pos_offset={a.pos_offset}' if a.pos_offset else '')
    print(f'{a.min} {a.max} {a.name}{extra} | {" ".join(toks)}')
    print(f'# cocok {n}x di file ' + ('(OK, unik)' if n == 1 else '-> TIDAK unik/tidak ketemu: naikkan --len atau pilih alamat lain'), file=sys.stderr)

if __name__ == '__main__': main()
