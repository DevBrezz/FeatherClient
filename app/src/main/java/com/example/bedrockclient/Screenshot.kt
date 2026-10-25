package com.example.bedrockclient

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/** Screenshot window game via PixelCopy (API 26+). Hasil: Pictures/FeatherClient. */
object Screenshot {
    fun take(a: Activity) {
        val w = a.window; val v = w.decorView
        if (v.width == 0 || v.height == 0) return
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(w, bmp, { r ->
                val msg = if (r == PixelCopy.SUCCESS) try { save(a, bmp) } catch (e: Exception) { "Gagal simpan: ${e.message}" } else "Screenshot gagal (kode $r)"
                a.runOnUiThread { Toast.makeText(a, msg, Toast.LENGTH_LONG).show() }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) { Toast.makeText(a, "Screenshot gagal: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun save(a: Activity, bmp: Bitmap): String {
        val name = "Feather_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FeatherClient")
            }
            val uri = a.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: throw IOException("insert gagal")
            a.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return "Tersimpan: Pictures/FeatherClient/$name"
        }
        val dir = File(a.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FeatherClient").apply { mkdirs() }
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "Tersimpan: ${dir.absolutePath}/$name"
    }
}
