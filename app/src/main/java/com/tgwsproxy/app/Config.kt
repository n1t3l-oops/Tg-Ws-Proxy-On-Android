package com.tgwsproxy.app

import android.content.Context
import java.security.SecureRandom

object Config {
    const val PORT = 1443

    fun secret(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        var s = prefs.getString("secret", null)
        if (s == null || s.length != 32) {
            val b = ByteArray(16)
            SecureRandom().nextBytes(b)
            s = b.joinToString("") { "%02x".format(it) }
            prefs.edit().putString("secret", s).apply()
        }
        return s
    }

    fun link(ctx: Context): String =
        "tg://proxy?server=127.0.0.1&port=$PORT&secret=dd${secret(ctx)}"
}
