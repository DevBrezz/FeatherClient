# Feather Client v1.4 — by KingBrezz

## Cara kerja
1. `MinecraftGuard` – cek Minecraft asli terinstall (+ signature jika diisi). Gagal -> semua dimatikan.
2. `HostApplication` – memasang classloader yang membaca kode & native lib dari APK Minecraft ASLI
   yang terinstall (tidak ada file Minecraft yang dibundel/disalin). Activity Minecraft
   (`com.mojang.minecraftpe.MainActivity`) dideklarasikan di manifest dan dijalankan di proses kita.
3. `HudOverlay` – FPS, CPS, Jam, Baterai, RAM, Keystrokes, Crosshair kustom sebagai View di atas game
   (tanpa offset memori, tahan update). Posisi bisa digeser lewat tombol ✎ di dalam game. Tema: opacity, ukuran, warna aksen.
4. `libclientmod.so` – thread menunggu `libminecraftpe.so` ter-load, lalu pattern-scan + inline hook
   (dipakai Zoom lewat hook getFov).

## Yang HARUS kamu lengkapi / uji sendiri
- **Belum pernah dikompilasi/dijalankan** oleh penulisnya. Harapkan debugging.
- `HostApplication` memakai reflection ke API internal Android; bisa diblokir di versi Android tertentu.
- Data game (world, setting) akan tersimpan di data app ini, bukan di folder Minecraft asli.
- `hooks_zoom.cpp`: isi `kPatterns` dengan pattern fungsi FOV dari binary versimu.
- Hook backend: taruh `libdobby.a` di `cpp/third_party/dobby/` dan build dengan `-DUSE_DOBBY=ON`.
- `TRUSTED_SIGNATURES` di `MinecraftGuard.kt`: isi hash signature APK asli.
- Koordinat butuh hook native (posisi pemain); Zoom butuh pattern FOV.
- Keystrokes membaca keyboard/gamepad eksternal (kontrol layar sentuh Minecraft tidak bisa dibaca overlay).

Hanya modul visual/QoL; tidak ada fitur cheat.

## Layar utama (v1.4)
Layout: top bar (Launch / Instances / About / Settings / notifikasi / Sign In), kartu hero (Launch + pilih instance),
kartu Mods, Content Management, Miscellaneous.
- **Launch**: cek Minecraft asli -> jalankan game. Jika belum terinstall tombol jadi "Install Minecraft" (Play Store).
- **Instances**: profil modul & tema HUD terpisah (buat/pilih/ubah nama/hapus). Konten dipakai bersama.
- **Mods**: toggle semua modul. **Content**: daftar/impor/hapus world, resource pack, behavior pack
  (.mcworld/.mcpack/.mcaddon; juga lewat "buka dengan" dari browser/file manager).
- **CurseForge**: buka situs di browser. **Microsoft Accounts**: info + buka halaman akun (login sebenarnya di dalam Minecraft).
- **Quick Launch**: langsung buka game saat app dibuka. **Sign In**: profil lokal (tanpa server).
- **Notifikasi**: status Minecraft & hook. **About / Settings**: info, tema HUD, bersihkan cache.

## Hook Zoom & Koordinat (v1.4)
- Backend inline hook **bawaan arm64** (`hook_arm64.cpp`, tanpa Dobby). Menolak dengan aman jika 16 byte awal fungsi
  berisi instruksi PC-relative (log-nya jelas); pakai `adjust=` atau Dobby (`-DUSE_DOBBY=ON`) untuk kasus itu.
- Pattern dibaca dari `patterns.txt` (tanpa rebuild). Format & nama hook: lihat `tools/patterns.example.txt`.
- `tools/make_pattern.py` membuat baris pattern dari `libminecraftpe.so` milikmu + cek keunikan.
- Settings > Hook: import patterns.txt, status hook, dan "Scan pattern" (uji pattern pada game yang sedang jalan).
- Zoom: tombol 🔍 di layar (tahan). Koordinat: dibaca aman (pipe-write) dari `this + pos_offset` pemain lokal.
- **Tidak ada pattern bawaan**: offset berbeda tiap versi; kamu harus menemukannya via disassembler (Ghidra/IDA).
- Signature hook (`fov`, `player_tick`) ada di `cpp/hooks.cpp`; sesuaikan jika fungsi aslinya berbeda.

## v1.5
- **Export world**: ikon 📤 di Content Management > Worlds -> simpan sebagai `.mcworld` (pilih lokasi lewat file picker).
- **Tema menu**: Settings > Tema Menu (Sistem / Gelap / Terang). Menyimpan tab yang sedang dibuka saat tema berganti.

## v1.6 — Menu in-game
Buka dengan tombol 🪶 (kanan atas) atau Shift Kanan; Back menutup.
- **Modules**: kartu modul (tap = nyala/mati, 📌 = pin ke atas & tab Pinned, ⚙ = pengaturan per modul), filter All/HUD/Utility/Pinned, pencarian.
  Pengaturan per modul: ukuran, format jam, desimal koordinat, crosshair (ukuran/celah/ketebalan), level zoom.
- **Tools**: Stopwatch & Countdown (tampil di HUD, getar saat selesai), Screenshot (PixelCopy -> Pictures/FeatherClient), Catatan cepat.
- **Waypoints**: simpan/hapus/salin koordinat; jarak & "Posisi sekarang" butuh hook koordinat.
- **Profiles**: ganti/buat/hapus profil (modul + HUD + posisi) langsung di dalam game.
- **Settings**: sembunyikan HUD, opacity, ukuran global, warna aksen, reset posisi. **Edit HUD**: seret elemen lalu ✓.
Tidak disertakan: Social/Cosmetics (butuh server/hook skin) dan Macros (otomatisasi input) — tidak dibuat pura-pura.

## v1.6.1 — Validasi dependensi Minecraft Bedrock
- `MinecraftGuard.status()` -> `Compatible | NotInstalled | Incompatible(reason, checks) | Error(message)`.
  Pemeriksaan: terinstall (package visibility via `<queries>`), aktif, ABI arm64-v8a (perangkat & Minecraft), `libminecraftpe.so`
  (di nativeLibraryDir atau di dalam APK/split), activity utama via PackageManager, versi minimum (`MIN_VERSION_CODE`, 0 = belum dikonfirmasi),
  signature resmi (opsional, isi `TRUSTED_SIGNATURES`).
- Layar dependensi dengan Coba Lagi, Buka di Google Play (fallback browser), info aplikasi, dan salin diagnostik.
- Peluncuran memverifikasi ulang, memasang loader secara lazy (Minecraft diinstall setelah Feather terbuka), memuat kelas utama sebelum `startActivity`,
  dan menangkap kegagalan library native. Flag `pending` mencegah crash-loop Quick Launch.
- Status "Compatible" = prasyarat statis lolos, bukan jaminan game berjalan. Lihat `docs/TESTING.md`.

## v1.7 — Antarmuka lengkap bergaya Alpine
Registry modul terpusat: `ModuleRegistry.kt` (50 modul; semua layar membaca dari sini). Status per modul: `docs/MODULE_STATUS.md`.
- **Menu in-game** (🪶 / Shift Kanan): sidebar Modules, Social, Cosmetics, Macros, Waypoints, Profiles, Settings, Edit HUD.
  Grid responsif (3/2/1 kolom), filter kategori + pencarian + Pinned bekerja bersamaan, kartu dengan toggle/pin/⚙ terpisah.
  Modul `UNSUPPORTED` redup dan tidak bisa dinyalakan (tap = alasan). Modul `HOOK` hanya bisa dinyalakan saat hook aktif.
- **Pengaturan per modul** bertipe slider/toggle/pilihan/warna; nilai divalidasi (clamp) dan disimpan per profil.
- **Edit HUD**: seret, ukuran (−/+), visibilitas, perataan, layout tersimpan (simpan/muat/hapus), reset default. Posisi disimpan sebagai
  pecahan area aman (tahan rotasi), mempertimbangkan display cutout.
- **Social / Cosmetics / Macros**: halaman jujur (butuh backend / hook render / tidak menyediakan otomatisasi input) dengan fungsi lokal yang nyata.

### Bagaimana HUD dirender (penting)
HUD = View Android di atas jendela Activity Minecraft (`addContentView`), bukan injeksi ke renderer game. Widget hanya tampil selama
`MainActivity` Minecraft berjalan di proses Feather. Data game yang tersedia dari native hanya posisi pemain (hook `player_tick`) dan FOV (hook `fov`),
keduanya butuh `patterns.txt`. Modul lain yang butuh data game (armor, boss bar, ping, minimap, dst.) terdaftar sebagai UNSUPPORTED.
Verifikasi statis: `python3 tools/validate_registry.py`.
