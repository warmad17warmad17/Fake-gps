package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.util.SharedLocationParser
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LocationFavoriteEntity
import com.example.data.model.LocationHistoryEntity
import com.example.ui.components.AddFavoriteDialog
import com.example.ui.components.BatteryOptimizationDialog
import com.example.ui.components.CoordinateInputDialog
import com.example.ui.components.DeveloperOptionsGuideDialog
import com.example.ui.components.EditFavoriteDialog
import com.example.util.BatteryOptimizationHelper
import com.example.ui.components.FavoritesBottomSheet
import com.example.ui.components.FilterDialog
import com.example.ui.components.HistoryBottomSheet
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.components.BackupDialog
import com.example.ui.components.RestorePreviewDialog
import com.example.util.BackupRestoreHelper
import com.example.ui.components.TopAppBarFakeGps
import com.example.ui.components.openDeveloperSettings
import com.example.ui.map.InteractiveMap
import com.example.ui.theme.MyApplicationTheme
import com.example.widget.FavoritesWidgetProvider
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Sync and refresh home screen widgets when app opens
        FavoritesWidgetProvider.updateAllWidgets(this)

        // Handle shared location intents (WhatsApp, Google Maps links, geo: URIs, etc.)
        handleIncomingLocationIntent(intent)

        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingLocationIntent(intent)
    }

    private fun handleIncomingLocationIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val currentAction = incomingIntent.action ?: return
        if (currentAction != Intent.ACTION_VIEW && currentAction != Intent.ACTION_SEND) return

        lifecycleScope.launch {
            val parsed = SharedLocationParser.parseIntent(
                intent = incomingIntent,
                addressSearchManager = viewModel.addressSearchManager
            )
            if (parsed != null) {
                viewModel.setLocationFromShared(parsed.latitude, parsed.longitude, parsed.label)
                val coordText = String.format(Locale.US, "%.6f, %.6f", parsed.latitude, parsed.longitude)
                val message = if (!parsed.label.isNullOrBlank()) {
                    "Lokasi dibuka dari aplikasi lain: ${parsed.label} ($coordText)"
                } else {
                    "Titik koordinat dibuka: $coordText"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val selectedLat by viewModel.selectedLat.collectAsStateWithLifecycle()
    val selectedLng by viewModel.selectedLng.collectAsStateWithLifecycle()
    val isMocking by viewModel.isMocking.collectAsStateWithLifecycle()
    val mockError by viewModel.mockError.collectAsStateWithLifecycle()

    val showHistoryOnMap by viewModel.showHistory.collectAsStateWithLifecycle()
    val showFavoritesOnMap by viewModel.showFavorites.collectAsStateWithLifecycle()

    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val favoritesList by viewModel.favoritesList.collectAsStateWithLifecycle()

    // Determine if the selected coordinates match any saved favorite
    val selectedFavorite = remember(selectedLat, selectedLng, favoritesList) {
        favoritesList.find {
            abs(it.latitude - selectedLat) < 0.0001 && abs(it.longitude - selectedLng) < 0.0001
        }
    }

    // Dialog & Sheet States
    var showFilterDialog by remember { mutableStateOf(false) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var addFavInitialName by remember { mutableStateOf("") }
    var targetFavLat by remember { mutableStateOf(selectedLat) }
    var targetFavLng by remember { mutableStateOf(selectedLng) }
    var favoriteToEdit by remember { mutableStateOf<LocationFavoriteEntity?>(null) }
    var showCoordinateInputDialog by remember { mutableStateOf(false) }
    var showDevGuideDialog by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

    var showHistorySheet by remember { mutableStateOf(false) }
    var showFavoritesSheet by remember { mutableStateOf(false) }

    // Backup & Restore state
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestorePreviewDialog by remember { mutableStateOf(false) }
    var pendingRestoreList by remember { mutableStateOf<List<LocationFavoriteEntity>>(emptyList()) }

    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val favoritesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Storage SAF Launcher for saving backup JSON to device internal/external storage
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = BackupRestoreHelper.createBackupJson(favoritesList)
            val success = BackupRestoreHelper.writeBackupToUri(context, uri, json)
            showBackupDialog = false
            coroutineScope.launch {
                if (success) {
                    snackbarHostState.showSnackbar("Berhasil mencadangkan ${favoritesList.size} lokasi favorit ke penyimpanan perangkat!")
                } else {
                    snackbarHostState.showSnackbar("Gagal menyimpan file cadangan ke penyimpanan.")
                }
            }
        }
    }

    // Storage SAF Launcher for opening backup JSON from device internal/external storage
    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val jsonContent = BackupRestoreHelper.readBackupFromUri(context, uri)
            if (!jsonContent.isNullOrBlank()) {
                val parsed = BackupRestoreHelper.parseBackupJson(jsonContent)
                if (parsed.isNotEmpty()) {
                    pendingRestoreList = parsed
                    showRestorePreviewDialog = true
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("File cadangan tidak berisi lokasi favorit yang valid.")
                    }
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Gagal membaca berkas cadangan dari penyimpanan.")
                }
            }
        }
    }

    val triggerBackup: () -> Unit = {
        if (favoritesList.isEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Belum ada lokasi favorit untuk dicadangkan.")
            }
        } else {
            showBackupDialog = true
        }
    }

    val triggerRestore: () -> Unit = {
        openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
    }

    // Permission launcher for Location, Storage, and Post Notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!locationGranted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Izin lokasi diperlukan untuk fungsi Mock Location")
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val needed = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    LaunchedEffect(isMocking) {
        if (isMocking) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = "Mock Location aktif pada %.6f, %.6f".format(selectedLat, selectedLng),
                duration = SnackbarDuration.Short
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                isMocking = isMocking,
                historyCount = historyList.size,
                favoritesCount = favoritesList.size,
                onShowHistory = { showHistorySheet = true },
                onShowFavorites = { showFavoritesSheet = true },
                onAddCurrentToFavorite = {
                    addFavInitialName = ""
                    targetFavLat = selectedLat
                    targetFavLng = selectedLng
                    showAddFavoriteDialog = true
                },
                onBackupFavorites = triggerBackup,
                onRestoreFavorites = triggerRestore,
                onSearchCoordinate = { showCoordinateInputDialog = true },
                onShowFilter = { showFilterDialog = true },
                onShowDevGuide = { showDevGuideDialog = true },
                onShowBatterySettings = { showBatteryOptimizationDialog = true },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Edge-to-edge Google Maps Engine with Floating Search & Autocomplete
                InteractiveMap(
                    selectedLat = selectedLat,
                    selectedLng = selectedLng,
                    isMocking = isMocking,
                    showHistory = showHistoryOnMap,
                    showFavorites = showFavoritesOnMap,
                    historyList = historyList,
                    favoritesList = favoritesList,
                    onLocationSelected = { lat, lng, fromUser ->
                        viewModel.selectLocation(lat, lng, fromUser)
                    },
                    onStartMock = {
                        viewModel.startMock(context)
                        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                            showBatteryOptimizationDialog = true
                        }
                    },
                    onStopMock = {
                        viewModel.stopMock(context)
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = "Mock Location dihentikan",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    onDeleteHistory = { id ->
                        viewModel.deleteHistory(id)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Riwayat lokasi telah dihapus",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    onAddHistoryToFavorite = { hist ->
                        targetFavLat = hist.latitude
                        targetFavLng = hist.longitude
                        addFavInitialName = if (hist.label.isNotBlank() && !hist.label.startsWith("Lat:")) hist.label else "Location ${hist.id}"
                        showAddFavoriteDialog = true
                    },
                    onEditFavorite = { fav ->
                        favoriteToEdit = fav
                    },
                    onDeleteFavorite = { fav ->
                        viewModel.deleteFavorite(fav)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Favorit '${fav.name}' telah dihapus",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    onMenuClick = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    onSearchCoordinateClick = {
                        showCoordinateInputDialog = true
                    },
                    onShowFavorites = {
                        showFavoritesSheet = true
                    },
                    onShowHistory = {
                        showHistorySheet = true
                    }
                )

                // Top-aligned notification banner positioned below the search bar chips (never blocks bottom Start/Stop FAB)
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 118.dp, start = 16.dp, end = 16.dp)
                        .zIndex(100f)
                ) { data ->
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF212121).copy(alpha = 0.94f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { data.dismiss() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isStopped = data.visuals.message.contains("dihentikan", ignoreCase = true)
                            Icon(
                                imageVector = if (isStopped) Icons.Default.StopCircle else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isStopped) Color(0xFFEF5350) else Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = data.visuals.message,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Filter Dialog
    if (showFilterDialog) {
        FilterDialog(
            showHistory = showHistoryOnMap,
            showFavorites = showFavoritesOnMap,
            onToggleHistory = { viewModel.toggleFilterHistory(it) },
            onToggleFavorites = { viewModel.toggleFilterFavorites(it) },
            onDismiss = { showFilterDialog = false }
        )
    }

    // Add Favorite Dialog (Set Favorite Data)
    if (showAddFavoriteDialog) {
        AddFavoriteDialog(
            latitude = targetFavLat,
            longitude = targetFavLng,
            initialTitle = addFavInitialName,
            onConfirm = { title, description ->
                viewModel.addFavorite(title, targetFavLat, targetFavLng, description)
                showAddFavoriteDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("'$title' berhasil ditambahkan ke favorit")
                }
            },
            onDismiss = { showAddFavoriteDialog = false }
        )
    }

    // Edit Favorite Dialog
    if (favoriteToEdit != null) {
        EditFavoriteDialog(
            favorite = favoriteToEdit!!,
            onConfirm = { updated ->
                viewModel.updateFavorite(updated)
                favoriteToEdit = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Nama favorit diperbarui menjadi '${updated.name}'")
                }
            },
            onDismiss = { favoriteToEdit = null }
        )
    }

    // Coordinate Input Dialog
    if (showCoordinateInputDialog) {
        CoordinateInputDialog(
            currentLat = selectedLat,
            currentLng = selectedLng,
            onConfirm = { lat, lng ->
                viewModel.selectLocation(lat, lng)
                showCoordinateInputDialog = false
            },
            onDismiss = { showCoordinateInputDialog = false }
        )
    }

    // Developer Options Guide Dialog
    if (showDevGuideDialog) {
        DeveloperOptionsGuideDialog(
            onDismiss = { showDevGuideDialog = false }
        )
    }

    // Battery Optimization Dialog
    if (showBatteryOptimizationDialog) {
        BatteryOptimizationDialog(
            onDismissRequest = { showBatteryOptimizationDialog = false }
        )
    }

    // Error Alert Dialog (if Developer options mock location app is not configured)
    if (mockError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMockError() },
            title = { Text("Peringatan Mock Location") },
            text = { Text(mockError ?: "") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearMockError()
                        openDeveloperSettings(context)
                    },
                    modifier = Modifier.testTag("error_dialog_open_settings_button")
                ) {
                    Text("Buka Opsi Pengembang")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearMockError() },
                    modifier = Modifier.testTag("error_dialog_dismiss_button")
                ) {
                    Text("Tutup")
                }
            }
        )
    }

    // History Bottom Sheet
    if (showHistorySheet) {
        HistoryBottomSheet(
            sheetState = historySheetState,
            historyList = historyList,
            onSelectLocation = { lat, lng ->
                viewModel.selectLocation(lat, lng)
            },
            onAddToFavorite = { historyItem ->
                targetFavLat = historyItem.latitude
                targetFavLng = historyItem.longitude
                addFavInitialName = if (historyItem.label.isNotBlank() && !historyItem.label.startsWith("Lat:")) historyItem.label else "Lokasi ${historyItem.id}"
                showAddFavoriteDialog = true
            },
            onDeleteHistory = { id ->
                viewModel.deleteHistory(id)
            },
            onClearAllHistory = {
                viewModel.clearAllHistory()
            },
            onDismiss = { showHistorySheet = false }
        )
    }

    // Favorites Bottom Sheet
    if (showFavoritesSheet) {
        FavoritesBottomSheet(
            sheetState = favoritesSheetState,
            favoritesList = favoritesList,
            onSelectFavorite = { fav ->
                viewModel.selectLocation(fav.latitude, fav.longitude)
            },
            onEditFavorite = { fav ->
                favoriteToEdit = fav
            },
            onDeleteFavorite = { fav ->
                viewModel.deleteFavorite(fav)
            },
            onReorderFavorites = { newOrderList ->
                viewModel.reorderFavorites(newOrderList)
            },
            onDismiss = { showFavoritesSheet = false }
        )
    }

    // Backup Dialog
    if (showBackupDialog) {
        BackupDialog(
            favoritesCount = favoritesList.size,
            onSaveToStorage = {
                createDocumentLauncher.launch(BackupRestoreHelper.generateDefaultBackupFileName())
            },
            onShareBackup = {
                val shareIntent = BackupRestoreHelper.createShareIntent(context, favoritesList)
                if (shareIntent != null) {
                    context.startActivity(shareIntent)
                    showBackupDialog = false
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Gagal menyiapkan file cadangan untuk dibagikan.")
                    }
                }
            },
            onDismiss = { showBackupDialog = false }
        )
    }

    // Restore Preview & Confirmation Dialog
    if (showRestorePreviewDialog && pendingRestoreList.isNotEmpty()) {
        RestorePreviewDialog(
            favoritesToRestore = pendingRestoreList,
            onConfirmRestore = { replaceExisting ->
                val listToRestore = pendingRestoreList
                viewModel.restoreFavorites(listToRestore, replaceExisting) { count ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Berhasil memulihkan $count lokasi favorit!")
                    }
                }
                showRestorePreviewDialog = false
                pendingRestoreList = emptyList()
            },
            onDismiss = {
                showRestorePreviewDialog = false
                pendingRestoreList = emptyList()
            }
        )
    }
}
