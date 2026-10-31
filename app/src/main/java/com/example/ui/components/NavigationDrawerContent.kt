package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import com.example.widget.WidgetHelper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FakeGpsAccent
import com.example.ui.theme.FakeGpsFavoriteRed
import com.example.ui.theme.FakeGpsHistoryBlue
import com.example.ui.theme.FakeGpsPrimary

import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import com.example.util.BatteryOptimizationHelper
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox

import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore

@Composable
fun NavigationDrawerContent(
    isMocking: Boolean,
    historyCount: Int,
    favoritesCount: Int,
    onShowHistory: () -> Unit,
    onShowFavorites: () -> Unit,
    onAddCurrentToFavorite: () -> Unit,
    onBackupFavorites: () -> Unit = {},
    onRestoreFavorites: () -> Unit = {},
    onSearchCoordinate: () -> Unit,
    onShowFilter: () -> Unit,
    onShowDevGuide: () -> Unit,
    onShowBatterySettings: () -> Unit,
    onShowGpsLockerIntegration: () -> Unit = {},
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        // Drawer Header with Fake GPS Theme
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FakeGpsPrimary)
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "My Maps",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Mock Location Provider",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Active status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isMocking) Color(0xFF4CAF50) else Color(0x33FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isMocking) "● Mock Location Sedang Aktif" else "○ Mock Location Nonaktif",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Items
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Map, contentDescription = null, tint = FakeGpsPrimary) },
            label = { Text("Peta Lokasi") },
            selected = true,
            onClick = onCloseDrawer,
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_map_item")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.History, contentDescription = null, tint = FakeGpsHistoryBlue) },
            label = { Text("Riwayat Mock Location ($historyCount)") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onShowHistory()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_history_item")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = FakeGpsFavoriteRed) },
            label = { Text("Lokasi Favorit ($favoritesCount)") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onShowFavorites()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_favorites_item")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.EditLocation, contentDescription = null, tint = Color(0xFF00897B)) },
            label = { Text("Simpan Posisi Ini ke Favorit") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onAddCurrentToFavorite()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_save_current_fav_item")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Widgets, contentDescription = null, tint = FakeGpsPrimary) },
            label = { Text("Pasang Widget ke Layar Utama") },
            selected = false,
            onClick = {
                onCloseDrawer()
                WidgetHelper.pinWidgetToHomeScreen(context)
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_pin_widget_item")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Backup, contentDescription = null, tint = FakeGpsPrimary) },
            label = { Text("Cadangkan Favorit (Backup ke HP)") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onBackupFavorites()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_backup_favorites_item")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF2E7D32)) },
            label = { Text("Pulihkan Favorit (Restore dari HP)") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onRestoreFavorites()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_restore_favorites_item")
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.FilterList, contentDescription = null, tint = FakeGpsPrimary) },
            label = { Text("Filter Penanda Peta") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onShowFilter()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_filter_item")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.PinDrop, contentDescription = null, tint = FakeGpsAccent) },
            label = { Text("Input Koordinat Manual / Preset") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onSearchCoordinate()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_search_coords_item")
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

        val isBatteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)

        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = if (isBatteryExempt) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = if (isBatteryExempt) Color(0xFF2E7D32) else Color(0xFFE65100)
                )
            },
            label = {
                Column {
                    Text("Pengaturan Baterai Latar Belakang")
                    Text(
                        text = if (isBatteryExempt) "Bebas pembatasan (Optimal)" else "Perlu dimatikan agar tidak tertutup",
                        fontSize = 11.sp,
                        color = if (isBatteryExempt) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }
            },
            selected = false,
            onClick = {
                onCloseDrawer()
                onShowBatterySettings()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_battery_optimization_item")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFE65100)) },
            label = { Text("Petunjuk Opsi Pengembang") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onShowDevGuide()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_dev_guide_item")
        )

        val isGpsLockerInstalled = com.example.util.GpsLockerBridge.isGpsLockerInstalled(context)
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.PinDrop,
                    contentDescription = null,
                    tint = if (isGpsLockerInstalled) Color(0xFF2E7D32) else FakeGpsPrimary
                )
            },
            label = {
                Column {
                    Text("Sinkronisasi GPS Locker")
                    Text(
                        text = if (isGpsLockerInstalled) "Terhubung • Otomatis bergantian" else "Mati otomatis saat mock aktif",
                        fontSize = 11.sp,
                        color = if (isGpsLockerInstalled) Color(0xFF2E7D32) else Color.Gray
                    )
                }
            },
            selected = false,
            onClick = {
                onCloseDrawer()
                onShowGpsLockerIntegration()
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_gps_locker_item")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF455A64)) },
            label = { Text("Buka Pengaturan Android") },
            selected = false,
            onClick = {
                onCloseDrawer()
                openDeveloperSettings(context)
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag("drawer_settings_item")
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "My Maps v1.0 • Kotlin & Jetpack Compose",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(20.dp)
        )
    }
}
