package com.example.bedrockclient

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import dalvik.system.DexClassLoader

/**
 * Memasang classloader yang membaca kode + native lib dari APK Minecraft ASLI yang terinstall.
 * Tidak ada file Minecraft yang disalin/dibundel.
 *
 * PERINGATAN: memakai reflection ke API internal Android (ContextImpl.mPackageInfo.mClassLoader).
 * Pola ini rapuh antar versi Android -> uji di perangkatmu.
 */
class HostApplication : Application() {
    companion object {
        const val TAG = "FeatherClient"
        const val MC_MAIN = MinecraftGuard.MC_MAIN_ACTIVITY
        @Volatile var loaderStamp: Long? = null; private set      // lastUpdateTime Minecraft saat loader dipasang
        @Volatile var loaderError: String? = null; private set
        private var appBase: Context? = null

        /** Idempoten. Dipanggil saat proses mulai DAN saat peluncuran (mis. Minecraft diinstall setelah Feather terbuka). Return null jika siap. */
        @Synchronized fun ensureLoader(): String? {
            val base = appBase ?: return "Application belum siap"
            val s = MinecraftGuard.status(base)
            if (s !is MinecraftStatus.Compatible) return MinecraftGuard.describe(s)
            loaderStamp?.let { return if (it == s.stamp) null else "Minecraft diperbarui setelah Feather dibuka. Tutup Feather sepenuhnya lalu buka lagi." }
            return try { install(base); loaderStamp = s.stamp; loaderError = null; null }
            catch (t: Throwable) { Log.e(TAG, "loader gagal", t); loaderError = "${t.javaClass.simpleName}: ${t.message}"; loaderError }
        }

        private fun install(base: Context) {
            val mc = base.packageManager.getApplicationInfo(MinecraftGuard.MC_PACKAGE, 0)
            val apks = listOfNotNull(mc.sourceDir) + (mc.splitSourceDirs?.toList() ?: emptyList())
            // nativeLibraryDir untuk lib yang diekstrak + "apk!/lib/<abi>" untuk lib uncompressed di dalam APK/split.
            val libPath = (listOfNotNull(mc.nativeLibraryDir) + apks.map { "$it!/lib/${MinecraftGuard.ABI}" }).joinToString(":")
            val odex = base.getDir("mc_odex", Context.MODE_PRIVATE).absolutePath

            val original = base.classLoader
            val loader = DexClassLoader(apks.joinToString(":"), odex, libPath, original)   // parent-first

            // Verifikasi antarmuka yang dibutuhkan SEBELUM memasang: kelas ada, bisa dimuat, dan merupakan Activity.
            val cls = try { Class.forName(MC_MAIN, false, loader) } catch (e: Throwable) { throw IllegalStateException("$MC_MAIN tidak bisa dimuat dari APK Minecraft (${e.javaClass.simpleName})") }
            if (!Activity::class.java.isAssignableFrom(cls)) throw IllegalStateException("$MC_MAIN bukan Activity")

            val loadedApk = try {
                val f = Class.forName("android.app.ContextImpl").getDeclaredField("mPackageInfo").apply { isAccessible = true }
                f.get(unwrap(base))
            } catch (e: Throwable) { throw IllegalStateException("API internal Android diblokir di perangkat ini (${e.javaClass.simpleName})") }
            val clField = loadedApk.javaClass.getDeclaredField("mClassLoader").apply { isAccessible = true }
            clField.set(loadedApk, loader)
            Log.i(TAG, "Minecraft classloader terpasang: $libPath")
        }

        private fun unwrap(c: Context): Context { var x = c; while (x is android.content.ContextWrapper && x.baseContext != null) x = x.baseContext; return x }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        appBase = base
        ensureLoader()      // hasil dicatat di loaderError; peluncuran akan memeriksa ulang
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, s: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) { if (a.javaClass.name == MC_MAIN) { Prefs.setLaunchPending(a, false); HudOverlay.attach(a) } }
            override fun onActivityPaused(a: Activity) { if (a.javaClass.name == MC_MAIN) HudOverlay.detach() }
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, o: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
