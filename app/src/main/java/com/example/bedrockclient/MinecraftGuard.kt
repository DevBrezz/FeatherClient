package com.example.bedrockclient

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class Check(val label: String, val ok: Boolean, val detail: String = "")

/** Hasil pemeriksaan dependensi Minecraft Bedrock. */
sealed class MinecraftStatus {
    /** Semua prasyarat statis lolos. TIDAK menjamin game bisa dimuat; itu diverifikasi lagi saat peluncuran. */
    data class Compatible(val version: String, val code: Long, val stamp: Long, val installer: String?, val checks: List<Check>) : MinecraftStatus()
    object NotInstalled : MinecraftStatus()
    data class Incompatible(val reason: String, val checks: List<Check>) : MinecraftStatus()
    /** Kegagalan sementara/tak terduga saat memeriksa (bukan berarti Minecraft tidak ada). */
    data class Error(val message: String) : MinecraftStatus()
}

object MinecraftGuard {
    const val MC_PACKAGE = "com.mojang.minecraftpe"                       // Minecraft Bedrock (Android) di Google Play
    const val MC_MAIN_ACTIVITY = "com.mojang.minecraftpe.MainActivity"    // diverifikasi nyata via PackageManager, bukan diasumsikan
    const val NATIVE_LIB = "libminecraftpe.so"
    const val ABI = "arm64-v8a"                                           // satu-satunya ABI yang dibangun Feather

    /** Versi minimum yang sudah DIUJI. 0 = belum ada versi yang dikonfirmasi, jadi tidak dibatasi. Isi setelah pengujian. */
    const val MIN_VERSION_CODE = 0L
    /** Isi dengan SHA-256 signature rilis Minecraft (dari APK asli milikmu) untuk mengaktifkan verifikasi keaslian. */
    private val TRUSTED_SIGNATURES = setOf<String>()

    @Volatile private var cached: MinecraftStatus? = null
    fun invalidate() { cached = null }

    fun status(ctx: Context, force: Boolean = false): MinecraftStatus {
        if (!force) cached?.let { return it }
        val s = try { evaluate(ctx) } catch (t: Throwable) { MinecraftStatus.Error("${t.javaClass.simpleName}: ${t.message}") }
        cached = s
        return s
    }

    fun describe(s: MinecraftStatus): String = when (s) {
        is MinecraftStatus.Compatible -> "Minecraft ${s.version} kompatibel"
        is MinecraftStatus.NotInstalled -> "Minecraft Bedrock belum terinstall."
        is MinecraftStatus.Incompatible -> "Minecraft tidak kompatibel: ${s.reason}"
        is MinecraftStatus.Error -> "Gagal memeriksa Minecraft: ${s.message}"
    }

    // ---- API lama (dipertahankan agar kode lain tetap jalan) ----
    sealed class Result {
        data class Ok(val version: String, val code: Long) : Result()
        data class Fail(val reason: String) : Result()
    }
    fun check(ctx: Context): Result = when (val s = status(ctx)) {
        is MinecraftStatus.Compatible -> Result.Ok(s.version, s.code)
        else -> Result.Fail(describe(s))
    }

    // ---- Pemeriksaan ----
    private fun evaluate(ctx: Context): MinecraftStatus {
        val pm = ctx.packageManager
        val needSig = TRUSTED_SIGNATURES.isNotEmpty()
        val info = try { pkgInfo(pm, needSig) }
            catch (e: PackageManager.NameNotFoundException) { return MinecraftStatus.NotInstalled }
            catch (e: Exception) { return MinecraftStatus.Error("${e.javaClass.simpleName}: ${e.message}") }
        val ai = info.applicationInfo ?: return MinecraftStatus.Error("ApplicationInfo Minecraft kosong")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

        val checks = mutableListOf<Check>()
        checks += Check("Minecraft terinstall", true, "${info.versionName ?: "?"} ($code)")
        checks += Check("Aplikasi aktif", ai.enabled, if (ai.enabled) "" else "Minecraft dinonaktifkan di pengaturan Android. Aktifkan kembali.")

        val dev64 = Build.SUPPORTED_64_BIT_ABIS.contains(ABI)
        checks += Check("Perangkat mendukung $ABI", dev64, if (dev64) "" else "Feather hanya mendukung $ABI. ABI perangkat ini: ${Build.SUPPORTED_ABIS.joinToString()}.")

        val mcAbi = primaryAbi(ai)
        val abiOk = mcAbi == null || mcAbi == ABI
        checks += Check("ABI Minecraft = $ABI", abiOk, if (abiOk) (mcAbi ?: "tidak terbaca (tidak memblokir)") else "Minecraft berjalan sebagai $mcAbi, sedangkan Feather hanya mendukung $ABI.")

        val lib = hasNativeLib(ai)
        checks += Check("Library game ($NATIVE_LIB)", lib, if (lib) "" else "$NATIVE_LIB untuk $ABI tidak ditemukan di instalasi Minecraft ini.")

        val act = try { pm.getActivityInfo(ComponentName(MC_PACKAGE, MC_MAIN_ACTIVITY), 0); true }
            catch (e: PackageManager.NameNotFoundException) { false }
            catch (e: Exception) { return MinecraftStatus.Error("${e.javaClass.simpleName}: ${e.message}") }
        checks += Check("Activity utama ($MC_MAIN_ACTIVITY)", act, if (act) "" else "Activity utama tidak ditemukan. Varian/versi Minecraft ini tidak didukung.")

        if (MIN_VERSION_CODE > 0) checks += Check("Versi ≥ $MIN_VERSION_CODE", code >= MIN_VERSION_CODE, if (code >= MIN_VERSION_CODE) "" else "Versi $code terlalu lama. Perbarui Minecraft.")

        if (needSig) {
            val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures
            val ok = sigs?.any { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(":") { "%02X".format(it) } in TRUSTED_SIGNATURES } == true
            checks += Check("Signature resmi", ok, if (ok) "" else "Signature tidak cocok / tidak terbaca. Minecraft mungkin bukan versi resmi.")
        }

        val failed = checks.firstOrNull { !it.ok }
        if (failed != null) return MinecraftStatus.Incompatible(failed.detail.ifEmpty { failed.label }, checks)
        return MinecraftStatus.Compatible(info.versionName ?: "?", code, info.lastUpdateTime, installer(pm), checks)
    }

    private fun pkgInfo(pm: PackageManager, sig: Boolean): PackageInfo {
        val flags = if (!sig) 0 else if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        return if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(MC_PACKAGE, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageInfo(MC_PACKAGE, flags)
    }

    private fun installer(pm: PackageManager): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(MC_PACKAGE).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(MC_PACKAGE)
    } catch (_: Exception) { null }

    /** primaryCpuAbi adalah field tersembunyi; jika tidak terbaca, hasilnya null dan tidak memblokir. */
    private fun primaryAbi(ai: ApplicationInfo): String? = try {
        ApplicationInfo::class.java.getDeclaredField("primaryCpuAbi").apply { isAccessible = true }.get(ai) as? String
    } catch (_: Throwable) { null }

    /** Library bisa diekstrak ke nativeLibraryDir, atau tersimpan uncompressed di APK/split (extractNativeLibs=false). */
    private fun hasNativeLib(ai: ApplicationInfo): Boolean {
        ai.nativeLibraryDir?.let { if (File(it, NATIVE_LIB).exists()) return true }
        val paths = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
        return paths.any { p -> try { ZipFile(p).use { it.getEntry("lib/$ABI/$NATIVE_LIB") != null } } catch (_: Exception) { false } }
    }

    fun diagnostics(ctx: Context, s: MinecraftStatus): String = buildString {
        appendLine("Feather Client ${ModuleConfig.VERSION}  |  Android API ${Build.VERSION.SDK_INT}  |  ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("ABI perangkat: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Status: ${describe(s)}")
        val checks = when (s) { is MinecraftStatus.Compatible -> s.checks; is MinecraftStatus.Incompatible -> s.checks; else -> emptyList() }
        checks.forEach { appendLine((if (it.ok) "[OK]   " else "[GAGAL] ") + it.label + (if (it.detail.isNotEmpty()) " — ${it.detail}" else "")) }
        if (s is MinecraftStatus.Compatible) appendLine("Sumber instalasi: ${s.installer ?: "tidak diketahui"}")
        appendLine("Loader: ${HostApplication.loaderError ?: if (HostApplication.loaderStamp != null) "terpasang" else "belum dipasang"}")
    }
}
