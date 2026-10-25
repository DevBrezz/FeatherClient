package com.example.bedrockclient

import android.content.Context
import android.graphics.Color

object ModuleConfig {
    const val CLIENT_NAME = "Feather Client"
    const val AUTHOR = "KingBrezz"
    const val VERSION = "1.7"

    /** Alias ke registry terpusat. */
    val modules: List<ModuleInfo> get() = ModuleRegistry.all
    val accents = listOf("#26A69A", "#4FC3F7", "#81C784", "#FFB74D", "#F06292", "#B388FF", "#FFFFFF")

    private val settingIndex: Map<String, Setting> by lazy { ModuleRegistry.all.flatMap { m -> m.settings.map { "${m.id}.${it.key}" to it } }.toMap() }

    private fun p(c: Context) = c.getSharedPreferences("feather_" + InstanceStore.current(c), Context.MODE_PRIVATE)
    fun prefs(c: Context) = p(c)

    fun isOn(c: Context, m: ModuleInfo) = p(c).getBoolean("mod_" + m.id, m.defaultOn)
    fun isOn(c: Context, id: String) = ModuleRegistry.find(id)?.let { isOn(c, it) } ?: false
    fun set(c: Context, m: ModuleInfo, on: Boolean) = p(c).edit().putBoolean("mod_" + m.id, on).apply()

    fun isPinned(c: Context, id: String) = p(c).getBoolean("pin_$id", false)
    fun setPinned(c: Context, id: String, v: Boolean) = p(c).edit().putBoolean("pin_$id", v).apply()

    /** Nilai setting selalu divalidasi ke rentang yang didefinisikan registry. */
    fun getInt(c: Context, modId: String, key: String, def: Int): Int {
        val s = settingIndex["$modId.$key"]
        val v = p(c).getInt("set_${modId}_$key", s?.def ?: def)
        return if (s != null) v.coerceIn(s.min, s.max) else v
    }
    fun setInt(c: Context, modId: String, key: String, v: Int) {
        val s = settingIndex["$modId.$key"]
        p(c).edit().putInt("set_${modId}_$key", if (s != null) v.coerceIn(s.min, s.max) else v).apply()
    }
    fun getBool(c: Context, modId: String, key: String) = getInt(c, modId, key, 0) == 1
    fun resetSettings(c: Context, m: ModuleInfo) = p(c).edit().apply { m.settings.forEach { remove("set_${m.id}_${it.key}") } }.apply()
    fun colorFor(c: Context, modId: String, key: String, fallback: Int): Int {
        val i = getInt(c, modId, key, 0)
        return if (i <= 0 || i >= Palette.hex.size) fallback else Color.parseColor(Palette.hex[i])
    }

    fun hudHidden(c: Context) = p(c).getBoolean("hud_hidden", false)
    fun setHudHidden(c: Context, v: Boolean) = p(c).edit().putBoolean("hud_hidden", v).apply()

    var opacity: Int = 50; private set
    var scale: Int = 100; private set
    var accentIdx: Int = 0; private set
    fun load(c: Context) { opacity = p(c).getInt("opacity", 50).coerceIn(0, 100); scale = p(c).getInt("scale", 100).coerceIn(70, 160); accentIdx = p(c).getInt("accent", 0).coerceIn(0, accents.size - 1) }
    fun setOpacity(c: Context, v: Int) { opacity = v.coerceIn(0, 100); p(c).edit().putInt("opacity", opacity).apply() }
    fun setScale(c: Context, v: Int) { scale = v.coerceIn(70, 160); p(c).edit().putInt("scale", scale).apply() }
    fun setAccent(c: Context, i: Int) { accentIdx = i.coerceIn(0, accents.size - 1); p(c).edit().putInt("accent", accentIdx).apply() }
    fun accentColor() = Color.parseColor(accents[accentIdx])

    // Posisi widget disimpan sebagai pecahan (0..1) dari area aman -> tetap valid saat rotasi/ukuran layar berubah.
    fun posFrac(c: Context, id: String): Pair<Float, Float>? {
        val sp = p(c)
        return if (sp.contains("fx_$id") && sp.contains("fy_$id")) sp.getFloat("fx_$id", 0f).coerceIn(0f, 1f) to sp.getFloat("fy_$id", 0f).coerceIn(0f, 1f) else null
    }
    fun savePosFrac(c: Context, id: String, fx: Float, fy: Float) = p(c).edit().putFloat("fx_$id", fx.coerceIn(0f, 1f)).putFloat("fy_$id", fy.coerceIn(0f, 1f)).apply()
    fun resetPositions(c: Context) = p(c).edit().apply { p(c).all.keys.filter { it.startsWith("fx_") || it.startsWith("fy_") || it.startsWith("x_") || it.startsWith("y_") }.forEach { remove(it) } }.apply()
    fun resetAll(c: Context) { p(c).edit().clear().apply(); load(c) }

    fun serialize(c: Context) = modules.filter { it.kind == CardKind.TOGGLE && it.impl != Impl.UNSUPPORTED }.joinToString(";") { "${it.id}=${if (isOn(c, it)) 1 else 0}" }

    /** Sinkronkan toggle & level zoom ke native (aman dipanggil kapan saja). */
    fun syncNative(c: Context) {
        modules.filter { it.kind == CardKind.TOGGLE && it.impl != Impl.UNSUPPORTED }.forEach { try { NativeBridge.setModule(it.id, isOn(c, it)) } catch (_: Throwable) {} }
        try { NativeBridge.setZoomLevel(getInt(c, "zoom", "level", 25) / 100f) } catch (_: Throwable) {}
    }
}
