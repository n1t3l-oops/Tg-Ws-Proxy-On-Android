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
    private val white = Color.rgb(245, 248, 255)
    private val muted = Color.rgb(166, 180, 207)
    private val cyan = Color.rgb(143, 226, 255)
    private val mint = Color.rgb(143, 244, 213)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var power: TextView
    private lateinit var dot: TextView
    private lateinit var state: TextView
    private lateinit var detail: TextView
    private lateinit var stats: TextView
    private lateinit var logs: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var logToggle: TextView
    private var running = false
    private var showLogs = false

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1500)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glass(radius: Int = 22) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.argb(66, 211, 234, 255),
            Color.argb(25, 130, 157, 220),
            Color.argb(43, 102, 120, 185)
        )
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(65, 239, 248, 255))
    }

    private fun glassCircle(active: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (active) intArrayOf(Color.rgb(145, 239, 218), Color.rgb(72, 166, 173))
        else intArrayOf(Color.rgb(203, 250, 255), Color.rgb(112, 222, 226), Color.rgb(127, 192, 255))
    ).apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(2), Color.argb(150, 255, 255, 255))
    }

    private fun txt(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = glass()
        elevation = dp(7).toFloat()
        setPadding(dp(18), dp(16), dp(18), dp(16))
    }

    private fun heading(value: String) = txt(value.uppercase(), 11f, muted, true).apply {
        letterSpacing = 0.13f
        setPadding(dp(3), dp(22), dp(3), dp(10))
    }

    private fun action(title: String, subtitle: String, click: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glass(18)
            elevation = dp(4).toFloat()
            setPadding(dp(10), dp(15), dp(10), dp(15))
            isClickable = true
            isFocusable = true
            addView(txt(title, 14f, white, true).apply { gravity = Gravity.CENTER })
            addView(txt(subtitle, 11f, muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
            setOnClickListener { click() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = Color.rgb(19, 22, 43)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))

        val scroll = ScrollView(this).apply { setFillViewport(true) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(6, 11, 24), Color.rgb(13, 20, 42), Color.rgb(27, 22, 51))
            )
            setPadding(dp(22), dp(25), dp(22), dp(30))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(txt("TG WS Proxy", 26f, white, true))
        brand.addView(txt("Telegram connection tunnel", 12f, muted).apply {
            setPadding(0, dp(4), 0, 0)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(txt("LOCAL  •  1443", 10f, cyan, true).apply {
            background = glass(30)
            setPadding(dp(12), dp(9), dp(12), dp(9))
        })
        root.addView(header)

        val hero = panel().apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.argb(82, 150, 207, 255),
                    Color.argb(40, 105, 137, 215),
                    Color.argb(55, 105, 110, 184)
                )
            ).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), Color.argb(90, 245, 250, 255))
            }
            setPadding(dp(20), dp(20), dp(20), dp(22))
        }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dot = txt("●", 12f, muted)
        state = txt("Прокси выключен", 15f, white, true).apply {
            setPadding(dp(9), 0, 0, 0)
        }
        statusRow.addView(dot)
        statusRow.addView(state)
        hero.addView(statusRow)
        detail = txt("Запустите прокси, чтобы подключить Telegram", 12f, muted).apply {
            setPadding(0, dp(7), 0, 0)
        }
        hero.addView(detail)

        power = TextView(this).apply {
            text = "ЗАПУСТИТЬ"
            textSize = 13f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(8, 25, 38))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = glassCircle(false)
            elevation = dp(12).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { toggle() }
        }
        hero.addView(power, LinearLayout.LayoutParams(dp(152), dp(152)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(25)
            bottomMargin = dp(2)
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(25) })

        root.addView(heading("Подключение"))
        val connection = panel()
        connection.addView(txt("Адрес прокси", 12f, muted))
        connection.addView(txt("127.0.0.1:" + Config.PORT, 21f, white, true).apply {
            typeface = Typeface.create("sans-serif-monospace", Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(9))
        })
        connection.addView(View(this).apply {
            setBackgroundColor(Color.argb(65, 230, 240, 255))
        }, LinearLayout.LayoutParams(-1, dp(1)))
        connection.addView(txt("DC2 + DC4  •  исходная конфигурация", 11f, muted).apply {
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(connection)

        root.addView(heading("Быстрые действия"))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("Telegram", "Открыть подключение") { openTelegram() },
            LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        actions.addView(action("Скопировать", "Ссылку прокси") { copyLink() },
            LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)

        root.addView(heading("Сетевая статистика"))
        val statsCard = panel()
        stats = txt("Ожидание данных…", 11f, muted).apply {
            typeface = Typeface.MONOSPACE
        }
        statsCard.addView(stats)
        root.addView(statsCard)

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        logHeader.addView(heading("Журнал работы"), LinearLayout.LayoutParams(0, -2, 1f))
        logToggle = txt("ПОКАЗАТЬ", 10f, cyan, true).apply {
            setPadding(dp(10), dp(12), 0, dp(8))
            isClickable = true
            setOnClickListener {
                showLogs = !showLogs
                logScroll.visibility = if (showLogs) View.VISIBLE else View.GONE
                text = if (showLogs) "СКРЫТЬ" else "ПОКАЗАТЬ"
                refresh()
            }
        }
        logHeader.addView(logToggle)
        root.addView(logHeader)

        val logCard = panel()
        logs = txt("Журнал пока пуст.", 10f, muted).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        logScroll = ScrollView(this).apply {
            visibility = View.GONE
            addView(logs)
        }
        logCard.addView(logScroll, LinearLayout.LayoutParams(-1, dp(220)))
        root.addView(logCard)

        root.addView(txt(
            "Если подключение зависает, откройте журнал и отправьте последние строки для диагностики.",
            11f, muted
        ).apply { setPadding(dp(3), dp(18), dp(3), 0) })

        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun refresh() {
        try {
            val module = Python.getInstance().getModule("tgproxy_android")
            running = module.callAttr("is_running").toBoolean()
            val error = module.callAttr("last_error").toString()
            dot.setTextColor(if (running) mint else Color.rgb(255, 190, 130))
            state.text = when {
                running -> "Прокси активен"
                error.isNotEmpty() -> "Ошибка запуска"
                else -> "Прокси выключен"
            }
            detail.text = when {
                running -> "127.0.0.1:" + Config.PORT + "  •  локальное соединение"
                error.isNotEmpty() -> error
                else -> "Запустите прокси, чтобы подключить Telegram"
            }
            power.text = if (running) "ОСТАНОВИТЬ" else "ЗАПУСТИТЬ"
            power.background = glassCircle(running)
            stats.text = try {
                module.callAttr("get_stats").toString()
            } catch (_: Exception) {
                "Статистика пока недоступна"
            }
            if (showLogs) {
                val content = module.callAttr("get_log").toString()
                if (logs.text.toString() != content) {
                    logs.text = if (content.isBlank()) "Журнал пока пуст." else content
                    logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
                }
            }
        } catch (e: Exception) {
            state.text = "Ожидание ядра прокси"
            detail.text = e.message ?: "Python runtime недоступен"
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

    private fun openTelegram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.link(this))))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Telegram не найден", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("tg proxy", Config.link(this)))
        Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
    }
}
