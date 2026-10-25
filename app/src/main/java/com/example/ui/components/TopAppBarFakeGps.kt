package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FakeGpsPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarFakeGps(
    isMocking: Boolean,
    onMenuClick: () -> Unit,
    onStartMock: () -> Unit,
    onStopMock: () -> Unit,
    onFilterClick: () -> Unit,
    onSearchCoordinateClick: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onShowHistory: () -> Unit,
    onShowFavorites: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "Fake GPS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.testTag("drawer_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Buka Menu",
                    tint = Color.White
                )
            }
        },
        actions = {
            // Start Mock Location (Play icon)
            IconButton(
                onClick = onStartMock,
                modifier = Modifier.testTag("top_bar_start_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start Mock Location",
                    tint = if (isMocking) Color.White.copy(alpha = 0.5f) else Color.White
                )
            }

            // Stop Mock Location (Square icon)
            IconButton(
                onClick = onStopMock,
                modifier = Modifier.testTag("top_bar_stop_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Mock Location",
                    tint = if (isMocking) Color.White else Color.White.copy(alpha = 0.5f)
                )
            }

            // Filter button (Funnel / Filter List)
            IconButton(
                onClick = onFilterClick,
                modifier = Modifier.testTag("top_bar_filter_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter Koordinat Peta",
                    tint = Color.White
                )
            }

            // 3-dots overflow menu
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.testTag("top_bar_more_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu Lainnya",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Cari / Masukkan Koordinat") },
                    onClick = {
                        showMenu = false
                        onSearchCoordinateClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Daftar Riwayat Lokasi") },
                    onClick = {
                        showMenu = false
                        onShowHistory()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Daftar Lokasi Favorit") },
                    onClick = {
                        showMenu = false
                        onShowFavorites()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Pengaturan Opsi Pengembang") },
                    onClick = {
                        showMenu = false
                        onOpenDeveloperOptions()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Hapus Semua Riwayat") },
                    onClick = {
                        showMenu = false
                        onClearHistory()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = FakeGpsPrimary,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        modifier = modifier.testTag("top_app_bar_fake_gps")
    )
}
