package com.example.bedrockclient

import android.content.Context

/** Instance = profil terpisah (modul + tema HUD). Konten (world/pack) dipakai bersama. */
object InstanceStore {
    private fun p(c: Context) = c.getSharedPreferences("feather_instances", Context.MODE_PRIVATE)
    fun ids(c: Context): List<String> {
        val s = p(c).getString("ids", null)
        if (s.isNullOrEmpty()) {
            p(c).edit().putString("ids", "default").putString("name_default", "Minecraft").putString("current", "default").apply()
            return listOf("default")
        }
        return s.split(",")
    }
    fun current(c: Context): String { val l = ids(c); return p(c).getString("current", null)?.takeIf { it in l } ?: l.first() }
    fun name(c: Context, id: String) = p(c).getString("name_$id", id) ?: id
    fun setCurrent(c: Context, id: String) = p(c).edit().putString("current", id).apply()
    fun create(c: Context, name: String): String {
        val id = "i" + System.currentTimeMillis()
        p(c).edit().putString("ids", (ids(c) + id).joinToString(",")).putString("name_$id", name).apply(); return id
    }
    fun rename(c: Context, id: String, name: String) = p(c).edit().putString("name_$id", name).apply()
    fun delete(c: Context, id: String): Boolean {
        val l = ids(c); if (l.size <= 1) return false
        val cur = current(c)
        p(c).edit().putString("ids", (l - id).joinToString(",")).remove("name_$id").apply()
        c.deleteSharedPreferences("feather_$id")
        if (cur == id) setCurrent(c, (l - id).first())
        return true
    }
}
