#!/usr/bin/env python3
"""Verifikasi statis (tanpa kompilasi). Jalankan dari root proyek:  python3 tools/validate_registry.py [--status]

1. Registry: ID unik, kategori valid, rentang/default setting, modul UNSUPPORTED tanpa kontrol palsu,
   widget HUD punya sumber teks, kartu PANEL punya halaman, SETIAP setting benar-benar dibaca kode.
2. Simbol: referensi Obj.member lintas file benar-benar ada (menangkap typo/fungsi hilang).
"""
import re, sys, glob, os
SRC = 'app/src/main/java/com/example/bedrockclient/'
files = {os.path.basename(f): open(f).read() for f in glob.glob(SRC + '*.kt')}
reg = files['ModuleRegistry.kt']
STR = r'((?:\\.|[^"\\])*)'
errors, warns = [], []

cats = re.findall(r'"(\w+)"', re.search(r'val CATEGORIES = listOf\(([^)]*)\)', reg).group(1))
COMMON = [('scale', 'slider', 50, 200, 100), ('textsize', 'slider', 8, 24, 13), ('color', 'color', 0, 8, 0)]
mods = []
for line in reg.splitlines():
    m = re.match(r'\s*ModuleInfo\(id = "(\w+)"', line)
    if not m: continue
    g = lambda pat, d=None: (re.search(pat, line) or [None, d])[1] if re.search(pat, line) else d
    seg = line.split('settings = ', 1)[1] if 'settings = ' in line else ''
    settings = []
    if seg.startswith('TEXT'): settings += COMMON
    for k, _, mn, mx, df in re.findall(r'sSlider\("(\w+)", "' + STR + r'", (\d+), (\d+), (\d+)\)', seg): settings.append((k, 'slider', int(mn), int(mx), int(df)))
    for k, _, df in re.findall(r'sToggle\("(\w+)", "' + STR + r'", (true|false)\)', seg): settings.append((k, 'toggle', 0, 1, 1 if df == 'true' else 0))
    for k, _, opts, df in re.findall(r'sChoice\("(\w+)", "' + STR + r'", listOf\(([^)]*)\)(?:, (\d+))?\)', seg):
        n = len(re.findall(r'"', opts)) // 2; settings.append((k, 'choice', 0, n - 1, int(df or 0)))
    if 'sColor()' in seg: settings.append(('color', 'color', 0, 8, 0))
    mods.append(dict(id=m.group(1), title=g(r'title = "' + STR + '"'), cats=re.findall(r'"(\w+)"', g(r'categories = listOf\(([^)]*)\)', '')),
                     impl=g(r'impl = Impl\.(\w+)', 'LOCAL'), hooks=re.findall(r'"(\w+)"', g(r'hooks = listOf\(([^)]*)\)', '')),
                     panel='kind = CardKind.PANEL' in line, hud='hud = true' in line, reason='reason = "' in line, settings=settings))

hud_src, menu_src = files['HudOverlay.kt'], files['GameMenu.kt']
other = '\n'.join(v for k, v in files.items() if k != 'ModuleRegistry.kt')
ids = [m['id'] for m in mods]
for d in {i for i in ids if ids.count(i) > 1}: errors.append(f'id duplikat: {d}')
for m in mods:
    i = m['id']
    if not m['cats']: errors.append(f'{i}: tanpa kategori')
    for c in m['cats']:
        if c not in cats: errors.append(f'{i}: kategori tidak valid {c}')
    if m['impl'] == 'UNSUPPORTED':
        if not m['reason']: errors.append(f'{i}: UNSUPPORTED tanpa reason')
        if m['settings']: errors.append(f'{i}: UNSUPPORTED tapi punya settings (kontrol tanpa efek)')
        if m['hud'] or m['panel']: errors.append(f'{i}: UNSUPPORTED tapi hud/panel')
    if m['impl'] == 'HOOK' and not m['hooks']: errors.append(f'{i}: HOOK tanpa daftar hook')
    keys = [s[0] for s in m['settings']]
    if len(keys) != len(set(keys)): errors.append(f'{i}: key setting duplikat')
    for k, t, mn, mx, df in m['settings']:
        if not (mn <= df <= mx): errors.append(f'{i}.{k}: default {df} di luar {mn}..{mx}')
        if t == 'choice' and mx < 1: errors.append(f'{i}.{k}: pilihan < 2')
        if k in ('scale', 'textsize', 'color'):
            if f'm.id, "{k}"' not in hud_src: errors.append(f'{i}.{k}: setting umum tidak dibaca HudOverlay')
        elif not re.search(r'"%s",\s*"%s"' % (i, k), other): errors.append(f'{i}.{k}: setting TIDAK dibaca kode manapun (kontrol tanpa efek)')
    if m['hud'] and m['impl'] != 'UNSUPPORTED' and i != 'keystrokes' and f'"{i}" ->' not in hud_src: errors.append(f'{i}: widget HUD tanpa sumber teks di HudOverlay.textFor')
    if m['panel'] and f'"{i}" ->' not in menu_src: errors.append(f'{i}: kartu PANEL tanpa halaman di GameMenu.panelPage')
for c in cats:
    if not any(c in m['cats'] for m in mods): errors.append(f'kategori kosong: {c}')

# ---- simbol lintas file ----
members = {}
for fn, src in files.items():
    for obj in re.findall(r'^(?:private )?object (\w+)', src, re.M):
        members[obj] = (fn, set(re.findall(r'(?:fun|val|var|const val)\s+(?:<[^>]+>\s*)?(?:\w+\.)?(\w+)', src)) | set(re.findall(r'\b([A-Z_]{2,})\b', src)))
for en in ('CardKind', 'Impl', 'Avail', 'SettingType'):
    m_ = re.search(r'enum class %s \{([^}]*)\}' % en, reg); members[en] = ('ModuleRegistry.kt', set(re.findall(r'\w+', m_.group(1))))
nested = {'MinecraftGuard': {'Result', 'Ok', 'Fail'}, 'MinecraftStatus': {'Compatible', 'NotInstalled', 'Incompatible', 'Error'}}
for fn, src in files.items():
    for obj, (own, names) in members.items():
        for use in set(re.findall(r'(?<![\w.])%s\.(\w+)' % obj, src)):
            if use not in names and use not in nested.get(obj, set()) and not use[0].isupper():
                errors.append(f'{fn}: {obj}.{use} tidak ditemukan di {own}')
main = files['MainActivity.kt']; main_members = set(re.findall(r'fun (\w+)', main))
android_ok = set('cacheDir filesDir resources packageName window recreate finish runOnUiThread contentResolver packageManager startActivity getSystemService getExternalFilesDir getString'.split())
for use in set(re.findall(r'\ba\.(\w+)\(', files['Screens.kt'])):
    if use not in main_members and use not in android_ok: warns.append(f'Screens.kt: a.{use}() bukan anggota MainActivity (periksa)')

if '--status' in sys.argv:
    out = ['# Status modul Feather Client', '',
           'Dihasilkan otomatis dari `ModuleRegistry.kt` (`python3 tools/validate_registry.py --status`).', '',
           'Status: **NOT TESTED** = kode ada, belum diuji di Minecraft/perangkat. **BLOCKED** = tidak bisa jalan sampai prasyarat terpenuhi. Tidak ada modul berstatus WORKING karena belum ada pengujian runtime.', '',
           '| Modul | Kategori | Status | Catatan |', '|---|---|---|---|']
    for m in mods:
        if m['impl'] == 'UNSUPPORTED': st, note = 'BLOCKED', 'Belum didukung: butuh data/integrasi game yang tidak ada'
        elif m['impl'] == 'HOOK': st, note = 'BLOCKED', 'Butuh hook native (' + ', '.join(m['hooks']) + ') + patterns.txt per versi; kode sudah ada, NOT TESTED'
        else: st, note = 'NOT TESTED', 'Panel/alat' if m['panel'] else 'Diimplementasikan (data lokal Android)'
        out.append(f"| {m['title']} | {', '.join(m['cats'])} | {st} | {note} |")
    open('docs/MODULE_STATUS.md', 'w').write('\n'.join(out) + '\n')

from collections import Counter
print('modul:', len(mods), dict(Counter(m['impl'] for m in mods)), '| panel:', sum(m['panel'] for m in mods), '| hud:', sum(m['hud'] for m in mods))
print('settings terverifikasi:', sum(len(m['settings']) for m in mods))
for w in warns: print('WARN ', w)
for e in errors: print('ERROR', e)
print('HASIL:', 'GAGAL' if errors else 'LOLOS'); sys.exit(1 if errors else 0)
