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
    private val page = Color.rgb(8, 13, 27)
    private val card = Color.argb(42, 190, 220, 255)
    private val alt = Color.argb(32, 160, 190, 245)
    private val accent = Color.rgb(135, 226, 238)
    private val muted = Color.rgb(166, 180, 207)
    private val white = Color.rgb(245, 248, 255)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var power: TextView
    private lateinit var dot: TextView
    private lateinit var state: TextView
    private lateinit var detail: TextView
    private lateinit var dc4: TextView
    private lateinit var auto: TextView
    private lateinit var routeInfo: TextView
    private lateinit var stats: TextView
    private lateinit var logs: TextView
    private lateinit var logScroll: ScrollView
    private var running = false
    private var showLogs = false

    private val ticker = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1500) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color)
    }
    private fun txt(s: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(card)
        elevation = dp(7).toFloat()
        setPadding(dp(18), dp(16), dp(18), dp(16))
    }
    private fun heading(s: String) = txt(s, 12f, muted, true).apply {
        letterSpacing = 0.08f; setPadding(dp(2), dp(22), dp(2), dp(10))
    }
    private fun action(title: String, sub: String, click: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = rounded(alt, 16); setPadding(dp(8), dp(14), dp(8), dp(14))
            isClickable = true; isFocusable = true
            addView(txt(title, 14f, white, true).apply { gravity = Gravity.CENTER })
            addView(txt(sub, 11f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
            setOnClickListener { click() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))

        val scroll = ScrollView(this).apply { setFillViewport(true) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(page)
            setPadding(dp(22), dp(24), dp(22), dp(28))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(txt("TG WS Proxy", 25f, white, true))
        brand.addView(txt("Локальный прокси для Telegram", 12f, muted).apply { setPadding(0, dp(3), 0, 0) })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(txt("ANDROID", 10f, accent, true).apply {
            background = rounded(Color.rgb(23, 53, 49), 30)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        })
        root.addView(header)

        val hero = panel().apply { setPadding(dp(20), dp(20), dp(20), dp(20)) }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        dot = txt("●", 13f, muted)
        state = txt("Прокси выключен", 15f, white, true).apply { setPadding(dp(9), 0, 0, 0) }
        statusRow.addView(dot); statusRow.addView(state); hero.addView(statusRow)
        detail = txt("Запустите прокси, чтобы подключить Telegram", 12f, muted).apply {
            setPadding(0, dp(7), 0, 0)
        }
        hero.addView(detail)
        power = TextView(this).apply {
            text = "ЗАПУСТИТЬ"; textSize = 14f; letterSpacing = 0.08f
            setTextColor(Color.rgb(8, 28, 24)); typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER; background = circle(accent); isClickable = true; isFocusable = true
            setOnClickListener { toggle() }
        }
        hero.addView(power, LinearLayout.LayoutParams(dp(148), dp(148)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(24); bottomMargin = dp(4)
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })

        root.addView(heading("ПОДКЛЮЧЕНИЕ"))
        val connection = panel()
        connection.addView(txt("Локальный адрес", 12f, muted))
        connection.addView(txt("127.0.0.1:" + Config.PORT, 18f, white, true).apply {
            typeface = Typeface.create("sans-serif-monospace", Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(12))
        })
        connection.addView(View(this).apply { setBackgroundColor(Color.rgb(42, 49, 61)) },
            LinearLayout.LayoutParams(-1, dp(1)))
        connection.addView(txt("Прокси работает только на этом устройстве", 11f, muted).apply {
            setPadding(0, dp(11), 0, 0)
        })
        root.addView(connection)

        connection.addView(label("Маршрут узлов", 12f, muted).apply { setPadding(0, dp(10), 0, dp(3)) })
        connection.addView(label("DC2 + DC4  •  исходная конфигурация", 13f, white, true))
        root.addView(heading("БЫСТРЫЕ ДЕЙСТВИЯ"))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("Telegram", "Открыть прокси") { openTelegram() },
            LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        actions.addView(action("Ссылка", "Копировать") { copyLink() },
            LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)

        root.addView(heading("СЕТЕВАЯ СТАТИСТИКА"))
        val statPanel = panel()
        stats = txt("Ожидание данных…", 11f, muted).apply { typeface = Typeface.MONOSPACE }
        statPanel.addView(stats); root.addView(statPanel)

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        logHeader.addView(heading("ЖУРНАЛ РАБОТЫ"), LinearLayout.LayoutParams(0, -2, 1f))
        val logToggle = txt("ПОКАЗАТЬ", 10f, accent, true).apply {
            setPadding(dp(10), dp(12), 0, dp(8)); isClickable = true
            setOnClickListener {
                showLogs = !showLogs
                logScroll.visibility = if (showLogs) View.VISIBLE else View.GONE
                text = if (showLogs) "СКРЫТЬ" else "ПОКАЗАТЬ"
                refresh()
            }
        }
        logHeader.addView(logToggle); root.addView(logHeader)
        val logPanel = panel()
        logs = txt("Лог пока пуст.", 10f, muted).apply {
            typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
        }
        logScroll = ScrollView(this).apply { visibility = View.GONE; addView(logs) }
        logPanel.addView(logScroll, LinearLayout.LayoutParams(-1, dp(210)))
        root.addView(logPanel)
        root.addView(txt(
            "Сравнивайте режимы в одной сети. Скорость также зависит от маршрута провайдера и доступности узлов Telegram.",
            11f, muted
        ).apply { setPadding(dp(2), dp(18), dp(2), 0) })

        scroll.addView(root); setContentView(scroll); updateRouteUi()
    }

    private fun toggle() {
        if (running) startService(Intent(this, ProxyService::class.java).setAction(ProxyService.ACTION_STOP))
        else {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            ContextCompat.startForegroundService(this, Intent(this, ProxyService::class.java))
        }
    }
    private fun openTelegram() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.link(this)))) }
        catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Telegram не найден", Toast.LENGTH_SHORT).show()
        }
    }
    private fun copyLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("tg proxy", Config.link(this)))
        Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
    }
}
