package com.example.bedrockclient

import android.content.Context
import org.json.JSONObject

/** Layout HUD bernama: posisi, ukuran, dan visibilitas semua widget HUD. Global (dipakai semua profil). */
object LayoutStore {
    private fun p(c: Context) = c.getSharedPreferences("feather_layouts", Context.MODE_PRIVATE)
    private fun hudModules() = ModuleRegistry.all.filter { it.hud && it.impl != Impl.UNSUPPORTED }

    fun names(c: Context): List<String> = p(c).all.keys.filter { it.startsWith("layout_") }.map { it.removePrefix("layout_") }.sorted()

    fun save(c: Context, name: String) {
        val root = JSONObject()
        hudModules().forEach { m ->
            val o = JSONObject()
            ModuleConfig.posFrac(c, m.id)?.let { o.put("fx", it.first.toDouble()).put("fy", it.second.toDouble()) }
            o.put("scale", ModuleConfig.getInt(c, m.id, "scale", 100))
            if (m.kind == CardKind.TOGGLE) o.put("on", ModuleConfig.isOn(c, m))
            root.put(m.id, o)
        }
        p(c).edit().putString("layout_$name", root.toString()).apply()
    }

    /** Return false jika layout tidak ada / rusak. Nilai divalidasi (clamp) sebelum diterapkan. */
    fun load(c: Context, name: String): Boolean {
        val root = try { JSONObject(p(c).getString("layout_$name", null) ?: return false) } catch (_: Exception) { return false }
        hudModules().forEach { m ->
            val o = root.optJSONObject(m.id) ?: return@forEach
            if (o.has("fx") && o.has("fy")) ModuleConfig.savePosFrac(c, m.id, o.getDouble("fx").toFloat(), o.getDouble("fy").toFloat())
            if (o.has("scale")) ModuleConfig.setInt(c, m.id, "scale", o.getInt("scale"))
            if (m.kind == CardKind.TOGGLE && o.has("on")) ModuleConfig.set(c, m, o.getBoolean("on"))
        }
        return true
    }

    fun delete(c: Context, name: String) = p(c).edit().remove("layout_$name").apply()

    fun resetToDefault(c: Context) {
        ModuleConfig.resetPositions(c)
        hudModules().forEach { m ->
            ModuleConfig.prefs(c).edit().remove("set_${m.id}_scale").apply()
            if (m.kind == CardKind.TOGGLE) ModuleConfig.set(c, m, m.defaultOn)
        }
    }
}
