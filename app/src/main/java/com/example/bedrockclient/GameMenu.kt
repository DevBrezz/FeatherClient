package com.example.bedrockclient

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

/** Menu in-game (glass, navy). 8 bagian: Modules, Social, Cosmetics, Macros, Waypoints, Profiles, Settings, Edit HUD. */
object GameMenu {
    private var a: Activity? = null
    private var overlay: FrameLayout? = null
    private var content: FrameLayout? = null
    private var dateText: TextView? = null
    private val navViews = mutableMapOf<String, View>()
    private var page = "modules"
    private var filter = "All"
    private var query = ""
    private var settingsFor: ModuleInfo? = null
    private var panelId: String? = null
    private var swText: TextView? = null
    private var cdText: TextView? = null
    private var curScroll: ScrollView? = null
    private var lastKey = ""
    private var contentWidthDp = 600f
    private var hs = "-"
    private val h = Handler(Looper.getMainLooper())
    val isOpen: Boolean get() = overlay != null

    private val PANEL = Color.parseColor("#F0121526")
    private val CARD = Color.parseColor("#1E2238")
    private val EDGE = Color.parseColor("#2C3150")
    private val MUTED = Color.parseColor("#9AA3C0")
    private val AMBER = Color.parseColor("#FFB74D")
    private val WHITE = Color.WHITE
    private val NAV = listOf(Triple("modules", "Modules", "▦"), Triple("social", "Social", "👥"), Triple("cosmetics", "Cosmetics", "✨"),
        Triple("macros", "Macros", "⚡"), Triple("waypoints", "Waypoints", "📍"), Triple("profiles", "Profiles", "👤"))

    // ---------- helper ----------
    private fun ctx() = a!!
    private fun dp(v: Int) = (v * ctx().resources.displayMetrics.density).toInt()
    private fun accent() = ModuleConfig.accentColor()
    private fun mix(c1: Int, c2: Int, t: Float) = Color.rgb(
        (Color.red(c1) * (1 - t) + Color.red(c2) * t).toInt(), (Color.green(c1) * (1 - t) + Color.green(c2) * t).toInt(), (Color.blue(c1) * (1 - t) + Color.blue(c2) * t).toInt())
    private fun rr(fill: Int, stroke: Int? = null, r: Int = 14, sw: Int = 1) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(r).toFloat(); if (stroke != null) setStroke(dp(sw), stroke)
    }
    private fun txt(t: String, size: Float = 14f, color: Int = WHITE, bold: Boolean = false) = TextView(ctx()).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun lp(w: Int, hh: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, hh, wt)
    private fun vbox() = LinearLayout(ctx()).apply { orientation = LinearLayout.VERTICAL }
    private fun hbox() = LinearLayout(ctx()).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun wrap(v: View): View = LinearLayout(ctx()).apply { addView(v, lp(-1, -2)); setPadding(0, 0, 0, dp(8)) }
    private fun cardBox() = vbox().apply { background = rr(CARD, EDGE, 16); setPadding(dp(14), dp(12), dp(14), dp(12)) }
    private fun btn(label: String, filled: Boolean, onClick: () -> Unit) = TextView(ctx()).apply {
        text = label; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setPadding(dp(14), dp(9), dp(14), dp(9))
        background = if (filled) rr(accent(), null, 10) else rr(CARD, EDGE, 10)
        setTextColor(if (filled) Ui.onAccent(accent()) else WHITE)
        setOnClickListener { onClick() }
    }
    private fun toast(s: String) = Ui.toast(ctx(), s)
    private fun copy(label: String, text: String) {
        (ctx().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text)); toast("Disalin")
    }
    private fun info(title: String, msg: String) { AlertDialog.Builder(ctx()).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show() }
    private fun switchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = hbox().apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(10), dp(14), dp(10)) }
        val t = vbox(); t.addView(txt(title, 15f)); if (desc.isNotEmpty()) t.addView(txt(desc, 11f, MUTED))
        row.addView(t, lp(0, -2, 1f))
        row.addView(Switch(ctx()).apply { isChecked = checked; setOnCheckedChangeListener { _, on -> onChange(on) } })
        return wrap(row)
    }
    private fun slider(label: String, min: Int, max: Int, cur: Int, onChange: (Int) -> Unit): View {
        val box = vbox().apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(10), dp(14), dp(10)) }
        val v = txt("$label: $cur", 13f); box.addView(v)
        box.addView(SeekBar(ctx()).apply {
            this.max = max - min; progress = (cur - min).coerceIn(0, max - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { v.text = "$label: ${p + min}"; if (u) onChange(p + min) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        return wrap(box)
    }
    private fun chips(label: String, options: List<String>, cur: Int, onPick: (Int) -> Unit): View {
        val box = vbox().apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(10), dp(14), dp(10)) }
        box.addView(txt(label, 13f))
        val row = hbox().apply { setPadding(0, dp(6), 0, 0) }
        options.forEachIndexed { i, o ->
            row.addView(txt(o, 13f, if (i == cur) Ui.onAccent(accent()) else WHITE, i == cur).apply {
                setPadding(dp(12), dp(7), dp(12), dp(7)); background = if (i == cur) rr(accent(), null, 10) else rr(mix(CARD, WHITE, .08f), null, 10)
                setOnClickListener { onPick(i) }
            }, lp(-2, -2).apply { rightMargin = dp(6) })
        }
        box.addView(HorizontalScrollView(ctx()).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        return wrap(box)
    }
    private fun colorRow(label: String, cur: Int, onPick: (Int) -> Unit): View {
        val box = vbox().apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(10), dp(14), dp(10)) }
        box.addView(txt("$label: ${Palette.names[cur.coerceIn(0, Palette.names.size - 1)]}", 13f))
        val row = hbox().apply { setPadding(0, dp(6), 0, 0) }
        Palette.hex.forEachIndexed { i, hex ->
            row.addView(TextView(ctx()).apply {
                text = if (i == 0) "T" else ""; gravity = Gravity.CENTER; textSize = 11f; setTextColor(Ui.onAccent(accent()))
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i == 0) accent() else Color.parseColor(hex)); if (i == cur) setStroke(dp(3), WHITE) }
                setOnClickListener { onPick(i) }
            }, lp(dp(28), dp(28)).apply { rightMargin = dp(8) })
        }
        box.addView(HorizontalScrollView(ctx()).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        return wrap(box)
    }

    // ---------- buka / tutup ----------
    fun toggle(act: Activity, root: FrameLayout) { if (isOpen) close() else open(act, root, false) }

    private fun open(act: Activity, root: FrameLayout, keep: Boolean) {
        a = act
        if (!keep) { page = "modules"; settingsFor = null; panelId = null; lastKey = "" }
        ModuleConfig.load(act)
        val dm = act.resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        val compact = widthDp < 600
        contentWidthDp = widthDp * 0.95f - (if (compact) 64f else 190f)
        val ov = FrameLayout(act).apply { setBackgroundColor(Color.parseColor("#99000000")); setOnClickListener { close() } }
        val panel = LinearLayout(act).apply { background = rr(PANEL, mix(EDGE, accent(), .5f), 22); isClickable = true }
        ov.addView(panel, FrameLayout.LayoutParams((dm.widthPixels * 0.95).toInt(), (dm.heightPixels * 0.9).toInt(), Gravity.CENTER))
        panel.addView(sidebar(compact), lp(if (compact) dp(64) else dp(190), -1))
        panel.addView(View(act).apply { setBackgroundColor(EDGE) }, lp(dp(1), -1))
        content = FrameLayout(act); panel.addView(content, lp(0, -1, 1f))
        root.addView(ov, FrameLayout.LayoutParams(-1, -1))
        overlay = ov
        showPage()
        h.removeCallbacks(tick); h.post(tick)
        HudOverlay.onMenuClosed()
    }

    private fun teardown() {
        h.removeCallbacks(tick)
        (overlay?.parent as? ViewGroup)?.removeView(overlay)
        overlay = null; content = null; dateText = null; swText = null; cdText = null; curScroll = null
        navViews.clear()
        HudOverlay.onMenuClosed()
    }
    fun close() { teardown(); settingsFor = null; panelId = null; a = null }
    /** Dipanggil saat layar berputar/ukuran berubah: bangun ulang panel dengan state yang sama. */
    fun onResize() { val act = a ?: return; val r = overlay?.parent as? FrameLayout ?: return; teardown(); open(act, r, true) }

    private val tick = object : Runnable {
        override fun run() {
            if (!isOpen) return
            dateText?.text = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
            swText?.text = ToolState.fmt(ToolState.swElapsed(), true)
            cdText?.text = ToolState.fmt(ToolState.cdRemaining(), false)
            h.postDelayed(this, 100)
        }
    }

    // ---------- sidebar ----------
    private fun go(id: String) { page = id; settingsFor = null; panelId = null; showPage() }

    private fun sidebar(compact: Boolean): View {
        val sb = vbox().apply { setPadding(dp(10), dp(12), dp(10), dp(10)) }
        sb.addView(txt(if (compact) "🪶" else "🪶 FEATHER\nCLIENT", if (compact) 22f else 18f, WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(10)) }, lp(-1, -2))
        val nav = vbox()
        NAV.forEach { (id, label, icon) ->
            val row = navRow(icon, label, compact) { go(id) }
            navViews[id] = row; nav.addView(row, lp(-1, -2).apply { bottomMargin = dp(4) })
        }
        sb.addView(ScrollView(ctx()).apply { addView(nav); isVerticalScrollBarEnabled = false }, lp(-1, 0, 1f))
        val st = navRow("⚙", "Settings", compact) { go("settings") }
        navViews["settings"] = st; sb.addView(st, lp(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) })
        sb.addView(navRow("✥", "Edit HUD", compact) { close(); HudOverlay.startEdit() }, lp(-1, -2))
        dateText = txt("", 10f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
        if (!compact) sb.addView(dateText, lp(-1, -2))
        return sb
    }
    private fun navRow(icon: String, label: String, compact: Boolean, click: () -> Unit) = LinearLayout(ctx()).apply {
        gravity = if (compact) Gravity.CENTER else Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(9), dp(10), dp(9)); background = rr(Color.TRANSPARENT, null, 12)
        addView(txt(icon, 17f).apply { gravity = Gravity.CENTER }, lp(dp(28), -2))
        if (!compact) addView(txt(label, 14f), lp(0, -2, 1f))
        setOnClickListener { click() }
    }

    // ---------- kerangka ----------
    private fun frame(title: String, sub: String, right: View? = null, below: View? = null): Pair<View, LinearLayout> {
        val col = vbox().apply { setPadding(dp(16), dp(14), dp(16), dp(10)) }
        val head = hbox()
        val t = vbox(); t.addView(txt(title, 20f, WHITE, true)); t.addView(txt(sub, 12f, MUTED))
        head.addView(t, lp(0, -2, 1f)); right?.let { head.addView(it) }
        col.addView(head)
        below?.let { col.addView(it, lp(-1, -2).apply { topMargin = dp(8) }) }
        val body = vbox().apply { setPadding(0, dp(10), 0, dp(10)) }
        val sv = ScrollView(ctx()).apply { addView(body); isVerticalScrollBarEnabled = false }
        curScroll = sv
        col.addView(sv, lp(-1, 0, 1f))
        return col to body
    }
    private fun backBtn(label: String = "‹ Modules") = txt(label, 14f).apply {
        setPadding(dp(12), dp(8), dp(12), dp(8)); background = rr(CARD, EDGE, 12)
        setOnClickListener { settingsFor = null; panelId = null; showPage() }
    }

    private fun showPage() {
        val c = content ?: return
        hs = try { NativeBridge.hookStatus() } catch (_: Throwable) { "-" }
        val key = page + (settingsFor?.id ?: "") + (panelId ?: "")
        val keep = if (key == lastKey) curScroll?.scrollY ?: 0 else 0
        lastKey = key
        c.removeAllViews(); swText = null; cdText = null
        navViews.forEach { (id, v) -> v.background = if (id == page) rr(mix(CARD, accent(), .35f), accent(), 12) else rr(Color.TRANSPARENT, null, 12) }
        val m = settingsFor; val pid = panelId
        val v = when {
            m != null -> moduleSettingsPage(m)
            pid != null -> panelPage(pid)
            page == "modules" -> modulesPage()
            page == "social" -> socialPage()
            page == "cosmetics" -> cosmeticsPage()
            page == "macros" -> macrosPage()
            page == "waypoints" -> waypointsPage()
            page == "profiles" -> profilesPage()
            else -> settingsPage()
        }
        c.addView(v, FrameLayout.LayoutParams(-1, -1))
        if (keep > 0) c.post { curScroll?.scrollTo(0, keep) }
    }

    // ================= MODULES =================
    private fun applyToggle(m: ModuleInfo, on: Boolean) {
        ModuleConfig.set(ctx(), m, on)
        try { NativeBridge.setModule(m.id, on) } catch (_: Throwable) {}
        HudOverlay.refreshNow()
    }
    private fun avail(m: ModuleInfo) = ModuleRegistry.availability(m, hs)

    private fun modulesPage(): View {
        val search = txt(if (query.isEmpty()) "🔍" else "✕ \"$query\"", 15f).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8)); background = rr(CARD, EDGE, 12)
            setOnClickListener { if (query.isNotEmpty()) { query = ""; showPage() } else Ui.prompt(ctx(), "Cari modul", "") { query = it; showPage() } }
        }
        val tabs = hbox()
        (listOf("All") + ModuleRegistry.CATEGORIES + listOf("Pinned")).forEach { f ->
            tabs.addView(txt(if (f == "Pinned") "★ Pinned" else f, 13f, if (f == filter) WHITE else MUTED, f == filter).apply {
                setPadding(dp(13), dp(6), dp(13), dp(6))
                if (f == filter) background = rr(mix(CARD, accent(), .4f), null, 10)
                setOnClickListener { filter = f; showPage() }
            })
        }
        val tabScroll = HorizontalScrollView(ctx()).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        val (v, body) = frame("Modules", "Atur modul client bawaan", search, tabScroll)
        val cols = when { contentWidthDp >= 540 -> 3; contentWidthDp >= 330 -> 2; else -> 1 }
        val list = ModuleRegistry.all
            .filter { m -> when (filter) { "All" -> true; "Pinned" -> ModuleConfig.isPinned(ctx(), m.id); else -> filter in m.categories } }     // kategori + pencarian bekerja bersamaan
            .filter { query.isEmpty() || it.title.contains(query, true) || it.desc.contains(query, true) }
            .sortedWith(compareByDescending<ModuleInfo> { ModuleConfig.isPinned(ctx(), it.id) }.thenBy { it.impl == Impl.UNSUPPORTED }.thenBy { it.title })
        if (list.isEmpty()) body.addView(txt(if (filter == "Pinned") "Belum ada modul yang di-pin. Tekan 📌 pada kartu." else "Tidak ada modul yang cocok.", 13f, MUTED))
        list.chunked(cols).forEach { chunk ->
            val row = LinearLayout(ctx())
            chunk.forEach { row.addView(card(it), lp(0, -2, 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }) }
            repeat(cols - chunk.size) { row.addView(View(ctx()), lp(0, 1, 1f).apply { setMargins(dp(5), 0, dp(5), 0) }) }
            body.addView(row)
        }
        body.addView(txt("Tap kartu = nyala/mati  •  📌 pin  •  ⚙ pengaturan  •  redup = belum didukung (tap untuk alasan)  •  \"Butuh hook\" = perlu patterns.txt", 11f, MUTED).apply { setPadding(dp(5), dp(8), 0, 0) })
        return v
    }

    private fun card(m: ModuleInfo): View {
        val c = ctx(); val av = avail(m)
        val unsupported = av.state == Avail.UNSUPPORTED; val panel = m.kind == CardKind.PANEL
        val on = !panel && !unsupported && ModuleConfig.isOn(c, m)
        val activeTool = panel && ((m.id == "stopwatch" && ToolState.swActive()) || (m.id == "countdown" && ToolState.cdRunning))
        val pin = ModuleConfig.isPinned(c, m.id); val acc = accent()
        val box = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); minimumHeight = dp(84)
            background = if (on || activeTool) rr(mix(CARD, acc, .35f), acc, 18, 2) else rr(CARD, EDGE, 18)
            alpha = if (unsupported) 0.55f else 1f
            setOnClickListener {
                when {
                    unsupported -> info(m.title + " — belum didukung", m.reason)
                    panel -> { panelId = m.id; showPage() }
                    else -> { val cur = ModuleConfig.isOn(c, m); if (!cur && av.state != Avail.AVAILABLE) info(m.title, av.message) else { applyToggle(m, !cur); showPage() } }
                }
            }
        }
        box.addView(txt(m.icon, 22f).apply { gravity = Gravity.CENTER; background = rr(mix(CARD, WHITE, .08f), null, 12) }, lp(dp(46), dp(46)))
        val right = vbox().apply { gravity = Gravity.END }
        right.addView(txt(m.title, 14f).apply { gravity = Gravity.END; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lp(-1, -2))
        val badge = when { unsupported -> "Belum didukung" to MUTED; av.state == Avail.NEEDS_HOOK -> "Butuh hook" to AMBER; panel -> "Buka ▸" to MUTED; else -> null }
        badge?.let { right.addView(txt(it.first, 10f, it.second).apply { gravity = Gravity.END }, lp(-1, -2)) }
        val icons = LinearLayout(c).apply { gravity = Gravity.END }
        icons.addView(txt("📌", 15f).apply { alpha = if (pin) 1f else .35f; setPadding(dp(8), dp(6), dp(6), dp(2)); setOnClickListener { ModuleConfig.setPinned(c, m.id, !pin); showPage() } })
        if (m.settings.isNotEmpty() && !unsupported)
            icons.addView(txt("⚙\uFE0E", 17f, MUTED).apply { setPadding(dp(6), dp(6), dp(6), dp(2)); setOnClickListener { settingsFor = m; showPage() } })
        right.addView(icons, lp(-1, -2))
        box.addView(right, lp(0, -2, 1f).apply { leftMargin = dp(10) })
        return box
    }

    private fun moduleSettingsPage(m: ModuleInfo): View {
        val c = ctx()
        val (v, body) = frame(m.title, m.desc, backBtn())
        if (m.kind == CardKind.TOGGLE) {
            val av = avail(m)
            body.addView(switchRow("Aktif", if (av.state == Avail.AVAILABLE) "Nyalakan / matikan modul ini" else "Butuh hook native — lihat status di bawah", ModuleConfig.isOn(c, m)) { on ->
                if (on && av.state != Avail.AVAILABLE) { info(m.title, av.message); showPage() } else applyToggle(m, on)
            })
        }
        m.settings.forEach { s ->
            val cur = ModuleConfig.getInt(c, m.id, s.key, s.def)
            val change = { value: Int ->
                ModuleConfig.setInt(c, m.id, s.key, value); HudOverlay.refreshNow()
                if (m.id == "zoom" && s.key == "level") try { NativeBridge.setZoomLevel(value / 100f) } catch (_: Throwable) {}
            }
            when (s.type) {
                SettingType.TOGGLE -> body.addView(switchRow(s.label, "", cur == 1) { change(if (it) 1 else 0) })
                SettingType.SLIDER -> body.addView(slider(s.label, s.min, s.max, cur) { change(it) })
                SettingType.CHOICE -> body.addView(chips(s.label, s.options, cur) { change(it); showPage() })
                SettingType.COLOR -> body.addView(colorRow(s.label, cur) { change(it); showPage() })
            }
        }
        if (m.hud) {
            val box = vbox().apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(10), dp(14), dp(10)) }
            box.addView(txt("Posisi widget", 13f))
            val row = hbox().apply { setPadding(0, dp(6), 0, 0) }
            listOf("↖" to (0f to 0f), "↑" to (0.5f to 0f), "↗" to (1f to 0f), "↙" to (0f to 1f), "↓" to (0.5f to 1f), "↘" to (1f to 1f)).forEach { (label, f) ->
                row.addView(btn(label, false) { HudOverlay.setFrac(m.id, f.first, f.second); toast("Posisi diatur") }, lp(0, -2, 1f).apply { rightMargin = dp(4) })
            }
            box.addView(row)
            box.addView(btn("Atur manual di Edit HUD", true) { close(); HudOverlay.startEdit(m.id) }, lp(-1, -2).apply { topMargin = dp(8) })
            body.addView(wrap(box))
        }
        if (m.impl == Impl.HOOK) {
            val av = avail(m)
            body.addView(txt(if (av.state == Avail.AVAILABLE) "Hook aktif ✓ ($hs)" else av.message, 12f, if (av.state == Avail.AVAILABLE) MUTED else AMBER).apply { setPadding(0, 0, 0, dp(8)) })
        }
        if (m.settings.isNotEmpty()) body.addView(btn("Reset pengaturan modul", false) {
            ModuleConfig.resetSettings(c, m); HudOverlay.refreshNow(); ModuleConfig.syncNative(c); showPage()
        })
        return v
    }

    // ================= PANEL (kartu bertipe PANEL) =================
    private fun panelPage(id: String): View {
        val m = ModuleRegistry.find(id)
        val (v, body) = frame(m?.title ?: id, m?.desc ?: "", backBtn())
        val c = ctx()
        when (id) {
            "hudvis" -> body.addView(switchRow("Sembunyikan semua HUD", "Widget disembunyikan; crosshair tetap", ModuleConfig.hudHidden(c)) { ModuleConfig.setHudHidden(c, it); HudOverlay.refreshNow() })
            "hudscale" -> body.addView(slider("Ukuran HUD global (%)", 70, 160, ModuleConfig.scale) { ModuleConfig.setScale(c, it); HudOverlay.refreshNow() })
            "hudcolor" -> {
                val acc = hbox().apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(12), dp(14), dp(12)) }
                acc.addView(txt("Warna aksen", 14f), lp(0, -2, 1f))
                ModuleConfig.accents.forEachIndexed { i, hex ->
                    acc.addView(View(c).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex)); if (i == ModuleConfig.accentIdx) setStroke(dp(3), WHITE) }
                        setOnClickListener { ModuleConfig.setAccent(c, i); HudOverlay.refreshNow(); showPage() }
                    }, lp(dp(26), dp(26)).apply { leftMargin = dp(8) })
                }
                body.addView(wrap(acc))
                body.addView(slider("Opacity latar HUD", 0, 100, ModuleConfig.opacity) { ModuleConfig.setOpacity(c, it); HudOverlay.refreshNow() })
            }
            "config" -> {
                body.addView(txt("Ekspor/impor konfigurasi profil \"${InstanceStore.name(c, InstanceStore.current(c))}\" (modul, pengaturan, pin, posisi HUD) sebagai JSON. Impor memvalidasi setiap entri dan mengabaikan yang tidak valid.", 12f, MUTED).apply { setPadding(0, 0, 0, dp(10)) })
                body.addView(btn("Salin konfigurasi ke clipboard", true) { copy("Feather config", ConfigManager.export(c)) })
                body.addView(btn("Impor dari clipboard", false) {
                    val clip = (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(c)?.toString().orEmpty()
                    try {
                        val msg = ConfigManager.import(c, clip); HudOverlay.reload(); showPage(); toast(msg)
                    } catch (e: IllegalArgumentException) { toast("Gagal impor: ${e.message}") }
                }, lp(-1, -2).apply { topMargin = dp(8) })
            }
            "diag" -> {
                val s = MinecraftGuard.status(c)
                body.addView(txt(MinecraftGuard.diagnostics(c, s) + "Hook native: $hs", 12f).apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(12), dp(14), dp(12)) })
                val r = hbox().apply { setPadding(0, dp(10), 0, 0) }
                r.addView(btn("Periksa ulang", true) { MinecraftGuard.invalidate(); showPage() }, lp(0, -2, 1f).apply { rightMargin = dp(6) })
                r.addView(btn("Salin", false) { copy("Feather diagnostik", MinecraftGuard.diagnostics(c, MinecraftGuard.status(c)) + "Hook native: $hs") }, lp(0, -2, 1f))
                body.addView(r)
            }
            "debug" -> {
                val pf = java.io.File(c.filesDir, "patterns.txt")
                val text = buildString {
                    appendLine("Feather ${ModuleConfig.VERSION}  •  profil: ${InstanceStore.name(c, InstanceStore.current(c))}")
                    appendLine("Hook native: $hs")
                    appendLine("patterns.txt: ${if (pf.exists()) "${pf.length()} byte" else "tidak ada"}")
                    appendLine("Loader: ${HostApplication.loaderError ?: if (HostApplication.loaderStamp != null) "terpasang" else "belum dipasang"}")
                    appendLine("FPS: ${HudOverlay.fpsValue()}  •  frame time: ${"%.1f".format(Locale.US, HudOverlay.frameMs())} ms")
                    appendLine("Sesi: ${ToolState.fmtHms(ToolState.sessionElapsed())}")
                    appendLine("Modul aktif: " + ModuleRegistry.all.filter { it.kind == CardKind.TOGGLE && it.impl != Impl.UNSUPPORTED && ModuleConfig.isOn(c, it) }.joinToString { it.id })
                    appendLine("Modul belum didukung: ${ModuleRegistry.all.count { it.impl == Impl.UNSUPPORTED }} dari ${ModuleRegistry.all.size}")
                }
                body.addView(txt(text, 12f).apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(12), dp(14), dp(12)) })
                body.addView(btn("Salin", false) { copy("Feather debug", text) }, lp(-1, -2).apply { topMargin = dp(10) })
            }
            "reset" -> {
                body.addView(txt("Reset berlaku untuk profil aktif: \"${InstanceStore.name(c, InstanceStore.current(c))}\".", 12f, MUTED).apply { setPadding(0, 0, 0, dp(10)) })
                body.addView(btn("Reset semua pengaturan profil", true) {
                    AlertDialog.Builder(c).setTitle("Reset semua pengaturan?").setMessage("Modul, pengaturan, pin, dan posisi HUD profil ini kembali ke default.")
                        .setPositiveButton("Reset") { _, _ -> ModuleConfig.resetAll(c); ModuleConfig.syncNative(c); HudOverlay.reload(); showPage(); toast("Pengaturan direset") }.setNegativeButton("Batal", null).show()
                })
                body.addView(btn("Reset posisi & ukuran widget saja", false) { LayoutStore.resetToDefault(c); ModuleConfig.syncNative(c); HudOverlay.reload(); toast("Layout HUD direset") }, lp(-1, -2).apply { topMargin = dp(8) })
            }
            "stopwatch" -> {
                val sw = cardBox()
                swText = txt(ToolState.fmt(ToolState.swElapsed(), true), 30f, accent(), true); sw.addView(swText)
                val r1 = hbox().apply { setPadding(0, dp(6), 0, dp(4)) }
                r1.addView(btn(if (ToolState.swRunning) "Pause" else "Start", true) { ToolState.swToggle(); HudOverlay.refreshNow(); showPage() }, lp(0, -2, 1f).apply { rightMargin = dp(6) })
                r1.addView(btn("Reset", false) { ToolState.swReset(); HudOverlay.refreshNow(); showPage() }, lp(0, -2, 1f))
                sw.addView(r1); sw.addView(txt("Tampil di HUD selama aktif. Posisi/ukuran: ⚙ atau Edit HUD.", 11f, MUTED))
                body.addView(wrap(sw))
                body.addView(btn("Pengaturan widget", false) { settingsFor = m; showPage() })
            }
            "countdown" -> {
                val cd = cardBox()
                cdText = txt(ToolState.fmt(if (ToolState.cdRunning) ToolState.cdRemaining() else ToolState.cdMinutes * 60_000L, false), 30f, accent(), true); cd.addView(cdText)
                if (!ToolState.cdRunning) cd.addView(slider("Durasi (menit)", 1, 60, ToolState.cdMinutes) { ToolState.cdMinutes = it; cdText?.text = ToolState.fmt(it * 60_000L, false) })
                cd.addView(btn(if (ToolState.cdRunning) "Stop" else "Start", true) { if (ToolState.cdRunning) ToolState.cdStop() else ToolState.cdStart(); HudOverlay.refreshNow(); showPage() })
                cd.addView(txt("Pemberitahuan saat selesai diatur di modul Notifications.", 11f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
                body.addView(wrap(cd))
                body.addView(btn("Pengaturan widget", false) { settingsFor = m; showPage() })
            }
            "screenshot" -> {
                body.addView(txt("Menutup menu lalu menyimpan gambar ke Pictures/FeatherClient.", 12f, MUTED).apply { setPadding(0, 0, 0, dp(10)) })
                body.addView(btn("📸 Ambil screenshot", true) { val act = ctx(); close(); h.postDelayed({ Screenshot.take(act) }, 350) })
            }
            "notes" -> {
                val cur = Prefs.notes(c)
                body.addView(txt(if (cur.isBlank()) "(kosong)" else cur, 13f, MUTED).apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(12), dp(14), dp(12)) })
                body.addView(btn("Edit catatan", true) { editNotes() }, lp(-1, -2).apply { topMargin = dp(10) })
            }
            else -> body.addView(txt("Halaman tidak ditemukan.", 13f, MUTED))
        }
        return v
    }

    private fun editNotes() {
        val c = ctx()
        val et = EditText(c).apply { setText(Prefs.notes(c)); minLines = 4; gravity = Gravity.TOP; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        AlertDialog.Builder(c).setTitle("Catatan cepat").setView(et)
            .setPositiveButton("Simpan") { _, _ -> Prefs.setNotes(c, et.text.toString()); showPage() }.setNegativeButton("Batal", null).show()
    }

    // ================= SOCIAL (jujur: butuh backend) =================
    private fun socialPage(): View {
        val c = ctx()
        val (v, body) = frame("Social", "Teman & obrolan")
        val s = cardBox()
        s.addView(txt("Belum tersedia", 16f, AMBER, true))
        s.addView(txt("Fitur teman, status online, dan pesan membutuhkan layanan backend (akun, server, penyimpanan) yang belum ada di Feather Client. Karena itu tidak ada daftar teman atau pemain online yang ditampilkan.", 13f, MUTED).apply { setPadding(0, dp(6), 0, 0) })
        body.addView(wrap(s))
        val a2 = cardBox()
        a2.addView(txt("Yang tersedia sekarang", 15f, WHITE, true))
        a2.addView(txt("Profil lokal: ${Prefs.profile(c) ?: "belum diatur (launcher › Sign In)"}\nBagikan konfigurasi profil ke temanmu lewat teks JSON:", 13f, MUTED).apply { setPadding(0, dp(6), 0, dp(8)) })
        a2.addView(btn("Salin profil untuk dibagikan", true) { copy("Feather config", ConfigManager.export(c)) })
        a2.addView(btn("Impor profil dari clipboard", false) {
            val clip = (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(c)?.toString().orEmpty()
            try { toast(ConfigManager.import(c, clip)); HudOverlay.reload() } catch (e: IllegalArgumentException) { toast("Gagal impor: ${e.message}") }
        }, lp(-1, -2).apply { topMargin = dp(8) })
        body.addView(wrap(a2))
        return v
    }

    // ================= COSMETICS (jujur: butuh hook render) =================
    private fun cosmeticsPage(): View {
        val (v, body) = frame("Cosmetics", "Cape, model, aksesori")
        val s = cardBox()
        s.addView(txt("Belum tersedia", 16f, AMBER, true))
        s.addView(txt("Cosmetics (cape, model, aksesori) membutuhkan integrasi dengan renderer/skin Minecraft yang belum ada di Feather, dan layanan untuk menyimpan item. Karena itu tidak ada katalog cosmetics yang ditampilkan.", 13f, MUTED).apply { setPadding(0, dp(6), 0, 0) })
        body.addView(wrap(s))
        val a2 = cardBox()
        a2.addView(txt("Kustomisasi visual yang tersedia", 15f, WHITE, true))
        a2.addView(txt("Ini hanya memengaruhi tampilan HUD Feather, bukan karakter/skin.", 12f, MUTED).apply { setPadding(0, dp(4), 0, dp(8)) })
        a2.addView(btn("Warna & opacity HUD", true) { go("modules"); panelId = "hudcolor"; showPage() })
        a2.addView(btn("Gaya crosshair", false) { go("modules"); settingsFor = ModuleRegistry.find("crosshair"); showPage() }, lp(-1, -2).apply { topMargin = dp(8) })
        body.addView(wrap(a2))
        return v
    }

    // ================= MACROS (tanpa otomatisasi input; Quick Actions) =================
    private fun macrosPage(): View {
        val c = ctx()
        val (v, body) = frame("Macros", "Pintasan aksi cepat")
        val s = cardBox()
        s.addView(txt("Macro input tidak disediakan", 16f, AMBER, true))
        s.addView(txt("Merekam atau mengirim input otomatis ke game (macro/autoclick) tidak dibuat di Feather Client. Sebagai gantinya ada Quick Actions di bawah: pintasan sekali tekan untuk fitur yang memang ada.", 13f, MUTED).apply { setPadding(0, dp(6), 0, 0) })
        body.addView(wrap(s))
        val q = cardBox(); q.addView(txt("Quick Actions", 15f, WHITE, true))
        fun action(label: String, f: () -> Unit) = q.addView(btn(label, false, f), lp(-1, -2).apply { topMargin = dp(8) })
        action("📸 Screenshot") { val act = ctx(); close(); h.postDelayed({ Screenshot.take(act) }, 350) }
        action(if (ToolState.swRunning) "⏱ Pause stopwatch" else "⏱ Start stopwatch") { ToolState.swToggle(); HudOverlay.refreshNow(); showPage() }
        action(if (ToolState.cdRunning) "⏳ Stop countdown" else "⏳ Start countdown (${ToolState.cdMinutes} mnt)") { if (ToolState.cdRunning) ToolState.cdStop() else ToolState.cdStart(); HudOverlay.refreshNow(); showPage() }
        action(if (ModuleConfig.hudHidden(c)) "👓 Tampilkan HUD" else "👓 Sembunyikan HUD") { ModuleConfig.setHudHidden(c, !ModuleConfig.hudHidden(c)); HudOverlay.refreshNow(); showPage() }
        action("📍 Salin koordinat sekarang") {
            val p = try { NativeBridge.getCoords() } catch (_: Throwable) { null }
            if (p == null) toast("Koordinat belum tersedia (butuh hook player_tick)") else copy("Koordinat", String.format(Locale.US, "%.0f %.0f %.0f", p[0], p[1], p[2]))
        }
        body.addView(wrap(q))
        return v
    }

    // ================= WAYPOINTS =================
    private fun waypointsPage(): View {
        val add = btn("＋ Tambah", true) { addWaypointDialog() }
        val (v, body) = frame("Waypoints", "Simpan koordinat penting", add)
        val cur = try { NativeBridge.getCoords() } catch (_: Throwable) { null }
        val list = WaypointStore.list(ctx())
        if (list.isEmpty()) body.addView(txt("Belum ada waypoint. Tekan ＋ Tambah.", 13f, MUTED))
        list.forEachIndexed { i, w ->
            val row = hbox().apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(10), dp(10), dp(10)) }
            val t = vbox()
            t.addView(txt(w.name, 15f, WHITE, true))
            val dist = if (cur != null) { val dx = w.x - cur[0]; val dy = w.y - cur[1]; val dz = w.z - cur[2]; "  •  ≈ ${Math.sqrt(dx * dx + dy * dy + dz * dz).toInt()} m" } else ""
            t.addView(txt(String.format(Locale.US, "%.1f / %.1f / %.1f", w.x, w.y, w.z) + dist, 12f, MUTED))
            row.addView(t, lp(0, -2, 1f))
            row.addView(txt("📋", 18f).apply { setPadding(dp(10), dp(4), dp(10), dp(4)); setOnClickListener { copy("waypoint", String.format(Locale.US, "%.0f %.0f %.0f", w.x, w.y, w.z)) } })
            row.addView(txt("🗑", 18f).apply { setPadding(dp(10), dp(4), dp(10), dp(4)); setOnClickListener { WaypointStore.remove(ctx(), i); showPage() } })
            body.addView(wrap(row))
        }
        if (cur == null) body.addView(txt("Jarak & tombol \"Posisi sekarang\" butuh hook koordinat (player_tick).", 11f, MUTED))
        return v
    }

    private fun addWaypointDialog() {
        val c = ctx()
        val lay = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun field(hint: String, num: Boolean) = EditText(c).apply {
            this.hint = hint; setSingleLine()
            if (num) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }.also { lay.addView(it) }
        val n = field("Nama", false); val x = field("X", true); val y = field("Y", true); val z = field("Z", true)
        val dlg = AlertDialog.Builder(c).setTitle("Waypoint baru").setView(lay)
            .setPositiveButton("Simpan", null).setNeutralButton("Posisi sekarang", null).setNegativeButton("Batal", null).create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val p = try { NativeBridge.getCoords() } catch (_: Throwable) { null }
            if (p == null) Ui.toast(c, "Koordinat belum tersedia (butuh hook player_tick)")
            else { x.setText(String.format(Locale.US, "%.1f", p[0])); y.setText(String.format(Locale.US, "%.1f", p[1])); z.setText(String.format(Locale.US, "%.1f", p[2])) }
        }
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = n.text.toString().trim()
            val xs = x.text.toString().toDoubleOrNull(); val ys = y.text.toString().toDoubleOrNull(); val zs = z.text.toString().toDoubleOrNull()
            if (name.isEmpty() || xs == null || ys == null || zs == null) { Ui.toast(c, "Lengkapi nama dan koordinat"); return@setOnClickListener }
            WaypointStore.add(c, Waypoint(name, xs, ys, zs)); dlg.dismiss(); showPage()
        }
    }

    // ================= PROFILES =================
    private fun activate(id: String) {
        val c = ctx()
        InstanceStore.setCurrent(c, id); ModuleConfig.load(c); ModuleConfig.syncNative(c)
        HudOverlay.reload(); showPage()
    }

    private fun profilesPage(): View {
        val add = btn("＋ Profil baru", true) {
            Ui.prompt(ctx(), "Nama profil", "Profil ${InstanceStore.ids(ctx()).size + 1}") { n -> activate(InstanceStore.create(ctx(), n)) }
        }
        val (v, body) = frame("Profiles", "Tiap profil = modul, pengaturan & posisi HUD sendiri", add)
        val cur = InstanceStore.current(ctx())
        InstanceStore.ids(ctx()).forEach { id ->
            val box = cardBox()
            box.addView(txt(InstanceStore.name(ctx(), id) + if (id == cur) "   ✓ aktif" else "", 16f, if (id == cur) accent() else WHITE, true))
            val r = hbox().apply { setPadding(0, dp(8), 0, 0) }
            if (id != cur) r.addView(btn("Aktifkan", true) { activate(id) }, lp(0, -2, 1f).apply { rightMargin = dp(6) })
            r.addView(btn("Hapus", false) {
                if (!InstanceStore.delete(ctx(), id)) toast("Minimal harus ada 1 profil") else activate(InstanceStore.current(ctx()))
            }, lp(0, -2, 1f))
            box.addView(r)
            body.addView(wrap(box))
        }
        body.addView(btn("Configuration Manager (ekspor/impor)", false) { go("modules"); panelId = "config"; showPage() })
        return v
    }

    // ================= SETTINGS =================
    private fun settingsPage(): View {
        val c = ctx()
        val (v, body) = frame("Settings", "Tampilan HUD & umum")
        body.addView(switchRow("Sembunyikan semua HUD", "Widget disembunyikan (crosshair tetap)", ModuleConfig.hudHidden(c)) { ModuleConfig.setHudHidden(c, it); HudOverlay.refreshNow() })
        body.addView(slider("Opacity latar HUD", 0, 100, ModuleConfig.opacity) { ModuleConfig.setOpacity(c, it); HudOverlay.refreshNow() })
        body.addView(slider("Ukuran HUD global (%)", 70, 160, ModuleConfig.scale) { ModuleConfig.setScale(c, it); HudOverlay.refreshNow() })
        val acc = hbox().apply { background = rr(CARD, EDGE, 14); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        acc.addView(txt("Warna aksen", 14f), lp(0, -2, 1f))
        ModuleConfig.accents.forEachIndexed { i, hex ->
            acc.addView(View(c).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex)); if (i == ModuleConfig.accentIdx) setStroke(dp(3), WHITE) }
                setOnClickListener { ModuleConfig.setAccent(c, i); HudOverlay.refreshNow(); showPage() }
            }, lp(dp(26), dp(26)).apply { leftMargin = dp(8) })
        }
        body.addView(wrap(acc))
        body.addView(btn("Edit HUD", true) { close(); HudOverlay.startEdit() })
        body.addView(btn("Reset posisi HUD", false) { ModuleConfig.resetPositions(c); HudOverlay.reload(); toast("Posisi HUD direset") }, lp(-1, -2).apply { topMargin = dp(8) })
        body.addView(txt("\nBuka/tutup menu: tombol 🪶 (kanan atas) atau Shift Kanan. Back menutup menu.\nUtilitas lain (diagnostik, debug, reset, konfigurasi) ada di Modules › Utility.", 12f, MUTED))
        return v
    }
}
