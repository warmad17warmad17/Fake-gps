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
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
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

@Composable
fun NavigationDrawerContent(
    isMocking: Boolean,
    historyCount: Int,
    favoritesCount: Int,
    onShowHistory: () -> Unit,
    onShowFavorites: () -> Unit,
    onAddCurrentToFavorite: () -> Unit,
    onSearchCoordinate: () -> Unit,
    onShowFilter: () -> Unit,
    onShowDevGuide: () -> Unit,
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
                            text = "Fake GPS",
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
            text = "Fake GPS v1.0 • Kotlin & Jetpack Compose",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(20.dp)
        )
    }
}
