package com.example.playback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.AppUpdateInfo

/**
 * Posts an optional, low-overhead native notification when a newer Davoo Player version is detected
 * on GitHub. Tapping the notification opens the GitHub release link directly in the external browser.
 */
object AppUpdateNotifier {

    const val UPDATE_CHANNEL_ID = "davoo_app_updates"
    private const val UPDATE_NOTIFICATION_ID = 20202

    fun notifyUpdateAvailable(context: Context, info: AppUpdateInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Actualizaciones de Davoo Player",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos opcionales cuando hay una nueva versión disponible en GitHub"
            }
            manager.createNotificationChannel(channel)
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, UPDATE_NOTIFICATION_ID, browserIntent, pendingFlags)

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle("Nueva versión disponible: v${info.latestVersion}")
            .setContentText("Pulsa para abrir GitHub en tu navegador y descargar la actualización")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Davoo Player v${info.latestVersion} ya está disponible en GitHub. Pulsa para abrir el enlace en tu navegador."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            manager.notify(UPDATE_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }
}
