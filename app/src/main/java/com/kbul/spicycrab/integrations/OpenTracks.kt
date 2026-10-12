package com.kbul.spicycrab.integrations

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts/stops GPS recording in OpenTracks through its public API (explicit activity intents).
 * The user must enable "Public API" in OpenTracks settings; OpenTracks rejects the call otherwise.
 */
@Singleton
class OpenTracks @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun startRecording() = send("$API_PACKAGE.StartRecording") {
        putExtra("TRACK_NAME", "SoloForge workout")
        putExtra("TRACK_CATEGORY", "Workout")
    }

    fun stopRecording() = send("$API_PACKAGE.StopRecording") {}

    private fun send(activityClass: String, extras: Intent.() -> Unit) {
        val pkg = installedPackage() ?: run {
            Toast.makeText(context, "Install OpenTracks to record your route", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent().setClassName(pkg, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply(extras)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "OpenTracks couldn't be started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installedPackage(): String? = PACKAGES.firstOrNull { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private companion object {
        const val API_PACKAGE = "de.dennisguse.opentracks.publicapi"
        val PACKAGES = listOf(
            "de.dennisguse.opentracks",
            "de.dennisguse.opentracks.playStore",
            "de.dennisguse.opentracks.nightly",
        )
    }
}
