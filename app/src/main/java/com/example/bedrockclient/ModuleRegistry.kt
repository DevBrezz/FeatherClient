package com.example.bedrockclient

enum class SettingType { SLIDER, TOGGLE, CHOICE, COLOR }
data class Setting(val key: String, val label: String, val type: SettingType, val min: Int, val max: Int, val def: Int, val options: List<String> = emptyList())
fun sSlider(key: String, label: String, min: Int, max: Int, def: Int) = Setting(key, label, SettingType.SLIDER, min, max, def)
fun sToggle(key: String, label: String, def: Boolean) = Setting(key, label, SettingType.TOGGLE, 0, 1, if (def) 1 else 0)
fun sChoice(key: String, label: String, options: List<String>, def: Int = 0) = Setting(key, label, SettingType.CHOICE, 0, options.size - 1, def, options)
fun sColor(key: String = "color", label: String = "Warna") = Setting(key, label, SettingType.COLOR, 0, Palette.hex.size - 1, 0)

object Palette {
    val hex = listOf("", "#FFFFFF", "#4FC3F7", "#81C784", "#FFD54F", "#FFB74D", "#F06292", "#B388FF", "#EF5350")   // 0 = warna tema
    val names = listOf("Tema", "Putih", "Cyan", "Hijau", "Kuning", "Oranye", "Pink", "Ungu", "Merah")
}

/** LOCAL = memakai data Android/app saja. HOOK = butuh hook native + patterns.txt. UNSUPPORTED = belum ada sumber data/integrasi. */
enum class Impl { LOCAL, HOOK, UNSUPPORTED }
/** TOGGLE = kartu on/off. PANEL = kartu membuka halaman/alat (bukan on/off). */
enum class CardKind { TOGGLE, PANEL }
enum class Avail { AVAILABLE, NEEDS_HOOK, UNSUPPORTED }
data class Availability(val state: Avail, val message: String)

data class ModuleInfo(
    val id: String, val title: String, val desc: String, val icon: String,
    val categories: List<String>,
    val impl: Impl = Impl.LOCAL,
    val hooks: List<String> = emptyList(),
    val reason: String = "",
    val kind: CardKind = CardKind.TOGGLE,
    val defaultOn: Boolean = false,
    val hud: Boolean = false,
    val settings: List<Setting> = emptyList()
) {
    val needsNative get() = impl == Impl.HOOK
}

/** Satu-satunya daftar modul. Semua layar (menu in-game, launcher, HUD, layout, config) membaca dari sini. */
object ModuleRegistry {
    val CATEGORIES = listOf("HUD", "Combat", "Gamemode", "Mechanic", "Visual", "Movement", "Utility")
    private const val NA_GAME = "Butuh data dari dalam game yang belum diekspos oleh integrasi native Feather."
    private val TEXT = listOf(sSlider("scale", "Ukuran widget (%)", 50, 200, 100), sSlider("textsize", "Ukuran teks (sp)", 8, 24, 13), sColor())

    val all: List<ModuleInfo> = listOf(
        // ---------------- HUD ----------------
        ModuleInfo(id = "armor", title = "Armor Status", icon = "🛡", categories = listOf("HUD"), desc = "Status armor yang dipakai", impl = Impl.UNSUPPORTED, reason = "Butuh data inventori/armor pemain. $NA_GAME"),
        ModuleInfo(id = "blockcoords", title = "Block Coordinates", icon = "🧱", categories = listOf("HUD"), desc = "Koordinat blok (dibulatkan)", impl = Impl.HOOK, hooks = listOf("player_tick"), hud = true, settings = TEXT),
        ModuleInfo(id = "bossbar", title = "Boss Bar", icon = "☠", categories = listOf("HUD"), desc = "Bar nyawa boss", impl = Impl.UNSUPPORTED, reason = "Butuh data boss bar. $NA_GAME"),
        ModuleInfo(id = "clock", title = "Clock", icon = "🕒", categories = listOf("HUD"), desc = "Waktu perangkat", defaultOn = true, hud = true, settings = TEXT + listOf(sToggle("h24", "Format 24 jam", true), sToggle("seconds", "Tampilkan detik", false))),
        ModuleInfo(id = "combo", title = "Combo Count", icon = "✨", categories = listOf("HUD", "Combat"), desc = "Jumlah pukulan beruntun", impl = Impl.UNSUPPORTED, reason = "Butuh event serangan/hit dari game. $NA_GAME"),
        ModuleInfo(id = "compass", title = "Compass", icon = "🧭", categories = listOf("HUD"), desc = "Kompas arah pandang", impl = Impl.UNSUPPORTED, reason = "Butuh arah pandang (yaw) pemain. $NA_GAME"),
        ModuleInfo(id = "coords", title = "Coordinates", icon = "📍", categories = listOf("HUD"), desc = "Posisi pemain", impl = Impl.HOOK, hooks = listOf("player_tick"), hud = true, settings = TEXT + listOf(sChoice("format", "Format koordinat", listOf("XYZ: x / y / z", "x y z", "X: x  Y: y  Z: z"), 0), sSlider("decimals", "Angka desimal", 0, 2, 1))),
        ModuleInfo(id = "cps", title = "CPS", icon = "🖱", categories = listOf("HUD", "Combat"), desc = "Tap/klik per detik", defaultOn = true, hud = true, settings = TEXT + listOf(sToggle("label", "Tampilkan label \"CPS:\"", true))),
        ModuleInfo(id = "daycounter", title = "Day Counter", icon = "🌅", categories = listOf("HUD"), desc = "Hari ke-berapa di world", impl = Impl.UNSUPPORTED, reason = "Butuh waktu world. $NA_GAME"),
        ModuleInfo(id = "entitycount", title = "Entity Count", icon = "👾", categories = listOf("HUD"), desc = "Jumlah entitas di sekitar", impl = Impl.UNSUPPORTED, reason = "Butuh daftar entitas. $NA_GAME"),
        ModuleInfo(id = "fps", title = "FPS", icon = "📈", categories = listOf("HUD"), desc = "Frame per detik", defaultOn = true, hud = true, settings = TEXT + listOf(sToggle("label", "Tampilkan label \"FPS:\"", true), sSlider("interval", "Interval pembaruan (ms)", 250, 2000, 1000))),
        ModuleInfo(id = "infohud", title = "Info HUD", icon = "📋", categories = listOf("HUD"), desc = "Panel ringkas beberapa info", hud = true, settings = TEXT + listOf(sToggle("show_fps", "Baris FPS", true), sToggle("show_cps", "Baris CPS", true), sToggle("show_bat", "Baris baterai", true), sToggle("show_ram", "Baris RAM", true), sToggle("show_net", "Baris jaringan", false))),
        ModuleInfo(id = "keystrokes", title = "Keystrokes", icon = "⌨", categories = listOf("HUD"), desc = "WASD + Space (keyboard/gamepad eksternal)", hud = true, settings = listOf(sSlider("scale", "Ukuran widget (%)", 50, 200, 100), sColor())),
        ModuleInfo(id = "memory", title = "Memory", icon = "💾", categories = listOf("HUD"), desc = "Pemakaian RAM game (MB)", hud = true, settings = TEXT),
        ModuleInfo(id = "minimap", title = "Minimap", icon = "🗺", categories = listOf("HUD"), desc = "Peta mini sekitar pemain", impl = Impl.UNSUPPORTED, reason = "Butuh data chunk/blok dunia. $NA_GAME"),
        ModuleInfo(id = "potions", title = "Potion Effects", icon = "🧪", categories = listOf("HUD"), desc = "Efek potion aktif", impl = Impl.UNSUPPORTED, reason = "Butuh daftar efek status pemain. $NA_GAME"),
        ModuleInfo(id = "ping", title = "Ping", icon = "📡", categories = listOf("HUD"), desc = "Latensi ke server", impl = Impl.UNSUPPORTED, reason = "Butuh alamat server/data jaringan game. $NA_GAME"),
        ModuleInfo(id = "session", title = "Session Timer", icon = "⏲", categories = listOf("HUD"), desc = "Lama sesi bermain", hud = true, settings = TEXT + listOf(sToggle("label", "Tampilkan label", true))),
        ModuleInfo(id = "speed", title = "Speed Display", icon = "💨", categories = listOf("HUD"), desc = "Kecepatan horizontal (blok/detik)", impl = Impl.HOOK, hooks = listOf("player_tick"), hud = true, settings = TEXT + listOf(sSlider("decimals", "Angka desimal", 0, 2, 1))),
        ModuleInfo(id = "direction", title = "Direction Indicator", icon = "➤", categories = listOf("HUD"), desc = "Arah hadap (N/E/S/W)", impl = Impl.UNSUPPORTED, reason = "Butuh arah pandang (yaw) pemain. $NA_GAME"),
        ModuleInfo(id = "modulelist", title = "Module List", icon = "☰", categories = listOf("HUD"), desc = "Daftar modul yang aktif", hud = true, settings = TEXT),
        ModuleInfo(id = "battery", title = "Battery", icon = "🔋", categories = listOf("HUD"), desc = "Persentase baterai", hud = true, settings = TEXT),
        ModuleInfo(id = "network", title = "Network", icon = "📶", categories = listOf("HUD"), desc = "Jenis koneksi (Wi-Fi/Seluler)", hud = true, settings = TEXT),
        // ---------------- Combat (hanya informasi; tanpa otomatisasi/manipulasi) ----------------
        ModuleInfo(id = "hitindicator", title = "Hit Indicator", icon = "💥", categories = listOf("Combat"), desc = "Indikator saat terkena/memukul", impl = Impl.UNSUPPORTED, reason = "Butuh event damage dari game. $NA_GAME"),
        ModuleInfo(id = "attackinfo", title = "Attack Information", icon = "⚔", categories = listOf("Combat"), desc = "Info serangan terakhir", impl = Impl.UNSUPPORTED, reason = "Butuh event serangan dari game. $NA_GAME"),
        ModuleInfo(id = "targetinfo", title = "Target Information", icon = "🎯", categories = listOf("Combat"), desc = "Info target yang dilihat", impl = Impl.UNSUPPORTED, reason = "Butuh data target/entitas yang andal. $NA_GAME"),
        // ---------------- Gamemode (informasi) ----------------
        ModuleInfo(id = "gameinfo", title = "Game Information", icon = "🎮", categories = listOf("Gamemode"), desc = "Versi Minecraft yang terdeteksi", hud = true, settings = TEXT + listOf(sToggle("feather", "Tampilkan versi Feather", false))),
        ModuleInfo(id = "worldinfo", title = "World Information", icon = "🌍", categories = listOf("Gamemode"), desc = "Nama/seed/difficulty world", impl = Impl.UNSUPPORTED, reason = "Butuh data world aktif. $NA_GAME"),
        ModuleInfo(id = "playerinfo", title = "Player Information", icon = "🧍", categories = listOf("Gamemode"), desc = "Nyawa/lapar/level pemain", impl = Impl.UNSUPPORTED, reason = "Butuh atribut pemain. $NA_GAME"),
        ModuleInfo(id = "serverinfo", title = "Server Information", icon = "🖥", categories = listOf("Gamemode"), desc = "Nama/jumlah pemain server", impl = Impl.UNSUPPORTED, reason = "Butuh data koneksi server. $NA_GAME"),
        ModuleInfo(id = "dimension", title = "Current Dimension", icon = "🌀", categories = listOf("Gamemode"), desc = "Overworld/Nether/End", impl = Impl.UNSUPPORTED, reason = "Butuh dimensi pemain saat ini. $NA_GAME"),
        // ---------------- Mechanic / Movement ----------------
        ModuleInfo(id = "togglesprint", title = "Toggle Sprint", icon = "🏃", categories = listOf("Mechanic", "Movement"), desc = "Sprint tanpa menahan tombol", impl = Impl.UNSUPPORTED, reason = "Butuh pengiriman input ke game. Belum ada integrasi input yang aman."),
        ModuleInfo(id = "togglesneak", title = "Toggle Sneak", icon = "🥷", categories = listOf("Mechanic", "Movement"), desc = "Sneak tanpa menahan tombol", impl = Impl.UNSUPPORTED, reason = "Butuh pengiriman input ke game. Belum ada integrasi input yang aman."),
        ModuleInfo(id = "zoom", title = "Zoom", icon = "🔭", categories = listOf("Mechanic", "Visual"), desc = "Tombol 🔍 di layar untuk zoom kamera", impl = Impl.HOOK, hooks = listOf("fov"), settings = listOf(sSlider("level", "Level zoom (%) — makin kecil makin dekat", 10, 80, 25), sChoice("mode", "Perilaku tombol zoom", listOf("Tahan", "Toggle"), 0))),
        ModuleInfo(id = "perspective", title = "Perspective Shortcut", icon = "👁", categories = listOf("Mechanic"), desc = "Pintasan ganti sudut kamera", impl = Impl.UNSUPPORTED, reason = "Butuh pengiriman input/pengaturan kamera game."),
        ModuleInfo(id = "touchctl", title = "Touch Control Customization", icon = "👆", categories = listOf("Mechanic"), desc = "Atur tata letak kontrol sentuh", impl = Impl.UNSUPPORTED, reason = "Tata letak kontrol sentuh dikelola Minecraft sendiri (Settings › Touch). Android tidak mengizinkan app lain mengubahnya."),
        // ---------------- Visual ----------------
        ModuleInfo(id = "crosshair", title = "Custom Crosshair", icon = "🎯", categories = listOf("Visual"), desc = "Crosshair kustom di tengah layar", settings = listOf(sChoice("shape", "Bentuk", listOf("Plus", "Titik", "Lingkaran", "Silang"), 0), sSlider("size", "Ukuran", 6, 40, 14), sSlider("thick", "Ketebalan", 1, 6, 3), sSlider("gap", "Celah tengah (bentuk Plus/Silang)", 0, 20, 4), sColor(), sSlider("opacity", "Opacity (%)", 20, 100, 100))),
        ModuleInfo(id = "hudvis", title = "HUD Visibility", icon = "👓", categories = listOf("Visual"), desc = "Tampilkan/sembunyikan semua HUD", kind = CardKind.PANEL),
        ModuleInfo(id = "hudscale", title = "HUD Scale", icon = "🔎", categories = listOf("Visual"), desc = "Ukuran global semua widget", kind = CardKind.PANEL),
        ModuleInfo(id = "hudcolor", title = "HUD Color", icon = "🎨", categories = listOf("Visual"), desc = "Warna aksen & opacity latar", kind = CardKind.PANEL),
        // ---------------- Utility ----------------
        ModuleInfo(id = "notifications", title = "Notifications", icon = "🔔", categories = listOf("Utility"), desc = "Pemberitahuan saat timer selesai", defaultOn = true, settings = listOf(sToggle("toast", "Tampilkan toast", true), sToggle("vibrate", "Getar", true))),
        ModuleInfo(id = "config", title = "Configuration Manager", icon = "🗂", categories = listOf("Utility"), desc = "Ekspor/impor konfigurasi profil", kind = CardKind.PANEL),
        ModuleInfo(id = "perf", title = "Performance Information", icon = "⚡", categories = listOf("Utility", "HUD"), desc = "Frame time & RAM", hud = true, settings = TEXT),
        ModuleInfo(id = "diag", title = "Compatibility Diagnostics", icon = "🩺", categories = listOf("Utility"), desc = "Status dependensi Minecraft & hook", kind = CardKind.PANEL),
        ModuleInfo(id = "reset", title = "Reset Settings", icon = "♻", categories = listOf("Utility"), desc = "Kembalikan pengaturan ke default", kind = CardKind.PANEL),
        ModuleInfo(id = "debug", title = "Debug Information", icon = "🐞", categories = listOf("Utility"), desc = "Info teknis untuk debugging", kind = CardKind.PANEL),
        ModuleInfo(id = "stopwatch", title = "Stopwatch", icon = "⏱", categories = listOf("Utility"), desc = "Penghitung waktu maju (tampil di HUD saat aktif)", kind = CardKind.PANEL, hud = true, settings = TEXT),
        ModuleInfo(id = "countdown", title = "Countdown", icon = "⏳", categories = listOf("Utility"), desc = "Hitung mundur (tampil di HUD saat aktif)", kind = CardKind.PANEL, hud = true, settings = TEXT),
        ModuleInfo(id = "screenshot", title = "Screenshot", icon = "📸", categories = listOf("Utility"), desc = "Simpan gambar layar game", kind = CardKind.PANEL),
        ModuleInfo(id = "notes", title = "Quick Notes", icon = "📝", categories = listOf("Utility"), desc = "Catatan cepat", kind = CardKind.PANEL),
    )

    private val byId = all.associateBy { it.id }
    fun find(id: String): ModuleInfo? = byId[id]

    fun activeHooks(status: String): Set<String> =
        if (status.startsWith("ok:")) status.removePrefix("ok:").split(",").filter { it.isNotBlank() }.toSet() else emptySet()

    fun availability(m: ModuleInfo, hookStatus: String): Availability = when (m.impl) {
        Impl.UNSUPPORTED -> Availability(Avail.UNSUPPORTED, m.reason)
        Impl.LOCAL -> Availability(Avail.AVAILABLE, "")
        Impl.HOOK -> {
            val active = activeHooks(hookStatus)
            val missing = m.hooks.filter { it !in active }
            if (missing.isEmpty()) Availability(Avail.AVAILABLE, "")
            else Availability(Avail.NEEDS_HOOK, "Modul ini butuh hook native: ${missing.joinToString()}.\nStatus hook saat ini: $hookStatus.\nIsi patterns.txt (launcher › Settings › Hook), lalu jalankan game.")
        }
    }
}
