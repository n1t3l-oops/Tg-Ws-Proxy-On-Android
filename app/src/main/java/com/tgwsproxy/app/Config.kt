package com.tgwsproxy.app
import android.content.Context
import java.security.SecureRandom
object Config {
    const val PORT = 1443
    private const val PREFS = "cfg"
    private const val KEY_SECRET = "secret"
    private const val KEY_DC_MODE = "dc_mode"
    fun secret(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var s = prefs.getString(KEY_SECRET, null)
        if (s == null || s.length != 32) {
            val b = ByteArray(16)
            s = b.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_SECRET, s).apply()
        }
        return s
    }
    fun dcMode(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DC_MODE, "dc4") ?: "dc4"
    fun setDcMode(ctx: Context, mode: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DC_MODE, if (mode == "auto") "auto" else "dc4").apply()
    }
    fun link(ctx: Context): String =
        "tg://proxy?server=127.0.0.1&port=" + PORT + "&secret=dd" + secret(ctx)
}
