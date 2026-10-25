package com.example.bedrockclient

import android.content.Context
import android.content.Intent
import android.os.Build

object ModLoader {
    /** Return null jika peluncuran dimulai, atau pesan error yang jelas. Semua pemeriksaan dilakukan SEBELUM startActivity. */
    fun launch(ctx: Context): String? {
        // 1. Periksa ulang dependensi (bukan memakai hasil lama).
        MinecraftGuard.invalidate()
        val s = MinecraftGuard.status(ctx, force = true)
        if (s !is MinecraftStatus.Compatible) return MinecraftGuard.describe(s)

        // 2. Pastikan classloader Minecraft siap & kelas utama bisa dimuat (kegagalan di ActivityThread = crash, tidak bisa di-catch).
        HostApplication.ensureLoader()?.let { return "Loader Minecraft belum siap: $it" }

        // 3. Library native Feather (UnsatisfiedLinkError bila ABI tidak cocok).
        try {
            NativeBridge.init(ModuleConfig.serialize(ctx), s.code, java.io.File(ctx.filesDir, "patterns.txt").absolutePath)
            NativeBridge.setZoomLevel(ModuleConfig.getInt(ctx, "zoom", "level", 25) / 100f)
        } catch (t: Throwable) {
            return "Library native Feather gagal dimuat (${t.javaClass.simpleName}: ${t.message}). ABI perangkat: ${Build.SUPPORTED_ABIS.joinToString()}"
        }

        ToolState.sessionStart = 0L      // sesi baru dimulai saat game tampil

        // 4. Luncurkan. Flag 'pending' dihapus saat MainActivity game benar-benar resume; jika tersisa, peluncuran sebelumnya tidak selesai.
        Prefs.setLaunchPending(ctx, true)
        val i = Intent().setClassName(ctx.packageName, HostApplication.MC_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); null } catch (e: Exception) {
            Prefs.setLaunchPending(ctx, false); "Gagal membuka game: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
