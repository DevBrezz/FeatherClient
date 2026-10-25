package com.example.bedrockclient

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

object Ui {
    var dark = true
    private fun pick(d: String, l: String) = Color.parseColor(if (dark) d else l)
    val BG: Int get() = pick("#0B0D0F", "#F4F6F8")
    val CARD: Int get() = pick("#12161A", "#FFFFFF")
    val CARD2: Int get() = pick("#1B2026", "#EAEFF3")
    val BORDER: Int get() = pick("#252C33", "#D9DEE3")
    val MUTED: Int get() = pick("#8A94A0", "#5F6B76")
    val FG: Int get() = pick("#FFFFFF", "#11151A")
    val BTN_BG: Int get() = pick("#10201E", "#E6F4F2")
    fun applyTheme(c: Context) {
        dark = when (Prefs.themeMode(c)) {
            1 -> true; 2 -> false
            else -> (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun shape(c: Context, fill: Int, stroke: Int? = null, radius: Int = 14) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(c, radius).toFloat(); if (stroke != null) setStroke(dp(c, 1), stroke)
    }
    fun vbox(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    fun hbox(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun card(c: Context) = vbox(c).apply { background = shape(c, CARD, BORDER); setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14)) }
    fun text(c: Context, t: String, size: Float = 14f, color: Int = FG, bold: Boolean = false) = TextView(c).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    fun onAccent(a: Int) = if ((Color.red(a) * 299 + Color.green(a) * 587 + Color.blue(a) * 114) / 1000 > 180) Color.BLACK else Color.WHITE
    fun styleButton(t: TextView, filled: Boolean) {
        val acc = ModuleConfig.accentColor(); val c = t.context
        t.background = if (filled) shape(c, acc, null, 10) else shape(c, BTN_BG, acc, 10)
        t.setTextColor(if (filled) onAccent(acc) else acc)
    }
    fun button(c: Context, label: String, filled: Boolean, onClick: () -> Unit) = TextView(c).apply {
        text = label; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10)); styleButton(this, filled); setOnClickListener { onClick() }
    }
    fun row(c: Context, icon: String, label: String, trailing: String, onClick: () -> Unit): View = hbox(c).apply {
        setPadding(dp(c, 2), dp(c, 11), dp(c, 2), dp(c, 11))
        addView(text(c, icon, 18f), LinearLayout.LayoutParams(dp(c, 34), -2))
        addView(text(c, label, 16f), LinearLayout.LayoutParams(0, -2, 1f))
        addView(text(c, trailing, 15f, MUTED))
        setOnClickListener { onClick() }
    }
    fun section(c: Context, t: String) = text(c, t, 12f, MUTED, true).apply { setPadding(0, dp(c, 18), 0, dp(c, 8)) }
    fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()

    fun switchRow(c: Context, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = hbox(c).apply { background = shape(c, CARD, BORDER); setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10)) }
        val t = vbox(c); t.addView(text(c, title, 16f)); t.addView(text(c, desc, 12f, MUTED))
        row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(c).apply { isChecked = checked; setOnCheckedChangeListener { _, on -> onChange(on) } })
        return LinearLayout(c).apply { addView(row, LinearLayout.LayoutParams(-1, -2)); setPadding(0, 0, 0, dp(c, 8)) }
    }
    fun slider(c: Context, label: String, min: Int, max: Int, cur: Int, onChange: (Int) -> Unit): View {
        val box = vbox(c).apply { background = shape(c, CARD, BORDER); setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10)) }
        val v = text(c, "$label: $cur", 14f); box.addView(v)
        box.addView(SeekBar(c).apply {
            this.max = max - min; progress = cur - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { v.text = "$label: ${p + min}"; onChange(p + min) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        return LinearLayout(c).apply { addView(box, LinearLayout.LayoutParams(-1, -2)); setPadding(0, 0, 0, dp(c, 8)) }
    }
    fun prompt(c: Context, title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(c).apply { setText(initial); setSingleLine() }
        AlertDialog.Builder(c).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> et.text.toString().trim().takeIf { it.isNotEmpty() }?.let(ok) }
            .setNegativeButton("Batal", null).show()
    }
}
