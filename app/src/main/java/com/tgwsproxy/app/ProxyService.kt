package com.tgwsproxy.app
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class ProxyService : Service() {
    companion object {
        const val ACTION_STOP = "com.tgwsproxy.app.STOP"
        const val ACTION_RESTART = "com.tgwsproxy.app.RESTART"
        private const val CHANNEL = "proxy"
        private const val NOTIF_ID = 1
    }
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_RESTART -> { goForeground(); restartProxy(); return START_STICKY }
        }
        try {
            goForeground()
            ensureWakeLock()
            startProxy()
        } catch (e: Exception) {
            updateNotification("Ошибка запуска — откройте приложение")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun ensureWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tgwsproxy:run").apply { acquire() }
    }

    private fun startProxy() {
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        Python.getInstance().getModule("tgproxy_android").callAttr(
            "start", Config.secret(this), Config.PORT, Config.dcMode(this)
        )
    }

    private fun restartProxy() {
        Thread {
            try {
                if (Python.isStarted()) Python.getInstance().getModule("tgproxy_android").callAttr("stop")
                startProxy()
                ensureWakeLock()
                updateNotification("127.0.0.1:" + Config.PORT + " • " + routeLabel())
            } catch (e: Exception) {
                updateNotification("Ошибка перезапуска — откройте приложение")
            }
        }.start()
    }

    private fun routeLabel(): String = "DC2 + DC4"

    private fun notification(message: String) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("TG WS Proxy")
            .setContentText(message)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))
            .addAction(0, "Остановить", PendingIntent.getService(
                this, 1, Intent(this, ProxyService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))
            .setOngoing(true)
            .build()

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Прокси", NotificationManager.IMPORTANCE_LOW))
        }
        val n = notification("127.0.0.1:" + Config.PORT + " • " + routeLabel())
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }

    private fun updateNotification(message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification(message))
    }

    private fun shutdown() {
        Thread {
            try {
                if (Python.isStarted()) Python.getInstance().getModule("tgproxy_android").callAttr("stop")
            } catch (_: Exception) { }
            try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
            wakeLock = null
            stopForeground(true)
            stopSelf()
        }.start()
    }

    override fun onDestroy() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
        super.onDestroy()
    }
}
