package com.tgwsproxy.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var circle: TextView
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private var running = false
    private var logVisible = false

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, radius: Int = 0, oval: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(radius).toFloat()
    }

    private fun link(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#7FB2FF"))
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#101114"))
            setPadding(dp(24), dp(64), dp(24), dp(24))
        }

        val title = TextView(this).apply {
            text = "TG WS Proxy"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(title)

        circle = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { toggle() }
        }
        root.addView(circle, LinearLayout.LayoutParams(dp(170), dp(170)).apply {
            topMargin = dp(56)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#8A8F9C"))
            textSize = 15f
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        actions.addView(link("Открыть в Telegram") { openInTelegram() })
        actions.addView(link("Скопировать ссылку") { copyLink() })
        actions.addView(link("Показать лог") {
            logVisible = !logVisible
            logScroll.visibility = if (logVisible) View.VISIBLE else View.GONE
            refresh()
        })
        root.addView(actions, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        logView = TextView(this).apply {
            setTextColor(Color.parseColor("#B8BDC9"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        logScroll = ScrollView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#181A20"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(logView)
        }
        root.addView(logScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(8) })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun py() = Python.getInstance().getModule("tgproxy_android")

    private fun refresh() {
        val m = py()
        running = m.callAttr("is_running").toBoolean()
        val err = m.callAttr("last_error").toString()

        circle.text = if (running) "Остановить" else "Запустить"
        circle.background = bg(Color.parseColor(if (running) "#2E9E5B" else "#2A2D35"), oval = true)
        status.text = when {
            running -> "Работает\n127.0.0.1:${Config.PORT}"
            err.isNotEmpty() -> "Ошибка: $err"
            else -> "Выключено"
        }
        if (logVisible) {
            val text = m.callAttr("get_log").toString()
            if (logView.text.toString() != text) {
                logView.text = text
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun toggle() {
        if (running) {
            startService(Intent(this, ProxyService::class.java).setAction(ProxyService.ACTION_STOP))
        } else {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            ContextCompat.startForegroundService(this, Intent(this, ProxyService::class.java))
        }
    }

    private fun openInTelegram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.link(this))))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Telegram не найден", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLink() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tg proxy", Config.link(this)))
        Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
    }
}
