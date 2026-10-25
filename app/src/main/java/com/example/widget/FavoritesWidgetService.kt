package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.FakeGpsApp
import com.example.R
import com.example.data.model.LocationFavoriteEntity

class FavoritesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FavoritesRemoteViewsFactory(this.applicationContext)
    }
}

class FavoritesRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var favoritesList: List<LocationFavoriteEntity> = emptyList()

    override fun onCreate() {
        loadFavorites()
    }

    override fun onDataSetChanged() {
        loadFavorites()
    }

    private fun loadFavorites() {
        val app = context.applicationContext as? FakeGpsApp
        favoritesList = app?.database?.locationDao()?.getAllFavoritesList() ?: emptyList()
    }

    override fun onDestroy() {
        favoritesList = emptyList()
    }

    override fun getCount(): Int = favoritesList.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= favoritesList.size) return null
        val fav = favoritesList[position]

        val views = RemoteViews(context.packageName, R.layout.widget_favorite_item)
        views.setTextViewText(R.id.item_name, fav.name)
        views.setTextViewText(R.id.item_coords, "Lat: %.6f, Lng: %.6f".format(fav.latitude, fav.longitude))

        val prefs = context.getSharedPreferences(FavoritesWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val isMocking = prefs.getBoolean(FavoritesWidgetProvider.KEY_IS_MOCKING, false)
        val activeId = prefs.getLong(FavoritesWidgetProvider.KEY_ACTIVE_ID, -1L)
        val activeLatStr = prefs.getString(FavoritesWidgetProvider.KEY_ACTIVE_LAT, null)
        val activeLngStr = prefs.getString(FavoritesWidgetProvider.KEY_ACTIVE_LNG, null)

        val isActive = isMocking && (activeId == fav.id || (
            activeLatStr?.toDoubleOrNull() == fav.latitude && activeLngStr?.toDoubleOrNull() == fav.longitude
        ))

        if (isActive) {
            views.setImageViewResource(R.id.item_action_button, R.drawable.ic_widget_stop_red)
            views.setTextColor(R.id.item_name, 0xFF1E88E5.toInt())
            val stopFillIn = Intent().apply {
                action = FavoritesWidgetProvider.ACTION_STOP_MOCK
            }
            views.setOnClickFillInIntent(R.id.item_action_button, stopFillIn)
            views.setOnClickFillInIntent(R.id.item_row_root, stopFillIn)
        } else {
            views.setImageViewResource(R.id.item_action_button, R.drawable.ic_widget_target_blue)
            views.setTextColor(R.id.item_name, 0xFF212121.toInt())
            val startFillIn = Intent().apply {
                action = FavoritesWidgetProvider.ACTION_START_MOCK
                putExtra(FavoritesWidgetProvider.EXTRA_FAVORITE_ID, fav.id)
                putExtra(FavoritesWidgetProvider.EXTRA_FAVORITE_NAME, fav.name)
                putExtra(FavoritesWidgetProvider.EXTRA_LATITUDE, fav.latitude)
                putExtra(FavoritesWidgetProvider.EXTRA_LONGITUDE, fav.longitude)
            }
            views.setOnClickFillInIntent(R.id.item_action_button, startFillIn)
            views.setOnClickFillInIntent(R.id.item_row_root, startFillIn)
        }

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position in favoritesList.indices) favoritesList[position].id else position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
