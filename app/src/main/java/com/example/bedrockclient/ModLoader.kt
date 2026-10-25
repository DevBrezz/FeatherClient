package com.example.bedrockclient

import android.content.Context
import android.content.Intent

/**
 * Launches the installed, official Minecraft app directly. Declaring Minecraft's Activity
 * inside Feather's package/classloader can pass static checks but crash when MC initializes
 * its own resources and package context.
 */
object ModLoader {
    fun launch(ctx: Context): String? {
        MinecraftGuard.invalidate()
        val status = MinecraftGuard.status(ctx, force = true)
        if (status !is MinecraftStatus.Compatible) return MinecraftGuard.describe(status)

        val intent = try {
            ctx.packageManager.getLaunchIntentForPackage(MinecraftGuard.MC_PACKAGE)
        } catch (t: Throwable) {
            return "Tidak bisa membuka Minecraft: ${t.javaClass.simpleName}: ${t.message}"
        } ?: return "Minecraft terdeteksi, tetapi launcher resminya tidak ditemukan. Buka Minecraft sekali dari ikon aplikasinya."

        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            null
        } catch (t: Throwable) {
            "Gagal membuka Minecraft: ${t.javaClass.simpleName}: ${t.message}"
        }
    }
}
