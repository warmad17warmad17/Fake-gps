package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

object WidgetHelper {

    fun isPinWidgetSupported(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
            appWidgetManager?.isRequestPinAppWidgetSupported == true
        } else {
            false
        }
    }

    fun pinWidgetToHomeScreen(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
            val provider = ComponentName(context, FavoritesWidgetProvider::class.java)
            if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    99,
                    Intent(context, FavoritesWidgetProvider::class.java).apply {
                        action = FavoritesWidgetProvider.ACTION_REFRESH_WIDGET
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                Toast.makeText(
                    context,
                    "Silakan konfirmasi untuk memasang Widget My Maps ke Layar Utama",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Tekan lama di layar utama HP Anda, pilih menu 'Widget' lalu cari 'My Maps Favorit'.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(
                context,
                "Tekan lama di layar utama HP Anda, pilih menu 'Widget' lalu cari 'My Maps Favorit'.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
