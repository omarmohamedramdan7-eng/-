package com.example.core.util

import android.util.Log

/**
 * Security-hardened logger that redacts WireGuard private keys, preshared keys,
 * and raw 44-character Base64 Curve25519 keys before emitting logs.
 */
object SafeLogger {
    private const val TAG_PREFIX = "OmarVpn"

    private val keyAssignmentRegex = Regex(
        pattern = """(?i)(PrivateKey|PresharedKey)\s*=\s*([^\s\r\n]+)"""
    )
    private val base64KeyRegex = Regex(
        pattern = """[A-Za-z0-9+/]{42,43}="""
    )

    fun sanitize(message: String): String {
        var cleaned = keyAssignmentRegex.replace(message) { match ->
            "${match.groupValues[1]} = [REDACTED]"
        }
        cleaned = base64KeyRegex.replace(cleaned, "[KEY_REDACTED]")
        return cleaned
    }

    fun d(tag: String, message: String) {
        Log.d("$TAG_PREFIX:$tag", sanitize(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$TAG_PREFIX:$tag", sanitize(message), throwable)
        } else {
            Log.w("$TAG_PREFIX:$tag", sanitize(message))
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX:$tag", sanitize(message), throwable)
        } else {
            Log.e("$TAG_PREFIX:$tag", sanitize(message))
        }
    }
}
