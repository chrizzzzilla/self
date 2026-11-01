package com.chrizzzzilla.self.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Einfaches Absturz-Protokoll – nur lokal auf dem Geraet, wird nirgendwohin gesendet.
 * Beim naechsten Start zeigt die App es an, damit man es kopieren und weitergeben kann.
 */
object CrashLog {
    private fun file(context: Context) = File(context.applicationContext.filesDir, "absturz.txt")

    fun write(context: Context, e: Throwable) {
        runCatching {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val text = buildString {
                appendLine("Self ${versionName(context)} · Android ${Build.VERSION.RELEASE} (${Build.MODEL}) · ${LocalDateTime.now()}")
                appendLine()
                append(sw.toString().take(12_000))
            }
            file(context).writeText(text)
        }
    }

    fun read(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** Faengt Abstuerze ab, schreibt sie ins Protokoll und uebergibt dann an Android. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            write(app, e)
            previous?.uncaughtException(t, e)
        }
    }

    private fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")
}
