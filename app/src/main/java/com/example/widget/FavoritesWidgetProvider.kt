package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.example.FakeGpsApp
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
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
            try {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isMocking = prefs.getBoolean(KEY_IS_MOCKING, false)
        val activeName = prefs.getString(KEY_ACTIVE_NAME, "") ?: ""
        val activeId = prefs.getLong(KEY_ACTIVE_ID, -1L)
        val activeLatStr = prefs.getString(KEY_ACTIVE_LAT, null)
        val activeLngStr = prefs.getString(KEY_ACTIVE_LNG, null)

        val favoritesList = try {
            AppDatabase.getDatabase(context).locationDao().getAllFavoritesList()
        } catch (e: Exception) {
            emptyList()
        }

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_fake_gps)

            // 1. Header texts
            views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
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

            // 3. Top-right action icon (set explicitly in code)
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

            // 4. Set PendingIntent template for list items
            val itemTemplateIntent = Intent(context, FavoritesWidgetProvider::class.java).apply {
                action = ACTION_START_MOCK
            }
            val itemTemplatePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                itemTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list_view, itemTemplatePendingIntent)

            // 5. Populate Collection (RemoteCollectionItems on Android 12+, RemoteViewsService on Android < 12)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val itemsBuilder = RemoteViews.RemoteCollectionItems.Builder()
                if (favoritesList.isEmpty()) {
                    views.setViewVisibility(R.id.widget_list_view, View.GONE)
                    views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_list_view, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_empty_view, View.GONE)

                    for (fav in favoritesList) {
                        val itemViews = RemoteViews(context.packageName, R.layout.widget_favorite_item)
                        itemViews.setTextViewText(R.id.item_name, fav.name)

                        val isActive = isMocking && (activeId == fav.id || (
                            activeLatStr?.toDoubleOrNull() == fav.latitude && activeLngStr?.toDoubleOrNull() == fav.longitude
                        ))

                        if (isActive) {
                            itemViews.setImageViewResource(R.id.item_action_button, R.drawable.ic_widget_stop_red)
                            itemViews.setTextColor(R.id.item_name, 0xFF1E88E5.toInt())
                            val stopFillIn = Intent().apply {
                                action = ACTION_STOP_MOCK
                            }
                            itemViews.setOnClickFillInIntent(R.id.item_action_button, stopFillIn)
                            itemViews.setOnClickFillInIntent(R.id.item_row_root, stopFillIn)
                        } else {
                            itemViews.setImageViewResource(R.id.item_action_button, R.drawable.ic_widget_target_blue)
                            itemViews.setTextColor(R.id.item_name, 0xFF212121.toInt())
                            val startFillIn = Intent().apply {
                                action = ACTION_START_MOCK
                                putExtra(EXTRA_FAVORITE_ID, fav.id)
                                putExtra(EXTRA_FAVORITE_NAME, fav.name)
                                putExtra(EXTRA_LATITUDE, fav.latitude)
                                putExtra(EXTRA_LONGITUDE, fav.longitude)
                            }
                            itemViews.setOnClickFillInIntent(R.id.item_action_button, startFillIn)
                            itemViews.setOnClickFillInIntent(R.id.item_row_root, startFillIn)
                        }
                        itemsBuilder.addItem(fav.id, itemViews)
                    }
                }
                views.setRemoteAdapter(R.id.widget_list_view, itemsBuilder.build())
            } else {
                val serviceIntent = Intent(context, FavoritesWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("fakegps://widget/id/$appWidgetId")
                }
                views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
                views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
            }
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
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, FavoritesWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, appWidgetIds)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
