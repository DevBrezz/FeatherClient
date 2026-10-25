package com.example.bedrockclient

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("feather_global", Context.MODE_PRIVATE)
    fun quickLaunch(c: Context) = p(c).getBoolean("quick", false)
    fun setQuickLaunch(c: Context, v: Boolean) = p(c).edit().putBoolean("quick", v).apply()
    fun profile(c: Context): String? = p(c).getString("profile", null)
    fun setProfile(c: Context, n: String?) = p(c).edit().apply { if (n == null) remove("profile") else putString("profile", n) }.apply()
    fun themeMode(c: Context) = p(c).getInt("theme", 0)          // 0 sistem, 1 gelap, 2 terang
    fun setThemeMode(c: Context, m: Int) = p(c).edit().putInt("theme", m).apply()
    fun notes(c: Context) = p(c).getString("notes", "") ?: ""
    fun setNotes(c: Context, t: String) = p(c).edit().putString("notes", t).apply()
    fun launchPending(c: Context) = p(c).getBoolean("pending", false)
    fun setLaunchPending(c: Context, v: Boolean) = p(c).edit().putBoolean("pending", v).commit()
    fun seenNotif(c: Context) = p(c).getInt("seen", 0)
    fun setSeenNotif(c: Context, h: Int) = p(c).edit().putInt("seen", h).apply()
}
