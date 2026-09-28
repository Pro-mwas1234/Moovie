package com.Moovie.app.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Launches Android's package installer for a downloaded APK file. */
object ApkInstaller {

    /**
     * Returns true when the installer screen actually launched. Callers use this
     * to tell "install blocked" apart from "download failed" instead of blaming
     * the connection for both.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() <= 0L) return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )

        // Android 8+ needs the user to grant "install unknown apps" for this
        // app first; without it every install intent is silently dropped.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            openInstallPermissionSettings(context)
            // The user comes back and taps "Install now" again.
            return false
        }

        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (tryStart(context, installIntent)) return true

        // Some Android 14 builds drop ACTION_INSTALL_PACKAGE from background
        // contexts; ACTION_VIEW with the APK MIME is the documented fallback.
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return tryStart(context, viewIntent)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }
}
