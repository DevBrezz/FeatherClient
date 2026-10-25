package com.example.bedrockclient

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

/**
 * Mengelola world / resource pack / behavior pack.
 * Lokasi: <externalFilesDir>/games/com.mojang/{minecraftWorlds,resource_packs,behavior_packs}
 * (Karena game berjalan di dalam paket app ini, Minecraft membaca folder ini. Verifikasi di perangkatmu.)
 */
object ContentManager {
    enum class Kind(val label: String, val dir: String) {
        WORLDS("Worlds", "minecraftWorlds"), RESOURCE("Resource Packs", "resource_packs"), BEHAVIOR("Behavior Packs", "behavior_packs")
    }
    data class Report(var worlds: Int = 0, var resources: Int = 0, var behaviors: Int = 0, val errors: MutableList<String> = mutableListOf()) {
        fun merge(o: Report) { worlds += o.worlds; resources += o.resources; behaviors += o.behaviors; errors += o.errors }
        fun summary(): String {
            val ok = worlds + resources + behaviors
            val base = if (ok == 0) "Tidak ada konten valid ditemukan" else "Diimpor: $worlds world, $resources resource pack, $behaviors behavior pack"
            return if (errors.isEmpty()) base else base + "\nError: " + errors.first()
        }
    }
    private const val MAX_BYTES = 2L * 1024 * 1024 * 1024

    fun root(c: Context): File = File(c.getExternalFilesDir(null), "games/com.mojang").apply { mkdirs() }
    fun dir(c: Context, k: Kind): File = File(root(c), k.dir).apply { mkdirs() }
    fun list(c: Context, k: Kind): List<File> = dir(c, k).listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
    fun delete(f: File) = f.deleteRecursively()

    fun displayName(k: Kind, f: File): String {
        try {
            if (k == Kind.WORLDS) File(f, "levelname.txt").takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            else File(f, "manifest.json").takeIf { it.exists() }?.let {
                val n = JSONObject(it.readText().trimStart('\uFEFF')).getJSONObject("header").getString("name")
                if (!n.startsWith("pack.") && n.isNotBlank()) return n
            }
        } catch (_: Exception) {}
        return f.name
    }
    fun sizeMb(f: File): Long = f.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024)

    // ---------- Export ----------
    /** Zip isi folder world ke .mcworld (file berada di root zip, sesuai format Minecraft). */
    fun exportWorld(world: File, out: OutputStream) {
        if (!File(world, "level.dat").exists()) throw IOException("Bukan world yang valid (level.dat tidak ada)")
        ZipOutputStream(out.buffered()).use { z ->
            world.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(world).path.replace(File.separatorChar, '/')))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    // ---------- Import ----------
    fun importUri(c: Context, uri: Uri): Report {
        val r = Report()
        val tmp = File(c.cacheDir, "import_${System.nanoTime()}").apply { mkdirs() }
        try {
            val zip = File(tmp, "src.zip")
            c.contentResolver.openInputStream(uri)?.use { i -> zip.outputStream().use { o -> i.copyTo(o) } } ?: throw IOException("Tidak bisa membaca file")
            importZip(c, zip, tmp, r, 0)
        } catch (e: Exception) { r.errors.add(e.message ?: e.toString()) } finally { tmp.deleteRecursively() }
        return r
    }

    private fun importZip(c: Context, zip: File, work: File, r: Report, depth: Int) {
        if (depth > 3) return
        val out = File(work, "x_${System.nanoTime()}").apply { mkdirs() }
        try { unzip(zip, out) } catch (e: Exception) { r.errors.add("${zip.name}: ${e.message}"); return }
        scan(c, out, work, r, depth, zip.nameWithoutExtension)
    }

    private fun unzip(zip: File, dest: File) {
        val base = dest.canonicalPath + File.separator
        var total = 0L
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val f = File(dest, e.name)
                if (!f.canonicalPath.startsWith(base)) throw SecurityException("Entry zip tidak aman")   // zip-slip
                if (e.isDirectory) f.mkdirs() else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { o -> total += zin.copyTo(o); if (total > MAX_BYTES) throw IOException("File terlalu besar") }
                }
                e = zin.nextEntry
            }
        }
    }

    private fun scan(c: Context, dir: File, work: File, r: Report, depth: Int, topName: String) {
        val label = if (dir.name.startsWith("x_")) topName else dir.name
        when {
            File(dir, "level.dat").exists() -> { place(c, Kind.WORLDS, dir, label); r.worlds++; return }
            File(dir, "manifest.json").exists() -> {
                val k = packKind(dir); place(c, k, dir, label)
                if (k == Kind.RESOURCE) r.resources++ else r.behaviors++; return
            }
        }
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (f.isDirectory) scan(c, f, work, r, depth, f.name)
            else if (f.extension.lowercase() in setOf("mcpack", "mcaddon", "mcworld", "zip")) importZip(c, f, work, r, depth + 1)
        }
    }

    private fun packKind(dir: File): Kind = try {
        val mods = JSONObject(File(dir, "manifest.json").readText().trimStart('\uFEFF')).getJSONArray("modules")
        val types = (0 until mods.length()).map { mods.getJSONObject(it).optString("type") }
        if ("resources" in types) Kind.RESOURCE else Kind.BEHAVIOR
    } catch (_: Exception) { Kind.RESOURCE }

    private fun place(c: Context, k: Kind, src: File, name: String) {
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "imported" }
        var dest = File(dir(c, k), safe); var n = 1
        while (dest.exists()) dest = File(dir(c, k), "${safe}_${n++}")
        src.copyRecursively(dest, overwrite = false)
    }
}
