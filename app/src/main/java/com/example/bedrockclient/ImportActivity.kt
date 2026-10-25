package com.example.bedrockclient

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** Dipanggil saat user membuka .mcworld/.mcpack/.mcaddon dari browser / file manager. */
class ImportActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val uri = intent?.data
        if (uri == null) { finish(); return }
        Toast.makeText(this, "Mengimpor ke Feather Client…", Toast.LENGTH_SHORT).show()
        Thread {
            val r = ContentManager.importUri(applicationContext, uri)
            runOnUiThread { Toast.makeText(this, r.summary(), Toast.LENGTH_LONG).show(); finish() }
        }.start()
    }
}
