package com.example.bedrockclient

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * HUD = View Android yang ditambahkan di atas jendela Activity Minecraft (addContentView).
 * Ini BUKAN injeksi ke renderer game: widget hanya tampil selama MainActivity Minecraft berjalan di proses Feather.
 * Widget dibuat dari ModuleRegistry (modul dengan hud = true).
 */
object HudOverlay {
    private class Item(val m: ModuleInfo, val view: View, val defX: Float, val defY: Float)

    private var root: FrameLayout? = null
    private var host: Activity? = null
    private var origCb: Window.Callback? = null
    private val items = mutableListOf<Item>()
    private var crosshair: CrosshairView? = null
    private var keys: KeystrokesView? = null
    private var menuBtn: TextView? = null
    private var zoomBtn: TextView? = null
    private var shield: View? = null
    private var toolbar: View? = null
    private var toolbarLabel: TextView? = null
    var editMode = false; private set
    private var selectedId: String? = null
    private var running = false
    private var zoomToggled = false

    private var frames = 0; private var lastNs = 0L; private var lastFpsNs = 0L; private var fpsFrames = 0
    private var fps = 0; private var fpsIntervalNs = 1_000_000_000L
    private var ftSum = 0L; private var ftCount = 0; private var ftMax = 0L; private var lastFrameNs = 0L
    private var avgFtMs = 0f; private var worstFtMs = 0f
    private val taps = ArrayDeque<Long>()
    private var lastW = 0; private var lastH = 0
    private var prevPos: FloatArray? = null; private var prevT = 0L; private var speed = 0f
    private val FAST = setOf("fps", "coords", "blockcoords", "speed", "stopwatch", "countdown", "session")

    fun fpsValue() = fps
    fun frameMs() = avgFtMs
    fun activity(): Activity? = host

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(ns: Long) {
            if (!running) return
            if (lastNs == 0L) { lastNs = ns; lastFpsNs = ns }
            if (lastFrameNs != 0L) { val d = ns - lastFrameNs; ftSum += d; ftCount++; if (d > ftMax) ftMax = d }
            lastFrameNs = ns
            frames++; fpsFrames++
            if (ns - lastFpsNs >= fpsIntervalNs) {
                fps = (fpsFrames * 1_000_000_000.0 / (ns - lastFpsNs)).roundToInt(); fpsFrames = 0; lastFpsNs = ns; updateFast()
            } else if (frames % 6 == 0) updateFast()
            if (ns - lastNs >= 1_000_000_000L) {
                avgFtMs = if (ftCount > 0) ftSum / ftCount / 1e6f else 0f; worstFtMs = ftMax / 1e6f
                ftSum = 0; ftCount = 0; ftMax = 0; frames = 0; lastNs = ns
                refresh()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // ---------- API untuk menu / editor ----------
    fun refreshNow() = refresh()
    fun onMenuClosed() = refresh()
    fun reload() { items.forEach { applyPos(it) }; refresh() }
    private fun toggleMenu() { val a = host; val r = root; if (a != null && r != null) GameMenu.toggle(a, r) }

    /** Atur posisi widget (pecahan 0..1 dari area aman) dan simpan. */
    fun setFrac(id: String, fx: Float, fy: Float) {
        val a = host ?: return
        ModuleConfig.savePosFrac(a, id, fx, fy)
        items.firstOrNull { it.m.id == id }?.let { applyPos(it) }
    }

    // ---------- Attach / detach ----------
    fun attach(a: Activity) {
        detach()
        // Modul yang bergantung pada Minecraft hanya diinisialisasi jika Minecraft terverifikasi kompatibel.
        if (MinecraftGuard.status(a) !is MinecraftStatus.Compatible) return
        host = a; ModuleConfig.load(a)
        if (ToolState.sessionStart == 0L) ToolState.sessionStart = SystemClock.elapsedRealtime()
        try { NativeBridge.setZoomLevel(ModuleConfig.getInt(a, "zoom", "level", 25) / 100f) } catch (_: Throwable) {}
        val dm = a.resources.displayMetrics
        fun dp(v: Int) = v * dm.density

        val r = FrameLayout(a); root = r
        crosshair = CrosshairView(a).also { r.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        var idx = 0
        ModuleRegistry.all.filter { it.hud && it.impl != Impl.UNSUPPORTED }.forEach { m ->
            val v: View = if (m.id == "keystrokes") KeystrokesView(a).also { keys = it }
                          else TextView(a).apply { typeface = Typeface.MONOSPACE; setPadding(14, 6, 14, 6) }
            val col = idx / 7; val row = idx % 7; idx++
            val dx = dp(8 + col * 118); val dy = if (m.id == "keystrokes") dp(230) else dp(8 + row * 32)
            add(a, r, Item(m, v, dx, dy))
        }

        menuBtn = TextView(a).apply {
            text = "🪶"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; alpha = 0.8f
            setBackgroundColor(Color.parseColor("#66000000"))
            layoutParams = FrameLayout.LayoutParams(dp(38).toInt(), dp(38).toInt(), Gravity.TOP or Gravity.END).apply { topMargin = dp(8).toInt(); rightMargin = dp(8).toInt() }
            setOnClickListener { toggleMenu() }
        }
        r.addView(menuBtn)

        zoomBtn = TextView(a).apply {
            text = "🔍"; textSize = 22f; gravity = Gravity.CENTER; setBackgroundColor(Color.parseColor("#66000000"))
            layoutParams = FrameLayout.LayoutParams(dp(48).toInt(), dp(48).toInt(), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(24).toInt() }
            setOnTouchListener { v, e ->
                val toggleMode = ModuleConfig.getInt(a, "zoom", "mode", 0) == 1
                try {
                    if (toggleMode) {
                        if (e.actionMasked == MotionEvent.ACTION_UP) { zoomToggled = !zoomToggled; NativeBridge.setZoomHeld(zoomToggled); v.alpha = if (zoomToggled) 1f else 0.8f }
                    } else when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> NativeBridge.setZoomHeld(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> NativeBridge.setZoomHeld(false)
                    }
                } catch (_: Throwable) {}
                true
            }
        }
        r.addView(zoomBtn)
        a.addContentView(r, FrameLayout.LayoutParams(-1, -1))

        // Rotasi/perubahan ukuran: posisikan ulang widget & bangun ulang menu.
        r.addOnLayoutChangeListener { _, l, t, rr, b, _, _, _, _ ->
            val w = rr - l; val h = b - t
            if (w != lastW || h != lastH) { lastW = w; lastH = h; items.forEach { applyPos(it) }; if (GameMenu.isOpen) GameMenu.onResize() }
        }

        val w = a.window; val orig = w.callback; origCb = orig
        w.callback = object : Window.Callback by orig {
            override fun dispatchTouchEvent(e: MotionEvent): Boolean {
                if (!GameMenu.isOpen && !editMode && (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN)) taps.addLast(System.nanoTime())
                return orig.dispatchTouchEvent(e)
            }
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) { if (e.action == KeyEvent.ACTION_UP) toggleMenu(); return true }
                if (GameMenu.isOpen || editMode) {
                    if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { if (editMode) endEdit() else GameMenu.close() }
                    return true
                }
                val down = e.action == KeyEvent.ACTION_DOWN
                when (e.keyCode) {
                    KeyEvent.KEYCODE_W -> keys?.set(0, down); KeyEvent.KEYCODE_A -> keys?.set(1, down)
                    KeyEvent.KEYCODE_S -> keys?.set(2, down); KeyEvent.KEYCODE_D -> keys?.set(3, down)
                    KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A -> keys?.set(4, down)
                }
                return orig.dispatchKeyEvent(e)
            }
        }
        running = true; lastNs = 0L; frames = 0; lastFrameNs = 0L
        refresh()
        Choreographer.getInstance().postFrameCallback(frameCb)
    }

    fun detach() {
        running = false
        if (GameMenu.isOpen) GameMenu.close()
        host?.window?.let { w -> origCb?.let { w.callback = it } }
        (root?.parent as? ViewGroup)?.removeView(root)
        root = null; host = null; origCb = null; items.clear(); crosshair = null; keys = null; menuBtn = null; zoomBtn = null
        shield = null; toolbar = null; toolbarLabel = null; editMode = false; selectedId = null; zoomToggled = false; lastW = 0; lastH = 0
    }

    // ---------- Area aman & posisi ----------
    /** Area aman = layar dikurangi display cutout (notch). Bar sistem tidak dikurangi karena Minecraft berjalan fullscreen. */
    private fun safe(): Rect {
        val r = root ?: return Rect()
        var l = 0; var t = 0; var rt = 0; var b = 0
        if (Build.VERSION.SDK_INT >= 28) r.rootWindowInsets?.displayCutout?.let { l = it.safeInsetLeft; t = it.safeInsetTop; rt = it.safeInsetRight; b = it.safeInsetBottom }
        return Rect(l, t, r.width - rt, r.height - b)
    }

    private fun applyPos(it: Item) {
        val a = host ?: return; val v = it.view
        if (v.width == 0 || v.height == 0) return
        val s = safe(); val w = v.width * v.scaleX; val h = v.height * v.scaleY
        val rangeX = maxOf(0f, s.width() - w); val rangeY = maxOf(0f, s.height() - h)
        val f = ModuleConfig.posFrac(a, it.m.id)
        if (f != null) { v.translationX = s.left + f.first * rangeX; v.translationY = s.top + f.second * rangeY }
        else { v.translationX = it.defX.coerceIn(s.left.toFloat(), s.left + rangeX); v.translationY = it.defY.coerceIn(s.top.toFloat(), s.top + rangeY) }
    }
    private fun currentFrac(it: Item): Pair<Float, Float> {
        val a = host ?: return 0f to 0f
        ModuleConfig.posFrac(a, it.m.id)?.let { return it }
        val s = safe(); val v = it.view
        val rx = maxOf(1f, s.width() - v.width * v.scaleX); val ry = maxOf(1f, s.height() - v.height * v.scaleY)
        return ((v.translationX - s.left) / rx).coerceIn(0f, 1f) to ((v.translationY - s.top) / ry).coerceIn(0f, 1f)
    }

    private fun add(a: Activity, r: FrameLayout, item: Item) {
        items.add(item)
        val v = item.view
        r.addView(v, FrameLayout.LayoutParams(-2, -2))
        v.pivotX = 0f; v.pivotY = 0f
        v.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyPos(item) }   // widget tumbuh -> tetap di dalam area aman
        var dx = 0f; var dy = 0f
        v.setOnTouchListener { view, e ->
            if (!editMode) return@setOnTouchListener false
            val loc = IntArray(2); root?.getLocationOnScreen(loc)
            val s = safe(); val w = view.width * view.scaleX; val h = view.height * view.scaleY
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { select(item.m.id); dx = view.translationX - (e.rawX - loc[0]); dy = view.translationY - (e.rawY - loc[1]) }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX = (e.rawX - loc[0] + dx).coerceIn(s.left.toFloat(), maxOf(s.left.toFloat(), s.right - w))
                    view.translationY = (e.rawY - loc[1] + dy).coerceIn(s.top.toFloat(), maxOf(s.top.toFloat(), s.bottom - h))
                }
                MotionEvent.ACTION_UP -> {
                    val rx = maxOf(1f, s.width() - w); val ry = maxOf(1f, s.height() - h)
                    ModuleConfig.savePosFrac(a, item.m.id, (view.translationX - s.left) / rx, (view.translationY - s.top) / ry)
                }
            }
            true
        }
    }

    // ---------- Editor HUD ----------
    fun startEdit(sel: String? = null) {
        val a = host ?: return; val r = root ?: return
        if (editMode) return
        editMode = true
        shield = View(a).apply { isClickable = true; setOnClickListener { select(null) } }      // memblokir sentuhan ke game selama edit
        r.addView(shield, 0, FrameLayout.LayoutParams(-1, -1))
        toolbar = buildToolbar(a).also { r.addView(it) }
        menuBtn?.visibility = View.GONE
        select(sel)
        refresh()
    }
    private fun endEdit() {
        val r = root; editMode = false; selectedId = null
        shield?.let { r?.removeView(it) }; toolbar?.let { r?.removeView(it) }
        shield = null; toolbar = null; toolbarLabel = null
        menuBtn?.visibility = View.VISIBLE
        items.forEach { it.view.foreground = null }
        refresh()
    }
    private fun select(id: String?) {
        selectedId = id
        val acc = ModuleConfig.accentColor()
        items.forEach { it.view.foreground = if (it.m.id == id) GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(4, acc) } else null }
        toolbarLabel?.text = id?.let { ModuleRegistry.find(it)?.title } ?: "Ketuk widget"
    }
    private fun selected(): Item? = items.firstOrNull { it.m.id == selectedId }

    private fun buildToolbar(a: Activity): View {
        val dm = a.resources.displayMetrics
        fun dp(v: Int) = (v * dm.density).toInt()
        val bar = LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply { setColor(Color.parseColor("#EE121526")); cornerRadius = dp(14).toFloat() }
        }
        toolbarLabel = TextView(a).apply { setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(8), 0, dp(10), 0); minWidth = dp(90) }
        bar.addView(toolbarLabel)
        fun btn(label: String, onClick: () -> Unit) = TextView(a).apply {
            text = label; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(11), dp(7), dp(11), dp(7))
            background = GradientDrawable().apply { setColor(Color.parseColor("#2C3150")); cornerRadius = dp(10).toFloat() }
            setOnClickListener { onClick() }
        }.also { bar.addView(it, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4) }) }
        fun needSel(f: (Item) -> Unit) { val it = selected(); if (it == null) Toast.makeText(a, "Ketuk widget dulu", Toast.LENGTH_SHORT).show() else f(it) }
        fun scaleBy(d: Int) = needSel { it ->
            ModuleConfig.setInt(a, it.m.id, "scale", ModuleConfig.getInt(a, it.m.id, "scale", 100) + d); refresh(); applyPos(it)
        }
        fun align(h: Float?, v: Float?) = needSel { it -> val c = currentFrac(it); ModuleConfig.savePosFrac(a, it.m.id, h ?: c.first, v ?: c.second); applyPos(it) }
        btn("−") { scaleBy(-10) }; btn("+") { scaleBy(10) }
        btn("👁") { needSel { it ->
            val m = it.m
            if (m.kind == CardKind.TOGGLE) {
                val on = ModuleConfig.isOn(a, m)
                val av = ModuleRegistry.availability(m, try { NativeBridge.hookStatus() } catch (_: Throwable) { "-" })
                if (!on && av.state != Avail.AVAILABLE) AlertDialog.Builder(a).setTitle(m.title).setMessage(av.message).setPositiveButton("OK", null).show()
                else { ModuleConfig.set(a, m, !on); try { NativeBridge.setModule(m.id, !on) } catch (_: Throwable) {}; refresh() }
            } else Toast.makeText(a, "Widget ini tampil otomatis saat aktif (Menu › Utility)", Toast.LENGTH_SHORT).show()
        } }
        btn("⇤") { align(0f, null) }; btn("⇔") { align(0.5f, null) }; btn("⇥") { align(1f, null) }
        btn("⤒") { align(null, 0f) }; btn("↕") { align(null, 0.5f) }; btn("⤓") { align(null, 1f) }
        btn("Layout") { layoutDialog(a) }
        btn("✓") { endEdit() }
        return HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false; addView(bar)
            layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(10); leftMargin = dp(12); rightMargin = dp(12) }
        }
    }

    private fun layoutDialog(a: Activity) {
        val names = LayoutStore.names(a)
        val labels = names.map { "Muat: $it" } + listOf("＋ Simpan layout sekarang…", "Hapus layout…", "Reset ke default")
        AlertDialog.Builder(a).setTitle("Layout HUD").setItems(labels.toTypedArray()) { _, i ->
            when {
                i < names.size -> { if (LayoutStore.load(a, names[i])) { ModuleConfig.syncNative(a); reload(); Toast.makeText(a, "Layout dimuat", Toast.LENGTH_SHORT).show() } else Toast.makeText(a, "Layout rusak", Toast.LENGTH_SHORT).show() }
                i == names.size -> Ui.prompt(a, "Nama layout", "Layout ${names.size + 1}") { LayoutStore.save(a, it); Toast.makeText(a, "Layout disimpan", Toast.LENGTH_SHORT).show() }
                i == names.size + 1 -> {
                    if (names.isEmpty()) Toast.makeText(a, "Belum ada layout", Toast.LENGTH_SHORT).show()
                    else AlertDialog.Builder(a).setTitle("Hapus layout").setItems(names.toTypedArray()) { _, j -> LayoutStore.delete(a, names[j]) }.show()
                }
                else -> AlertDialog.Builder(a).setTitle("Reset ke default?").setMessage("Posisi, ukuran, dan visibilitas widget kembali ke bawaan.")
                    .setPositiveButton("Reset") { _, _ -> LayoutStore.resetToDefault(a); ModuleConfig.syncNative(a); reload() }.setNegativeButton("Batal", null).show()
            }
        }.show()
    }

    // ---------- Teks dinamis ----------
    private fun coordsData(): FloatArray? = try { NativeBridge.getCoords() } catch (_: Throwable) { null }

    private fun networkText(a: Context): String {
        val cm = a.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (_: Exception) { null }
        return when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler"
            else -> "online"
        }
    }
    private fun battery(a: Context) = (a.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    private fun ramMb() = Debug.getPss() / 1024

    private fun textFor(a: Context, id: String): String = when (id) {
        "fps" -> (if (ModuleConfig.getBool(a, "fps", "label")) "FPS: " else "") + fps
        "cps" -> (if (ModuleConfig.getBool(a, "cps", "label")) "CPS: " else "") + taps.size
        "clock" -> {
            val h24 = ModuleConfig.getBool(a, "clock", "h24"); val sec = ModuleConfig.getBool(a, "clock", "seconds")
            SimpleDateFormat((if (h24) "HH:mm" else "hh:mm") + (if (sec) ":ss" else "") + (if (h24) "" else " a"), Locale.getDefault()).format(Date())
        }
        "battery" -> "BAT: ${battery(a)}%"
        "memory" -> "RAM: ${ramMb()} MB"
        "network" -> "NET: ${networkText(a)}"
        "coords" -> {
            val c = coordsData() ?: if (editMode) floatArrayOf(0f, 64f, 0f) else null
            if (c == null) "XYZ: -" else {
                val d = ModuleConfig.getInt(a, "coords", "decimals", 1)
                val f = { v: Float -> String.format(Locale.US, "%.${d}f", v) }
                when (ModuleConfig.getInt(a, "coords", "format", 0)) {
                    1 -> "${f(c[0])} ${f(c[1])} ${f(c[2])}"
                    2 -> "X: ${f(c[0])}  Y: ${f(c[1])}  Z: ${f(c[2])}"
                    else -> "XYZ: ${f(c[0])} / ${f(c[1])} / ${f(c[2])}"
                }
            }
        }
        "blockcoords" -> {
            val c = coordsData() ?: if (editMode) floatArrayOf(0f, 64f, 0f) else null
            if (c == null) "BLK: -" else "BLK: ${floor(c[0]).toInt()} ${floor(c[1]).toInt()} ${floor(c[2]).toInt()}"
        }
        "speed" -> if (coordsData() == null && !editMode) "SPD: -" else "SPD: " + String.format(Locale.US, "%.${ModuleConfig.getInt(a, "speed", "decimals", 1)}f", speed) + " b/s"
        "session" -> (if (ModuleConfig.getBool(a, "session", "label")) "SES: " else "") + ToolState.fmtHms(ToolState.sessionElapsed())
        "infohud" -> buildString {
            if (ModuleConfig.getBool(a, "infohud", "show_fps")) appendLine("FPS: $fps")
            if (ModuleConfig.getBool(a, "infohud", "show_cps")) appendLine("CPS: ${taps.size}")
            if (ModuleConfig.getBool(a, "infohud", "show_bat")) appendLine("BAT: ${battery(a)}%")
            if (ModuleConfig.getBool(a, "infohud", "show_ram")) appendLine("RAM: ${ramMb()} MB")
            if (ModuleConfig.getBool(a, "infohud", "show_net")) appendLine("NET: ${networkText(a)}")
        }.trimEnd().ifEmpty { "(semua baris dimatikan)" }
        "modulelist" -> ModuleRegistry.all.filter { it.kind == CardKind.TOGGLE && it.impl != Impl.UNSUPPORTED && ModuleConfig.isOn(a, it) }.joinToString("\n") { it.title }.ifEmpty { "(tidak ada modul aktif)" }
        "gameinfo" -> {
            val s = MinecraftGuard.status(a) as? MinecraftStatus.Compatible
            "MC ${s?.version ?: "?"}" + if (ModuleConfig.getBool(a, "gameinfo", "feather")) "  •  Feather ${ModuleConfig.VERSION}" else ""
        }
        "perf" -> String.format(Locale.US, "FT: %.1f ms (maks %.0f)  RAM: %d MB", avgFtMs, worstFtMs, ramMb())
        "stopwatch" -> "⏱ " + ToolState.fmt(ToolState.swElapsed(), true)
        "countdown" -> "⏳ " + ToolState.fmt(ToolState.cdRemaining(), false)
        else -> ""
    }

    private fun setIfChanged(tv: TextView, s: String) { if (!tv.text.contentEquals(s)) tv.text = s }

    private fun updateFast() {
        val a = host ?: return
        for (it in items) {
            if (it.m.id !in FAST || it.view.visibility != View.VISIBLE) continue
            if (it.m.id == "speed") updateSpeed()
            (it.view as? TextView)?.let { tv -> setIfChanged(tv, textFor(a, it.m.id)) }
        }
    }
    private fun updateSpeed() {
        val c = coordsData(); val now = SystemClock.elapsedRealtime()
        if (c == null) { prevPos = null; speed = 0f; return }
        val p = prevPos
        if (p == null) { prevPos = c; prevT = now }
        else if (now - prevT >= 250) { val dx = c[0] - p[0]; val dz = c[2] - p[2]; speed = sqrt(dx * dx + dz * dz) / ((now - prevT) / 1000f); prevPos = c; prevT = now }
    }

    private fun enabled(a: Context, m: ModuleInfo): Boolean = when (m.id) {
        "stopwatch" -> ToolState.swActive(); "countdown" -> ToolState.cdRunning
        else -> ModuleConfig.isOn(a, m)
    }

    private fun refresh() {
        val a = host ?: return
        if (ToolState.checkDone(a, ModuleConfig.isOn(a, "notifications") && ModuleConfig.getBool(a, "notifications", "vibrate"))
            && ModuleConfig.isOn(a, "notifications") && ModuleConfig.getBool(a, "notifications", "toast"))
            Toast.makeText(a, "⏳ Timer selesai!", Toast.LENGTH_LONG).show()
        fpsIntervalNs = ModuleConfig.getInt(a, "fps", "interval", 1000) * 1_000_000L
        val accent = ModuleConfig.accentColor()
        val bg = Color.argb(ModuleConfig.opacity * 255 / 100, 0, 0, 0)
        val gs = ModuleConfig.scale / 100f
        val now = System.nanoTime()
        while (taps.isNotEmpty() && now - taps.first() > 1_000_000_000L) taps.removeFirst()
        val hidden = ModuleConfig.hudHidden(a)

        for (it in items) {
            val m = it.m; val v = it.view
            val on = enabled(a, m)
            v.visibility = if ((on && !hidden) || editMode) View.VISIBLE else View.GONE
            v.alpha = if (editMode && !on) 0.45f else 1f
            val s = gs * ModuleConfig.getInt(a, m.id, "scale", 100) / 100f
            v.scaleX = s; v.scaleY = s
            val color = ModuleConfig.colorFor(a, m.id, "color", accent)
            if (v is TextView) {
                v.setTextColor(color); v.setBackgroundColor(bg)
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, ModuleConfig.getInt(a, m.id, "textsize", 13).toFloat())
                setIfChanged(v, textFor(a, m.id))
            } else if (v is KeystrokesView) v.style(color, bg)
            if (v.width > 0) applyPos(it)
        }
        crosshair?.apply {
            visibility = if (ModuleConfig.isOn(a, "crosshair")) View.VISIBLE else View.GONE
            val d = a.resources.displayMetrics.density
            val base = ModuleConfig.colorFor(a, "crosshair", "color", accent)
            color = Color.argb(ModuleConfig.getInt(a, "crosshair", "opacity", 100) * 255 / 100, Color.red(base), Color.green(base), Color.blue(base))
            shape = ModuleConfig.getInt(a, "crosshair", "shape", 0)
            sizePx = ModuleConfig.getInt(a, "crosshair", "size", 14) * d
            gapPx = ModuleConfig.getInt(a, "crosshair", "gap", 4) * d
            thickPx = ModuleConfig.getInt(a, "crosshair", "thick", 3) * d / 1.5f
            invalidate()
        }
        menuBtn?.setTextColor(accent)
        zoomBtn?.visibility = if (ModuleConfig.isOn(a, "zoom") && !GameMenu.isOpen && !editMode) View.VISIBLE else View.GONE
    }

    // ---------- Views ----------
    private class CrosshairView(c: Context) : View(c) {
        var color = Color.WHITE; var shape = 0; var sizePx = 20f; var gapPx = 6f; var thickPx = 3f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { isClickable = false; isFocusable = false }
        override fun onDraw(cv: Canvas) {
            p.color = color; p.strokeWidth = thickPx; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            val cx = width / 2f; val cy = height / 2f
            when (shape) {
                1 -> { p.style = Paint.Style.FILL; cv.drawCircle(cx, cy, maxOf(thickPx * 1.2f, sizePx / 5f), p) }
                2 -> cv.drawCircle(cx, cy, sizePx, p)
                3 -> { val o = sizePx * 0.7f; val g = gapPx * 0.7f
                    cv.drawLine(cx - o, cy - o, cx - g, cy - g, p); cv.drawLine(cx + g, cy + g, cx + o, cy + o, p)
                    cv.drawLine(cx - o, cy + o, cx - g, cy + g, p); cv.drawLine(cx + g, cy - g, cx + o, cy - o, p) }
                else -> {
                    cv.drawLine(cx - sizePx, cy, cx - gapPx, cy, p); cv.drawLine(cx + gapPx, cy, cx + sizePx, cy, p)
                    cv.drawLine(cx, cy - sizePx, cx, cy - gapPx, p); cv.drawLine(cx, cy + gapPx, cx, cy + sizePx, p)
                }
            }
        }
        override fun onTouchEvent(e: MotionEvent) = false
    }

    private class KeystrokesView(c: Context) : View(c) {
        private val down = BooleanArray(5)
        private val k = 40f * c.resources.displayMetrics.density; private val g = 4f * c.resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG); private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = k * 0.4f }
        private var accent = Color.WHITE; private var bg = Color.argb(128, 0, 0, 0)
        fun set(i: Int, v: Boolean) { if (down[i] != v) { down[i] = v; postInvalidate() } }
        fun style(a: Int, b: Int) { if (accent != a || bg != b) { accent = a; bg = b; invalidate() } }
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((3 * k + 2 * g).toInt(), (2 * k + k * 0.7f + 2 * g).toInt())
        private fun key(cv: Canvas, label: String, col: Int, row: Int, span: Int, idx: Int, hFactor: Float = 1f) {
            val l = col * (k + g); val t = row * (k + g); val r = l + span * k + (span - 1) * g; val b = t + k * hFactor
            fill.color = if (down[idx]) accent else bg
            cv.drawRoundRect(l, t, r, b, 8f, 8f, fill)
            txt.color = if (down[idx]) Color.BLACK else accent
            cv.drawText(label, (l + r) / 2, (t + b) / 2 + txt.textSize / 3, txt)
        }
        override fun onDraw(cv: Canvas) {
            key(cv, "W", 1, 0, 1, 0); key(cv, "A", 0, 1, 1, 1); key(cv, "S", 1, 1, 1, 2); key(cv, "D", 2, 1, 1, 3)
            key(cv, "━", 0, 2, 3, 4, 0.7f)
        }
    }
}
