package com.example.bedrockclient

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.animation.ObjectAnimator
import android.app.Dialog
import android.view.animation.LinearInterpolator
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    companion object { const val REQ_PICK = 71; const val REQ_PATTERNS = 72; const val REQ_EXPORT = 73 }
    private lateinit var content: FrameLayout
    private lateinit var tabsBox: LinearLayout
    private lateinit var logo: TextView
    private lateinit var signBtn: TextView
    private lateinit var bellDot: View
    private val stack = ArrayList<() -> View>()
    private var tab = "launch"
    private val tabDefs = listOf("launch" to "🚀 Launch", "instances" to "☰ Instances", "about" to "ⓘ About", "settings" to "⚙ Settings")
    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Ui.applyTheme(this)
        ModuleConfig.load(this)
        setContentView(buildShell())
        selectTab(b?.getString("tab") ?: "launch")
        // Peluncuran sebelumnya tidak pernah mencapai game (kemungkinan crash): jangan Quick Launch lagi agar tidak crash-loop.
        val unfinished = b == null && Prefs.launchPending(this)
        if (unfinished) {
            Prefs.setLaunchPending(this, false)
            AlertDialog.Builder(this).setTitle("Peluncuran sebelumnya tidak selesai")
                .setMessage("Game kemungkinan crash atau tertutup saat dimulai. Quick Launch dilewati kali ini.\n\nPeriksa apakah versi Minecraft kamu didukung, lalu salin info diagnostik jika masalah berulang.")
                .setPositiveButton("Salin diagnostik") { _, _ -> copyDiagnostics() }.setNegativeButton("Tutup", null).show()
        }
        if (b == null && !unfinished && Prefs.quickLaunch(this) && MinecraftGuard.status(this) is MinecraftStatus.Compatible)
            window.decorView.post { launchGame() }
    }

    override fun onSaveInstanceState(o: Bundle) { super.onSaveInstanceState(o); o.putString("tab", tab) }
    override fun onResume() {
        super.onResume()
        MinecraftGuard.invalidate()     // pengguna mungkin baru menginstall/memperbarui Minecraft; periksa ulang setiap kembali ke app
        render()
    }
    override fun onBackPressed() { if (stack.size > 1) { stack.removeAt(stack.size - 1); render() } else super.onBackPressed() }

    // ---------- Shell ----------
    private fun buildShell(): View {
        val root = Ui.vbox(this).apply { setBackgroundColor(Ui.BG) }
        val bar = Ui.hbox(this).apply { setPadding(dp(10), dp(30), dp(12), dp(6)) }
        bar.addView(Ui.text(this, "‹", 32f).apply { setPadding(dp(6), 0, dp(14), 0); setOnClickListener { onBackPressed() } })
        val brandIcon = ImageView(this).apply {
            setImageResource(R.drawable.feather_ingame_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Feather Client logo"
        }
        bar.addView(brandIcon, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(8) })
        logo = Ui.text(this, "FeatherClient", 20f, Ui.FG, true); bar.addView(logo)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val bell = FrameLayout(this).apply { setOnClickListener { showNotifications() } }
        bell.addView(Ui.text(this, "🔔", 22f).apply { setPadding(dp(8), dp(4), dp(8), dp(4)) })
        bellDot = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#F44336")) } }
        bell.addView(bellDot, FrameLayout.LayoutParams(dp(9), dp(9), Gravity.TOP or Gravity.END).apply { topMargin = dp(2); rightMargin = dp(4) })
        bar.addView(bell)
        signBtn = Ui.button(this, "Sign In", true) { onSign() }
        bar.addView(signBtn, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        root.addView(bar)

        tabsBox = Ui.hbox(this).apply { setPadding(dp(8), 0, dp(8), dp(6)) }
        tabDefs.forEach { (id, label) ->
            tabsBox.addView(Ui.text(this, label, 15f, Ui.MUTED).apply {
                tag = id; setPadding(dp(14), dp(8), dp(14), dp(8)); setOnClickListener { selectTab(id) }
            })
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabsBox) })
        root.addView(View(this).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(-1, 1))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    // ---------- Navigasi ----------
    fun selectTab(id: String) {
        tab = id; stack.clear()
        stack.add { when (id) { "instances" -> Screens.instances(this); "about" -> Screens.about(this); "settings" -> Screens.settings(this); else -> Screens.home(this) } }
        render()
    }
    fun push(b: () -> View) { stack.add(b); render() }
    fun replaceTop(b: () -> View) { stack[stack.size - 1] = b; render() }
    fun refresh() = render()
    fun render() {
        content.removeAllViews()
        content.addView(ScrollView(this).apply { addView(stack.last().invoke()) }, FrameLayout.LayoutParams(-1, -1))
        val acc = ModuleConfig.accentColor()
        logo.setTextColor(acc)
        for (i in 0 until tabsBox.childCount) (tabsBox.getChildAt(i) as TextView).setTextColor(if (tabsBox.getChildAt(i).tag == tab) acc else Ui.MUTED)
        Ui.styleButton(signBtn, true)
        signBtn.text = Prefs.profile(this) ?: "Sign In"
        bellDot.visibility = if (Prefs.seenNotif(this) != notifHash()) View.VISIBLE else View.GONE
    }

    // ---------- Aksi ----------
    private var launchDialog: Dialog? = null
    fun launchGame() {
        if (launchDialog?.isShowing == true) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = Ui.shape(this@MainActivity, Ui.CARD, Ui.BORDER, 20)
        }
        val feather = ImageView(this).apply {
            setImageResource(com.example.bedrockclient.R.drawable.feather_logo)
            contentDescription = "Feather Client logo"
        }
        panel.addView(feather, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(12) })
        panel.addView(Ui.text(this, "Launching Minecraft", 20f, Ui.FG, true).apply { gravity = Gravity.CENTER })
        panel.addView(Ui.text(this, "Feather Client 1.7", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(14) ) })
        panel.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(180), dp(4)))
        val dialog = Dialog(this).apply {
            setContentView(panel)
            setCancelable(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout((resources.displayMetrics.widthPixels * 0.42f).toInt().coerceAtLeast(dp(260)), -2)
        }
        launchDialog = dialog
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.42f).toInt().coerceAtLeast(dp(260)), -2)
        ObjectAnimator.ofFloat(feather, View.ROTATION, -8f, 8f).apply {
            duration = 750; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE
            interpolator = LinearInterpolator(); start()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val error = ModLoader.launch(this)
            if (error != null) {
                if (dialog.isShowing) dialog.dismiss()
                launchDialog = null
                Ui.toast(this, error)
                render()
            }
        }, 550)
    }

    // ---------- Dependensi Minecraft ----------
    fun recheckMinecraft() {
        MinecraftGuard.invalidate()
        val s = MinecraftGuard.status(this, force = true)
        render()
        Ui.toast(this, when (s) {
            is MinecraftStatus.Compatible -> "Minecraft ${s.version} terdeteksi ✓"
            is MinecraftStatus.NotInstalled -> "Minecraft masih belum terinstall"
            is MinecraftStatus.Incompatible -> "Masih tidak kompatibel: ${s.reason}"
            is MinecraftStatus.Error -> "Gagal memeriksa: ${s.message}"
        })
    }
    /** Listing resmi Minecraft di Google Play. Ketersediaan bergantung pada wilayah/akun; jika Play Store tidak ada, dibuka di browser. */
    fun openPlayStore() {
        val id = MinecraftGuard.MC_PACKAGE
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).setPackage("com.android.vending")) }
        catch (e: ActivityNotFoundException) { openUrl("https://play.google.com/store/apps/details?id=$id") }
    }
    fun openAppInfo() {
        try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${MinecraftGuard.MC_PACKAGE}"))) }
        catch (e: Exception) { Ui.toast(this, "Tidak bisa membuka info aplikasi") }
    }
    fun copyDiagnostics() {
        val text = MinecraftGuard.diagnostics(this, MinecraftGuard.status(this))
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Feather diagnostik", text))
        Ui.toast(this, "Info diagnostik disalin")
    }
    fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: ActivityNotFoundException) { Ui.toast(this, "Tidak ada aplikasi untuk membuka link ini") }
    }
    fun pickFiles() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, REQ_PICK)
    }
    private var exportSrc: java.io.File? = null
    fun exportWorld(f: java.io.File) {
        exportSrc = f
        val safe = ContentManager.displayName(ContentManager.Kind.WORLDS, f).replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "world" }
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"; putExtra(Intent.EXTRA_TITLE, "$safe.mcworld")
        }, REQ_EXPORT)
    }
    fun pickPatterns() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQ_PATTERNS)
    }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_EXPORT) {
            val src = exportSrc; val uri = data?.data
            if (res == RESULT_OK && src != null && uri != null) {
                Ui.toast(this, "Mengekspor world…")
                Thread {
                    val msg = try {
                        contentResolver.openOutputStream(uri)?.use { ContentManager.exportWorld(src, it) } ?: throw java.io.IOException("Tidak bisa menulis file")
                        "World diekspor ✓"
                    } catch (e: Exception) { "Gagal ekspor: ${e.message}" }
                    runOnUiThread { Ui.toast(this, msg) }
                }.start()
            }
            return
        }
        if (req == REQ_PATTERNS && res == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openInputStream(data.data!!)?.use { i -> java.io.File(filesDir, "patterns.txt").outputStream().use { o -> i.copyTo(o) } }
                Ui.toast(this, "patterns.txt diimpor. Mulai ulang app lalu jalankan game."); render()
            } catch (e: Exception) { Ui.toast(this, "Gagal: ${e.message}") }
            return
        }
        if (req != REQ_PICK || res != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>(); data.data?.let { uris.add(it) }
        data.clipData?.let { cd -> for (i in 0 until cd.itemCount) uris.add(cd.getItemAt(i).uri) }
        Ui.toast(this, "Mengimpor…")
        Thread {
            val total = ContentManager.Report(); uris.forEach { total.merge(ContentManager.importUri(applicationContext, it)) }
            runOnUiThread { Ui.toast(this, total.summary()); render() }
        }.start()
    }

    // ---------- Sign in (profil lokal) ----------
    private fun onSign() {
        val cur = Prefs.profile(this)
        if (cur == null) Ui.prompt(this, "Profil lokal (tersimpan di perangkat)", "") { Prefs.setProfile(this, it); render() }
        else AlertDialog.Builder(this).setTitle(cur).setItems(arrayOf("Ganti nama", "Sign out")) { _, i ->
            if (i == 0) Ui.prompt(this, "Nama profil", cur) { Prefs.setProfile(this, it); render() } else { Prefs.setProfile(this, null); render() }
        }.show()
    }

    // ---------- Notifikasi ----------
    private fun hookStatus() = try { NativeBridge.hookStatus() } catch (_: Throwable) { "-" }
    private fun notifications(): List<Pair<String, String>> {
        val l = mutableListOf<Pair<String, String>>()
        when (val g = MinecraftGuard.check(this)) {
            is MinecraftGuard.Result.Fail -> l.add("Minecraft tidak ditemukan" to g.reason)
            is MinecraftGuard.Result.Ok -> l.add("Minecraft ${g.version} terdeteksi" to "Hook native: ${hookStatus()}")
        }
        l.add("Selamat datang di ${ModuleConfig.CLIENT_NAME}" to "Versi ${ModuleConfig.VERSION} oleh ${ModuleConfig.AUTHOR}.")
        return l
    }
    private fun notifHash() = notifications().joinToString("|") { it.first + it.second }.hashCode()
    private fun showNotifications() {
        AlertDialog.Builder(this).setTitle("Notifikasi").setMessage(notifications().joinToString("\n\n") { "• ${it.first}\n  ${it.second}" })
            .setPositiveButton("Tutup", null).show()
        Prefs.setSeenNotif(this, notifHash()); render()
    }
}
