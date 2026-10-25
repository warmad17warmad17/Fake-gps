package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.FakeGpsApp
import com.example.MainActivity
import com.example.R
import com.example.service.MockLocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FavoritesWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_START_MOCK = "com.example.widget.ACTION_START_MOCK"
        const val ACTION_STOP_MOCK = "com.example.widget.ACTION_STOP_MOCK"
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"

        const val EXTRA_FAVORITE_ID = "extra_favorite_id"
        const val EXTRA_FAVORITE_NAME = "extra_favorite_name"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"

        const val PREFS_NAME = "fake_gps_widget_prefs"
        const val KEY_ACTIVE_ID = "active_id"
        const val KEY_ACTIVE_NAME = "active_name"
        const val KEY_ACTIVE_LAT = "active_lat"
        const val KEY_ACTIVE_LNG = "active_lng"
        const val KEY_IS_MOCKING = "is_mocking"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, FavoritesWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, FavoritesWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isMocking = prefs.getBoolean(KEY_IS_MOCKING, false)
        val activeName = prefs.getString(KEY_ACTIVE_NAME, "") ?: ""

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_fake_gps)

            // 1. Header texts
            views.setTextViewText(R.id.widget_title, "Fake GPS")
            val subtitleText = if (isMocking && activeName.isNotBlank()) {
                "Selected Location: $activeName"
            } else if (isMocking) {
                "Selected Location: Aktif"
            } else {
                "Selected Location: Tidak aktif"
            }
            views.setTextViewText(R.id.widget_subtitle, subtitleText)

            // 2. Open App when tapping header
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 10,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent)

            // 3. Top-right action icon
            if (isMocking) {
                val stopIntent = Intent(context, FavoritesWidgetProvider::class.java).apply {
                    action = ACTION_STOP_MOCK
                }
                val stopPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 1,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_header_action, stopPendingIntent)
                views.setImageViewResource(R.id.widget_header_action, R.drawable.ic_widget_stop_red)
            } else {
                views.setOnClickPendingIntent(R.id.widget_header_action, openAppPendingIntent)
                views.setImageViewResource(R.id.widget_header_action, R.drawable.ic_widget_target)
            }

            // 4. Remote adapter for list
            val serviceIntent = Intent(context, FavoritesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("fakegps://widget/id/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
            views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view)

            // 5. Template pending intent for list items
            val itemTemplateIntent = Intent(context, FavoritesWidgetProvider::class.java)
            val itemTemplatePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                itemTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list_view, itemTemplatePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_START_MOCK -> {
                val favId = intent.getLongExtra(EXTRA_FAVORITE_ID, -1L)
                val favName = intent.getStringExtra(EXTRA_FAVORITE_NAME) ?: "Lokasi Favorit"
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(KEY_IS_MOCKING, true)
                    .putLong(KEY_ACTIVE_ID, favId)
                    .putString(KEY_ACTIVE_NAME, favName)
                    .putString(KEY_ACTIVE_LAT, lat.toString())
                    .putString(KEY_ACTIVE_LNG, lng.toString())
                    .apply()

                MockLocationService.start(context, lat, lng, favName, favId)

                val app = context.applicationContext as? FakeGpsApp
                app?.let {
                    CoroutineScope(Dispatchers.IO).launch {
                        it.repository.addHistory(lat, lng, favName)
                    }
                }

                refreshAll(context)
            }
            ACTION_STOP_MOCK -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(KEY_IS_MOCKING, false)
                    .putLong(KEY_ACTIVE_ID, -1L)
                    .putString(KEY_ACTIVE_NAME, "")
                    .apply()

                MockLocationService.stop(context)

                refreshAll(context)
            }
            ACTION_REFRESH_WIDGET -> {
                refreshAll(context)
            }
        }
    }

    private fun refreshAll(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, FavoritesWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
            onUpdate(context, appWidgetManager, appWidgetIds)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
        }
    }
}
