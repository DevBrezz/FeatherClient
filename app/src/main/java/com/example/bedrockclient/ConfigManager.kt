package com.example.bedrockclient

import android.content.Context
import org.json.JSONObject

/** Ekspor/impor konfigurasi profil aktif sebagai JSON. Impor memvalidasi setiap entri (tipe, id, rentang). */
object ConfigManager {
    private val FIXED = setOf("opacity", "scale", "accent", "hud_hidden")

    fun export(c: Context): String {
        val prefs = JSONObject()
        ModuleConfig.prefs(c).all.forEach { (k, v) ->
            val ok = k.startsWith("mod_") || k.startsWith("pin_") || k.startsWith("set_") || k.startsWith("fx_") || k.startsWith("fy_") || k in FIXED
            if (ok && v != null) prefs.put(k, v)
        }
        return JSONObject().put("feather", 1).put("version", ModuleConfig.VERSION).put("profile", InstanceStore.name(c, InstanceStore.current(c))).put("prefs", prefs).toString(2)
    }

    /** Return ringkasan hasil. Melempar IllegalArgumentException jika format tidak valid. */
    fun import(c: Context, text: String): String {
        val root = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("Bukan JSON yang valid") }
        if (root.optInt("feather", 0) != 1) throw IllegalArgumentException("Bukan konfigurasi Feather")
        val prefs = root.optJSONObject("prefs") ?: throw IllegalArgumentException("Data konfigurasi kosong")
        val ed = ModuleConfig.prefs(c).edit()
        var applied = 0; var skipped = 0
        for (k in prefs.keys()) {
            val v = prefs.get(k)
            val ok = when {
                k.startsWith("mod_") -> v is Boolean && ModuleRegistry.find(k.removePrefix("mod_"))?.let { it.impl != Impl.UNSUPPORTED && it.kind == CardKind.TOGGLE }?.also { if (it) ed.putBoolean(k, v as Boolean) } == true
                k.startsWith("pin_") -> v is Boolean && ModuleRegistry.find(k.removePrefix("pin_")) != null && run { ed.putBoolean(k, v as Boolean); true }
                k.startsWith("set_") -> v is Number && setting(k)?.let { ed.putInt(k, (v as Number).toInt().coerceIn(it.min, it.max)); true } == true
                k.startsWith("fx_") || k.startsWith("fy_") -> v is Number && ModuleRegistry.find(k.drop(3))?.hud == true && run { ed.putFloat(k, (v as Number).toFloat().coerceIn(0f, 1f)); true }
                k == "opacity" -> v is Number && run { ed.putInt(k, (v as Number).toInt().coerceIn(0, 100)); true }
                k == "scale" -> v is Number && run { ed.putInt(k, (v as Number).toInt().coerceIn(70, 160)); true }
                k == "accent" -> v is Number && run { ed.putInt(k, (v as Number).toInt().coerceIn(0, ModuleConfig.accents.size - 1)); true }
                k == "hud_hidden" -> v is Boolean && run { ed.putBoolean(k, v as Boolean); true }
                else -> false
            }
            if (ok) applied++ else skipped++
        }
        ed.apply()
        ModuleConfig.load(c); ModuleConfig.syncNative(c)
        return "Diterapkan $applied entri" + if (skipped > 0) ", dilewati $skipped (tidak valid/tidak didukung)" else ""
    }

    /** "set_<modId>_<key>" -> Setting. modId tidak memuat '_' sehingga pemisah pertama adalah batas id. */
    private fun setting(k: String): Setting? {
        val rest = k.removePrefix("set_"); val i = rest.indexOf('_'); if (i <= 0) return null
        return ModuleRegistry.find(rest.substring(0, i))?.settings?.firstOrNull { it.key == rest.substring(i + 1) }
    }
}
