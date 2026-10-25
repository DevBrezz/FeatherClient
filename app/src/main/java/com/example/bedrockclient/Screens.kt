package com.example.bedrockclient

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import java.io.File
import android.widget.*
import com.example.bedrockclient.ContentManager.Kind

object Screens {
    private val tips = listOf(
        "CurseForge: unduh addon/world, lalu buka file-nya untuk impor otomatis ke Feather.",
        "Di dalam game, tekan ✎ (kanan atas) untuk memindahkan elemen HUD.",
        "Quick Launch melewati layar ini dan langsung membuka game.",
        "Instance menyimpan profil modul & tema HUD yang berbeda-beda."
    )
    private var tipIdx = 0
    private val RED = Color.parseColor("#F44336")
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun page(a: MainActivity) = Ui.vbox(a).apply { setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 24)) }

    // ================= HOME =================
    fun home(a: MainActivity): View {
        val status = MinecraftGuard.status(a)
        if (status !is MinecraftStatus.Compatible) return dependency(a, status)
        fun dp(v: Int) = Ui.dp(a, v)
        val accent = ModuleConfig.accentColor()
        val guard = MinecraftGuard.check(a)
        val land = a.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root = page(a)

        val hero = Ui.card(a).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#101A30"), Color.parseColor("#0B0D0F"))
            ).apply { cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.parseColor("#1769E8")) }
            setPadding(dp(18), dp(18), dp(18), dp(16))
        }
        val brandRow = Ui.hbox(a)
        val brandIcon = ImageView(a).apply {
            setImageResource(R.drawable.feather_ingame_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Feather Client logo"
        }
        brandRow.addView(brandIcon, lp(dp(76), dp(76)))
        val brandText = Ui.vbox(a).apply { setPadding(dp(14), 0, 0, 0) }
        brandText.addView(Ui.text(a, "FEATHER CLIENT", 12f, Color.parseColor("#35B7FF"), true))
        brandText.addView(Ui.text(a, "Minecraft", 29f, Color.WHITE, true))
        brandText.addView(Ui.text(a, if (guard is MinecraftGuard.Result.Ok) "Bedrock Edition  •  ${guard.version}" else "Bedrock Edition", 13f, Color.parseColor("#AAB8D0")))
        brandRow.addView(brandText, lp(0, -2, 1f))
        hero.addView(brandRow)
        if (guard is MinecraftGuard.Result.Fail) hero.addView(Ui.text(a, "⚠ ${guard.reason}", 13f, RED).apply { setPadding(0, dp(6), 0, 0) })

        val controls = Ui.hbox(a).apply { setPadding(0, dp(14), 0, dp(10)) }
        val inst = Ui.hbox(a).apply {
            background = Ui.shape(a, Ui.CARD2, null, 12); setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { pickInstance(a) }
        }
        val it = Ui.vbox(a)
        it.addView(Ui.text(a, "Current Instance", 11f, Ui.MUTED))
        it.addView(Ui.text(a, InstanceStore.name(a, InstanceStore.current(a)), 16f, Ui.FG, true))
        inst.addView(it, lp(0, -2, 1f)); inst.addView(Ui.text(a, "⌄", 18f, Ui.MUTED))
        controls.addView(inst, lp(0, -2, 1f))
        controls.addView(Ui.button(a, if (guard is MinecraftGuard.Result.Ok) "🚀 Launch" else "Install Minecraft", true) {
            if (guard is MinecraftGuard.Result.Ok) a.launchGame() else a.openUrl("market://details?id=${MinecraftGuard.MC_PACKAGE}")
        }, lp(-2, -2).apply { leftMargin = dp(10) })
        hero.addView(controls)
        hero.addView(Ui.text(a, "💡 ${tips[tipIdx % tips.size]}", 13f, Ui.MUTED).apply {
            setOnClickListener { tipIdx++; a.refresh() }
        })
        root.addView(hero)

        // Mods
        val mods = Ui.card(a)
        val mh = Ui.hbox(a)
        mh.addView(Ui.text(a, "🧩  Mods", 18f, Ui.FG, true), lp(0, -2, 1f))
        mh.addView(Ui.button(a, "Manage Mods", false) { a.push { modsScreen(a) } })
        mods.addView(mh)
        val on = ModuleConfig.modules.filter { it.kind == CardKind.TOGGLE && it.impl != Impl.UNSUPPORTED && ModuleConfig.isOn(a, it) }
        mods.addView(Ui.text(a, if (on.isEmpty()) "Belum ada mod aktif" else on.joinToString("\n") { "• ${it.title}" }, 13f, Ui.MUTED).apply { setPadding(0, dp(10), 0, 0) })

        // Content Management
        val cm = Ui.card(a)
        val ch = Ui.hbox(a)
        ch.addView(Ui.text(a, "📦  Content Management", 18f, Ui.FG, true), lp(0, -2, 1f))
        ch.addView(Ui.text(a, "View All →", 13f, Ui.MUTED).apply { setOnClickListener { a.push { contentScreen(a, Kind.WORLDS) } } })
        cm.addView(ch)
        val icons = mapOf(Kind.WORLDS to "🌍", Kind.RESOURCE to "🖼", Kind.BEHAVIOR to "⚙")
        Kind.values().forEach { k -> cm.addView(Ui.row(a, icons[k]!!, k.label, "${ContentManager.list(a, k).size} ›") { a.push { contentScreen(a, k) } }) }

        // Misc
        val misc = Ui.card(a)
        misc.addView(Ui.text(a, "🗂  Miscellaneous", 18f, Ui.FG, true).apply { setPadding(0, 0, 0, dp(4)) })
        misc.addView(Ui.row(a, "🔶", "CurseForge", "›") { a.openUrl("https://www.curseforge.com/minecraft-bedrock") })
        misc.addView(Ui.row(a, "👤", "Microsoft Accounts", "›") { microsoftInfo(a) })
        misc.addView(Ui.row(a, "🚀", "Quick Launch", if (Prefs.quickLaunch(a)) "ON" else "OFF") {
            Prefs.setQuickLaunch(a, !Prefs.quickLaunch(a)); a.refresh()
        })

        val cards = if (land) Ui.hbox(a).apply { gravity = Gravity.TOP } else Ui.vbox(a)
        listOf(mods, cards.let { cm }, misc).forEach { v ->
            cards.addView(v, if (land) lp(0, -2, 1f).apply { leftMargin = dp(5); rightMargin = dp(5) } else lp(-1, -2).apply { topMargin = dp(10) })
        }
        root.addView(cards, lp(-1, -2).apply { topMargin = dp(10) })
        return root
    }

    // ================= DEPENDENSI MINECRAFT =================
    fun dependency(a: MainActivity, s: MinecraftStatus): View {
        fun dp(v: Int) = Ui.dp(a, v)
        val root = page(a)
        val (icon, title, body) = when (s) {
            is MinecraftStatus.NotInstalled -> Triple("🎮", "Minecraft Bedrock Edition diperlukan",
                "Feather Client adalah add-on untuk Minecraft Bedrock Edition di Android dan tidak bisa berjalan sendiri, jadi game belum bisa dimulai.\n\nInstall Minecraft dari Google Play, lalu kembali ke sini dan tekan Coba Lagi.")
            is MinecraftStatus.Incompatible -> Triple("⚠️", "Minecraft terdeteksi, tetapi tidak kompatibel", s.reason)
            is MinecraftStatus.Error -> Triple("❗", "Gagal memeriksa Minecraft", s.message + "\n\nIni bisa bersifat sementara. Tekan Coba Lagi. Jika terus terjadi, salin info diagnostik.")
            is MinecraftStatus.Compatible -> Triple("✓", "Minecraft siap", "")
        }
        val c = Ui.card(a)
        c.addView(Ui.text(a, icon, 40f))
        c.addView(Ui.text(a, title, 20f, Ui.FG, true).apply { setPadding(0, dp(8), 0, dp(6)) })
        c.addView(Ui.text(a, body, 14f, Ui.MUTED))
        val btns = Ui.vbox(a).apply { setPadding(0, dp(14), 0, 0) }
        btns.addView(Ui.button(a, "↻  Coba Lagi", true) { a.recheckMinecraft() }, lp(-1, -2))
        if (s is MinecraftStatus.NotInstalled || s is MinecraftStatus.Incompatible)
            btns.addView(Ui.button(a, "Buka di Google Play", false) { a.openPlayStore() }, lp(-1, -2).apply { topMargin = dp(8) })
        if (s is MinecraftStatus.Incompatible && s.checks.any { it.label == "Aplikasi aktif" && !it.ok })
            btns.addView(Ui.button(a, "Buka info aplikasi Minecraft", false) { a.openAppInfo() }, lp(-1, -2).apply { topMargin = dp(8) })
        btns.addView(Ui.button(a, "Salin info diagnostik", false) { a.copyDiagnostics() }, lp(-1, -2).apply { topMargin = dp(8) })
        c.addView(btns)
        root.addView(c)

        val checks = if (s is MinecraftStatus.Incompatible) s.checks else emptyList()
        if (checks.isNotEmpty()) {
            root.addView(Ui.section(a, "PEMERIKSAAN"))
            val list = Ui.card(a)
            checks.forEach { ck ->
                list.addView(Ui.text(a, (if (ck.ok) "✓  " else "✗  ") + ck.label, 14f, if (ck.ok) Ui.FG else RED, !ck.ok).apply { setPadding(0, dp(5), 0, 0) })
                if (ck.detail.isNotEmpty() && !ck.ok) list.addView(Ui.text(a, ck.detail, 12f, Ui.MUTED).apply { setPadding(dp(22), 0, 0, 0) })
            }
            root.addView(list)
        }
        return root
    }

    private fun pickInstance(a: MainActivity) {
        val ids = InstanceStore.ids(a)
        AlertDialog.Builder(a).setTitle("Pilih Instance")
            .setItems(ids.map { InstanceStore.name(a, it) }.toTypedArray()) { _, i ->
                InstanceStore.setCurrent(a, ids[i]); ModuleConfig.load(a); a.refresh()
            }.show()
    }

    private fun microsoftInfo(a: MainActivity) {
        AlertDialog.Builder(a).setTitle("Microsoft Accounts")
            .setMessage("Login Microsoft/Xbox dilakukan di dalam Minecraft saat game berjalan. Feather Client tidak menyimpan password atau token Microsoft kamu.\n\nBuka halaman akun Microsoft di browser?")
            .setPositiveButton("Buka") { _, _ -> a.openUrl("https://account.microsoft.com") }
            .setNegativeButton("Tutup", null).show()
    }

    // ================= MODS =================
    fun modsScreen(a: MainActivity): View {
        val root = page(a)
        root.addView(Ui.text(a, "Mods", 22f, Ui.FG, true))
        root.addView(Ui.text(a, "Profil: ${InstanceStore.name(a, InstanceStore.current(a))}. Modul yang bergantung hook diaktifkan dari menu in-game setelah hook aktif.", 12f, Ui.MUTED).apply { setPadding(0, 0, 0, Ui.dp(a, 10)) })
        ModuleRegistry.CATEGORIES.forEach { cat ->
            val list = ModuleRegistry.all.filter { it.categories.first() == cat && it.kind == CardKind.TOGGLE }
            if (list.isEmpty()) return@forEach
            root.addView(Ui.section(a, cat.uppercase()))
            list.forEach { m ->
                when {
                    m.impl == Impl.UNSUPPORTED -> root.addView(Ui.text(a, "${m.icon} ${m.title} — belum didukung", 13f, Ui.MUTED).apply { setPadding(Ui.dp(a, 4), Ui.dp(a, 4), 0, Ui.dp(a, 4)) })
                    m.impl == Impl.HOOK -> root.addView(Ui.text(a, "${m.icon} ${m.title} — butuh hook (aktifkan di menu in-game)", 13f, Ui.MUTED).apply { setPadding(Ui.dp(a, 4), Ui.dp(a, 4), 0, Ui.dp(a, 4)) })
                    else -> root.addView(Ui.switchRow(a, "${m.icon}  ${m.title}", m.desc, ModuleConfig.isOn(a, m)) { on ->
                        ModuleConfig.set(a, m, on); try { NativeBridge.setModule(m.id, on) } catch (_: Throwable) {}
                    })
                }
            }
        }
        return root
    }

    // ================= CONTENT =================
    fun contentScreen(a: MainActivity, kind: Kind): View {
        fun dp(v: Int) = Ui.dp(a, v)
        val root = page(a)
        root.addView(Ui.text(a, "Content Management", 22f, Ui.FG, true))
        val seg = Ui.hbox(a).apply { setPadding(0, dp(10), 0, dp(10)) }
        Kind.values().forEach { k ->
            val b = Ui.button(a, "${k.label} (${ContentManager.list(a, k).size})", k == kind) { a.replaceTop { contentScreen(a, k) } }
            b.textSize = 11f; b.setPadding(dp(4), dp(10), dp(4), dp(10))
            seg.addView(b, lp(0, -2, 1f).apply { rightMargin = dp(6) })
        }
        root.addView(seg)
        root.addView(Ui.button(a, "＋  Import (.mcworld / .mcpack / .mcaddon)", true) { a.pickFiles() }, lp(-1, -2))

        val list = Ui.card(a).apply { (layoutParams as? LinearLayout.LayoutParams) }
        val items = ContentManager.list(a, kind)
        if (items.isEmpty()) list.addView(Ui.text(a, "Kosong. Tekan Import atau buka file dari CurseForge.", 13f, Ui.MUTED))
        items.forEach { f ->
            val r = Ui.hbox(a).apply { setPadding(0, dp(9), 0, dp(9)) }
            val t = Ui.vbox(a)
            t.addView(Ui.text(a, ContentManager.displayName(kind, f), 15f))
            t.addView(Ui.text(a, "${ContentManager.sizeMb(f)} MB", 11f, Ui.MUTED))
            r.addView(t, lp(0, -2, 1f))
            if (kind == Kind.WORLDS) r.addView(Ui.text(a, "📤", 20f).apply {
                setPadding(dp(12), dp(4), dp(4), dp(4)); setOnClickListener { a.exportWorld(f) }
            })
            r.addView(Ui.text(a, "🗑", 20f).apply {
                setPadding(dp(12), dp(4), dp(4), dp(4))
                setOnClickListener {
                    AlertDialog.Builder(a).setTitle("Hapus?").setMessage(ContentManager.displayName(kind, f))
                        .setPositiveButton("Hapus") { _, _ -> ContentManager.delete(f); a.refresh() }
                        .setNegativeButton("Batal", null).show()
                }
            })
            list.addView(r)
        }
        root.addView(list, lp(-1, -2).apply { topMargin = dp(12) })
        root.addView(Ui.text(a, ContentManager.dir(a, kind).absolutePath, 10f, Ui.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        return root
    }

    // ================= INSTANCES =================
    fun instances(a: MainActivity): View {
        fun dp(v: Int) = Ui.dp(a, v)
        val root = page(a)
        root.addView(Ui.text(a, "Instances", 22f, Ui.FG, true))
        root.addView(Ui.text(a, "Tiap instance punya profil modul & tema HUD sendiri. World/pack dipakai bersama.", 12f, Ui.MUTED).apply { setPadding(0, 0, 0, dp(10)) })
        val cur = InstanceStore.current(a)
        InstanceStore.ids(a).forEach { id ->
            val c = Ui.card(a)
            val h = Ui.hbox(a)
            h.addView(Ui.text(a, InstanceStore.name(a, id) + if (id == cur) "  ✓ aktif" else "", 17f, if (id == cur) ModuleConfig.accentColor() else Ui.FG, true), lp(0, -2, 1f))
            c.addView(h)
            val btns = Ui.hbox(a).apply { setPadding(0, dp(10), 0, 0) }
            fun small(t: String, f: Boolean, act: () -> Unit) = Ui.button(a, t, f, act).also { btns.addView(it, lp(0, -2, 1f).apply { rightMargin = dp(6) }) }
            if (id != cur) small("Pilih", true) { InstanceStore.setCurrent(a, id); ModuleConfig.load(a); a.refresh() }
            small("Ubah nama", false) { Ui.prompt(a, "Nama instance", InstanceStore.name(a, id)) { InstanceStore.rename(a, id, it); a.refresh() } }
            small("Hapus", false) {
                AlertDialog.Builder(a).setTitle("Hapus instance?").setMessage(InstanceStore.name(a, id))
                    .setPositiveButton("Hapus") { _, _ -> if (!InstanceStore.delete(a, id)) Ui.toast(a, "Minimal harus ada 1 instance"); ModuleConfig.load(a); a.refresh() }
                    .setNegativeButton("Batal", null).show()
            }
            c.addView(btns)
            root.addView(c, lp(-1, -2).apply { bottomMargin = dp(10) })
        }
        root.addView(Ui.button(a, "＋  Instance Baru", true) {
            Ui.prompt(a, "Nama instance baru", "Instance ${InstanceStore.ids(a).size + 1}") { n -> InstanceStore.setCurrent(a, InstanceStore.create(a, n)); ModuleConfig.load(a); a.refresh() }
        }, lp(-1, -2))
        return root
    }

    // ================= SETTINGS =================
    fun settings(a: MainActivity): View {
        fun dp(v: Int) = Ui.dp(a, v)
        val root = page(a)
        root.addView(Ui.text(a, "Settings", 22f, Ui.FG, true))
        root.addView(Ui.section(a, "UMUM"))
        root.addView(Ui.switchRow(a, "Quick Launch", "Langsung buka game saat app dibuka", Prefs.quickLaunch(a)) { Prefs.setQuickLaunch(a, it) })

        root.addView(Ui.section(a, "TEMA MENU"))
        val tm = Ui.hbox(a)
        listOf("Sistem", "Gelap", "Terang").forEachIndexed { i, n ->
            tm.addView(Ui.button(a, n, Prefs.themeMode(a) == i) { Prefs.setThemeMode(a, i); a.recreate() }, lp(0, -2, 1f).apply { rightMargin = dp(6) })
        }
        root.addView(tm)

        root.addView(Ui.section(a, "TAMPILAN HUD  (instance: ${InstanceStore.name(a, InstanceStore.current(a))})"))
        root.addView(Ui.slider(a, "Opacity latar", 0, 100, ModuleConfig.opacity) { ModuleConfig.setOpacity(a, it) })
        root.addView(Ui.slider(a, "Ukuran HUD (%)", 70, 160, ModuleConfig.scale) { ModuleConfig.setScale(a, it) })
        val box = Ui.hbox(a).apply { background = Ui.shape(a, Ui.CARD, Ui.BORDER); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        box.addView(Ui.text(a, "Warna aksen", 14f), lp(0, -2, 1f))
        ModuleConfig.accents.forEachIndexed { i, hex ->
            box.addView(View(a).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex)); if (i == ModuleConfig.accentIdx) setStroke(dp(3), Ui.FG) }
                setOnClickListener { ModuleConfig.setAccent(a, i); a.refresh() }
            }, lp(dp(28), dp(28)).apply { leftMargin = dp(8) })
        }
        root.addView(box, lp(-1, -2).apply { bottomMargin = dp(8) })
        root.addView(Ui.button(a, "Reset posisi HUD", false) { ModuleConfig.resetPositions(a); Ui.toast(a, "Posisi HUD direset") }, lp(-1, -2))

        root.addView(Ui.section(a, "HOOK (DEVELOPER)"))
        val pf = File(a.filesDir, "patterns.txt")
        val hs = try { NativeBridge.hookStatus() } catch (_: Throwable) { "-" }
        root.addView(Ui.text(a, "Status: $hs\n${if (pf.exists()) "patterns.txt ✓" else "patterns.txt belum ada"}\n${pf.absolutePath}", 12f, Ui.MUTED))
        root.addView(Ui.button(a, "Import patterns.txt", false) { a.pickPatterns() }, lp(-1, -2).apply { topMargin = dp(8) })
        val et = EditText(a).apply { hint = "Pattern, mis: FD 7B BF A9 ?? ??"; setTextColor(Ui.FG); setHintTextColor(Ui.MUTED); textSize = 12f }
        root.addView(et)
        val res = Ui.text(a, "", 12f, ModuleConfig.accentColor())
        root.addView(Ui.button(a, "Scan pattern (jalankan game dulu)", false) {
            res.text = try { NativeBridge.scanPattern(et.text.toString()) } catch (t: Throwable) { "error: ${t.message}" }
        })
        root.addView(res)

        root.addView(Ui.section(a, "DATA"))
        root.addView(Ui.button(a, "Bersihkan cache impor", false) {
            a.cacheDir.listFiles()?.forEach { it.deleteRecursively() }; Ui.toast(a, "Cache dibersihkan")
        }, lp(-1, -2))
        root.addView(Ui.text(a, "Folder konten:\n${ContentManager.root(a).absolutePath}", 11f, Ui.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        return root
    }

    // ================= ABOUT =================
    fun about(a: MainActivity): View {
        val root = page(a)
        val c = Ui.card(a)
        c.addView(Ui.text(a, "🪶 ${ModuleConfig.CLIENT_NAME}", 26f, ModuleConfig.accentColor(), true))
        c.addView(Ui.text(a, "Versi ${ModuleConfig.VERSION}  •  oleh ${ModuleConfig.AUTHOR}", 14f, Ui.MUTED))
        val g = MinecraftGuard.check(a)
        val hs = try { NativeBridge.hookStatus() } catch (_: Throwable) { "-" }
        c.addView(Ui.text(a, "\nMinecraft: ${if (g is MinecraftGuard.Result.Ok) g.version else "tidak terdeteksi"}\nHook native: $hs\nInstance aktif: ${InstanceStore.name(a, InstanceStore.current(a))}", 14f))
        c.addView(Ui.text(a, "\nClient visual & kenyamanan untuk Minecraft Bedrock. Tidak ada fitur cheat. Butuh Minecraft asli terinstall. Bukan produk resmi Mojang/Microsoft.", 12f, Ui.MUTED))
        root.addView(c)
        return root
    }
}
