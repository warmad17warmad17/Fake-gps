package com.example.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationFavoriteEntity
import com.example.data.model.LocationHistoryEntity
import com.example.ui.theme.FakeGpsAccent
import com.example.ui.theme.FakeGpsFavoriteRed
import com.example.ui.theme.FakeGpsHistoryBlue
import com.example.ui.theme.FakeGpsPrimary
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.example.ui.components.GoogleMapsSearchBar
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun InteractiveMap(
    selectedLat: Double,
    selectedLng: Double,
    isMocking: Boolean,
    showHistory: Boolean,
    showFavorites: Boolean,
    historyList: List<LocationHistoryEntity>,
    favoritesList: List<LocationFavoriteEntity>,
    onLocationSelected: (Double, Double, Boolean) -> Unit,
    onStartMock: () -> Unit,
    onStopMock: () -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onAddHistoryToFavorite: (LocationHistoryEntity) -> Unit,
    onEditFavorite: (LocationFavoriteEntity) -> Unit = {},
    onDeleteFavorite: (LocationFavoriteEntity) -> Unit = {},
    onMenuClick: () -> Unit = {},
    onSearchCoordinateClick: () -> Unit = {},
    onShowFavorites: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tileManager = remember { TileManager(coroutineScope, context.applicationContext) }

    // Map Camera state (Surabaya coordinates matching screenshot)
    var centerLat by remember { mutableDoubleStateOf(selectedLat) }
    var centerLng by remember { mutableDoubleStateOf(selectedLng) }
    var zoomLevel by remember { mutableDoubleStateOf(16.5) }
    var tileRefreshTrigger by remember { mutableIntStateOf(0) }

    // Synchronize camera when selected coordinates change externally (e.g. from search, dialog, or favorite)
    LaunchedEffect(selectedLat, selectedLng) {
        centerLat = selectedLat
        centerLng = selectedLng
    }

    // Selected history item for showing Delete & Add to Favorite action popup
    var selectedHistoryItem by remember { mutableStateOf<LocationHistoryEntity?>(null) }

    // Selected favorite item for showing name callout and Delete & Edit actions
    var selectedFavoriteItem by remember { mutableStateOf<LocationFavoriteEntity?>(null) }

    // Blue dot drag state
    var isDraggingDot by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableStateOf(Offset.Zero) }

    // Micro-precision coordinate nudge pad (for exact meter-level adjustments at full zoom)
    var showNudgePad by remember { mutableStateOf(false) }
    var nudgeStepIndex by remember { mutableIntStateOf(0) }
    val nudgeSteps = remember { listOf(0.000005, 0.00001, 0.00005, 0.00010) }
    val nudgeLabels = remember { listOf("±0.5m", "±1m", "±5m", "±10m") }

    // Synchronize selected favorite if database changes (e.g. edited name or deleted)
    LaunchedEffect(favoritesList) {
        if (selectedFavoriteItem != null) {
            selectedFavoriteItem = favoritesList.find { it.id == selectedFavoriteItem!!.id }
        }
    }

    // Listen to tiles updates
    LaunchedEffect(Unit) {
        tileManager.tileRefreshTrigger.collect {
            tileRefreshTrigger++
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val centerX = widthPx / 2f
        val centerY = heightPx / 2f

        val zoomInt = zoomLevel.roundToInt().coerceIn(3, 22)
        val effectiveZoom = zoomInt.toDouble()

        // Fetch visible Google Maps tiles outside Draw phase
        LaunchedEffect(centerLat, centerLng, effectiveZoom, widthPx, heightPx, tileManager.currentTileType) {
            delay(40)
            val (centerWorldX, centerWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, effectiveZoom)
            val minWorldX = centerWorldX - centerX
            val maxWorldX = centerWorldX + centerX
            val minWorldY = centerWorldY - centerY
            val maxWorldY = centerWorldY + centerY
            val tileSize = MapProjection.TILE_SIZE
            val minTileX = floor(minWorldX / tileSize).toInt()
            val maxTileX = floor(maxWorldX / tileSize).toInt()
            val minTileY = floor(minWorldY / tileSize).toInt()
            val maxTileY = floor(maxWorldY / tileSize).toInt()
            val maxTilesAcross = 1 shl zoomInt

            val neededTiles = mutableListOf<Triple<Int, Int, Int>>()
            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    if (ty in 0 until maxTilesAcross) {
                        val normTx = ((tx % maxTilesAcross) + maxTilesAcross) % maxTilesAcross
                        neededTiles.add(Triple(zoomInt, normTx, ty))
                        // Also prefetch parent tile at zoom 20 if zoomInt > 20 so fallback is always instantly ready
                        if (zoomInt > 20) {
                            val pDiff = zoomInt - 20
                            neededTiles.add(Triple(20, normTx shr pDiff, ty shr pDiff))
                        }
                    }
                }
            }
            tileManager.requestTiles(neededTiles)
        }

        // Canvas for Slippy Tiles & Road Grid Background
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // 1. Gentle zoom with deadzone to prevent accidental zooming during panning
                        if (kotlin.math.abs(zoom - 1f) > 0.02f) {
                            val zoomDelta = ((zoom - 1f) * 0.55).coerceIn(-0.25, 0.25)
                            zoomLevel = (zoomLevel + zoomDelta).coerceIn(3.0, 22.0)
                        }

                        // 2. Adaptive pan damping based on zoom level:
                        // At zoom 3: ~0.50f (responsive)
                        // At zoom 18-22 (full zoom): 0.12f - 0.20f (ultra-fine micro-precision)
                        val zoomRatio = ((zoomLevel - 3.0) / 19.0).coerceIn(0.0, 1.0)
                        val panDamping = (0.50 - (zoomRatio * 0.38)).toFloat()
                        val maxStep = (20f - (zoomRatio * 16f).toFloat()).coerceAtLeast(3f)

                        val smoothPanX = (pan.x * panDamping).coerceIn(-maxStep, maxStep)
                        val smoothPanY = (pan.y * panDamping).coerceIn(-maxStep, maxStep)

                        val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, effectiveZoom)
                        val newWorldX = currWorldX - smoothPanX
                        val newWorldY = currWorldY - smoothPanY
                        val (newLat, newLon) = MapProjection.worldPixelToLatLon(newWorldX, newWorldY, effectiveZoom)
                        centerLat = newLat.coerceIn(-85.0, 85.0)
                        centerLng = ((newLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                    }
                }
                .pointerInput(centerLat, centerLng, effectiveZoom, favoritesList, historyList, showFavorites, showHistory) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, effectiveZoom)

                            // 1. Check if tapped directly on/near any favorite marker (36dp radius)
                            val tappedFav = if (showFavorites) {
                                favoritesList.find { fav ->
                                    val (fwX, fwY) = MapProjection.latLonToWorldPixel(fav.latitude, fav.longitude, effectiveZoom)
                                    val fx = centerX + (fwX - currWorldX).toFloat()
                                    val fy = centerY + (fwY - currWorldY).toFloat() - 17.dp.toPx()
                                    val dx = fx - tapOffset.x
                                    val dy = fy - tapOffset.y
                                    (dx * dx + dy * dy) <= (36.dp.toPx() * 36.dp.toPx())
                                }
                            } else null

                            // 2. Check if tapped directly on/near any history marker (36dp radius)
                            val tappedHist = if (showHistory && tappedFav == null) {
                                historyList.find { hist ->
                                    val (hwX, hwY) = MapProjection.latLonToWorldPixel(hist.latitude, hist.longitude, effectiveZoom)
                                    val hx = centerX + (hwX - currWorldX).toFloat()
                                    val hy = centerY + (hwY - currWorldY).toFloat() - 17.dp.toPx()
                                    val dx = hx - tapOffset.x
                                    val dy = hy - tapOffset.y
                                    (dx * dx + dy * dy) <= (36.dp.toPx() * 36.dp.toPx())
                                }
                            } else null

                            if (tappedFav != null) {
                                // Select favorite to inspect/edit without moving blue dot
                                selectedFavoriteItem = tappedFav
                                selectedHistoryItem = null
                            } else if (tappedHist != null) {
                                // Select history to inspect/favorite without moving blue dot
                                selectedHistoryItem = tappedHist
                                selectedFavoriteItem = null
                            } else {
                                // Tap on map: move the blue dot ONLY, leaving existing history and favorites intact
                                selectedHistoryItem = null
                                selectedFavoriteItem = null
                                val tapWorldX = currWorldX + (tapOffset.x - centerX)
                                val tapWorldY = currWorldY + (tapOffset.y - centerY)
                                val (tapLat, tapLon) = MapProjection.worldPixelToLatLon(tapWorldX, tapWorldY, effectiveZoom)
                                onLocationSelected(tapLat, tapLon, true)
                            }
                        },
                        onDoubleTap = { tapOffset ->
                            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, effectiveZoom)
                            val tapWorldX = currWorldX + (tapOffset.x - centerX)
                            val tapWorldY = currWorldY + (tapOffset.y - centerY)
                            val (tapLat, tapLon) = MapProjection.worldPixelToLatLon(tapWorldX, tapWorldY, effectiveZoom)
                            centerLat = tapLat
                            centerLng = tapLon
                            zoomLevel = (zoomLevel + 1.0).coerceAtMost(22.0)
                        }
                    )
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val trigger = tileRefreshTrigger

            val bgColor = if (tileManager.currentTileType == MapTileType.GOOGLE_SATELLITE) Color(0xFF1E262C) else Color(0xFFF2F4F7)
            drawRect(bgColor)

            val zoomInt = zoomLevel.roundToInt().coerceIn(3, 22)
            val (centerWorldX, centerWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, zoomInt.toDouble())

            val minWorldX = centerWorldX - centerX
            val maxWorldX = centerWorldX + centerX
            val minWorldY = centerWorldY - centerY
            val maxWorldY = centerWorldY + centerY

            val tileSize = MapProjection.TILE_SIZE
            val minTileX = floor(minWorldX / tileSize).toInt()
            val maxTileX = floor(maxWorldX / tileSize).toInt()
            val minTileY = floor(minWorldY / tileSize).toInt()
            val maxTileY = floor(maxWorldY / tileSize).toInt()

            val maxTilesAcross = 1 shl zoomInt

            // Draw subtle street grid guides
            val gridStep = 64f
            var gx = 0f
            val gridColor = if (tileManager.currentTileType == MapTileType.GOOGLE_SATELLITE) Color(0xFF2C3842) else Color(0xFFE4E7EB)
            while (gx < size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(gx, 0f),
                    end = Offset(gx, size.height),
                    strokeWidth = 1f
                )
                gx += gridStep
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 1f
                )
                gy += gridStep
            }

            // Draw Google Maps Slippy Tiles with zero-delay hierarchical fallback
            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    if (ty in 0 until maxTilesAcross) {
                        val normTx = ((tx % maxTilesAcross) + maxTilesAcross) % maxTilesAcross
                        val tileData = tileManager.getTileWithFallback(zoomInt, normTx, ty)

                        val tileScreenX = (tx * tileSize - minWorldX).toFloat()
                        val tileScreenY = (ty * tileSize - minWorldY).toFloat()

                        if (tileData != null) {
                            drawImage(
                                image = tileData.bitmap,
                                srcOffset = tileData.srcOffset,
                                srcSize = tileData.srcSize,
                                dstOffset = IntOffset(tileScreenX.roundToInt(), tileScreenY.roundToInt()),
                                dstSize = IntSize(tileSize.roundToInt(), tileSize.roundToInt())
                            )
                        }
                    }
                }
            }
        }

        // Overlay Markers for History (BLUE pins)
        if (showHistory) {
            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, effectiveZoom)
            for (hist in historyList) {
                val (hwX, hwY) = MapProjection.latLonToWorldPixel(hist.latitude, hist.longitude, effectiveZoom)
                val sx = centerX + (hwX - currWorldX).toFloat()
                val sy = centerY + (hwY - currWorldY).toFloat()

                if (sx >= -40 && sx <= widthPx + 40 && sy >= -60 && sy <= heightPx + 40) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((sx - 15.dp.toPx()).roundToInt(), (sy - 34.dp.toPx()).roundToInt()) }
                            .clickable {
                                // Select this history pin to view options WITHOUT moving the blue dot
                                selectedHistoryItem = hist
                                selectedFavoriteItem = null
                            }
                            .testTag("history_pin_${hist.id}")
                    ) {
                        PinMarker(
                            color = FakeGpsHistoryBlue,
                            size = 30.dp
                        )
                    }
                }
            }
        }

        // Action popup and label above clicked History pin (matching user's red circle screenshot)
        if (selectedHistoryItem != null && showHistory) {
            val hist = selectedHistoryItem!!
            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, effectiveZoom)
            val (hwX, hwY) = MapProjection.latLonToWorldPixel(hist.latitude, hist.longitude, effectiveZoom)
            val hx = centerX + (hwX - currWorldX).toFloat()
            val hy = centerY + (hwY - currWorldY).toFloat()

            if (hx >= -60 && hx <= widthPx + 60 && hy >= -80 && hy <= heightPx + 60) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (hx - 80.dp.toPx()).roundToInt(),
                                (hy - 110.dp.toPx()).roundToInt()
                            )
                        }
                        .size(width = 160.dp, height = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Floating Action Pill: Delete, Add to Favorite, and Optional Set Blue Dot
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xEEFFFFFF),
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Delete button (Trash can)
                            IconButton(
                                onClick = {
                                    onDeleteHistory(hist.id)
                                    selectedHistoryItem = null
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("history_pin_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus History",
                                    tint = Color(0xFF616161),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Add to Favorite button (Red Heart)
                            IconButton(
                                onClick = {
                                    onAddHistoryToFavorite(hist)
                                    selectedHistoryItem = null
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("history_pin_favorite_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Tambah ke Favorit",
                                    tint = FakeGpsFavoriteRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Move Blue Dot to this History point (optional user action)
                            IconButton(
                                onClick = {
                                    onLocationSelected(hist.latitude, hist.longitude, true)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("history_pin_set_location_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = "Pindahkan Titik Biru ke Sini",
                                    tint = FakeGpsPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Speech bubble with location title (e.g. "Location 98")
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = if (hist.label.isNotBlank() && !hist.label.startsWith("Lat:")) hist.label else "Location ${hist.id}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFF212121),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Overlay Markers for Favorites (RED pins)
        if (showFavorites) {
            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, effectiveZoom)
            for (fav in favoritesList) {
                val (fwX, fwY) = MapProjection.latLonToWorldPixel(fav.latitude, fav.longitude, effectiveZoom)
                val sx = centerX + (fwX - currWorldX).toFloat()
                val sy = centerY + (fwY - currWorldY).toFloat()

                if (sx >= -40 && sx <= widthPx + 40 && sy >= -60 && sy <= heightPx + 40) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((sx - 15.dp.toPx()).roundToInt(), (sy - 34.dp.toPx()).roundToInt()) }
                            .clickable {
                                // Select this favorite pin to view callout WITHOUT moving the blue dot
                                selectedFavoriteItem = fav
                                selectedHistoryItem = null
                            }
                            .testTag("favorite_pin_${fav.id}")
                    ) {
                        PinMarker(
                            color = FakeGpsFavoriteRed,
                            size = 30.dp
                        )
                    }
                }
            }
        }

        // Callout displaying favorite name above clicked Favorite pin (Speech bubble with downward pointer)
        if (selectedFavoriteItem != null && showFavorites) {
            val fav = selectedFavoriteItem!!
            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, effectiveZoom)
            val (fwX, fwY) = MapProjection.latLonToWorldPixel(fav.latitude, fav.longitude, effectiveZoom)
            val fx = centerX + (fwX - currWorldX).toFloat()
            val fy = centerY + (fwY - currWorldY).toFloat()

            if (fx >= -100 && fx <= widthPx + 100 && fy >= -120 && fy <= heightPx + 60) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(fx.roundToInt(), (fy - 34.dp.toPx()).roundToInt())
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(-placeable.width / 2, -placeable.height)
                            }
                        }
                        .testTag("favorite_callout_${fav.id}")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            onEditFavorite(fav)
                        }
                    ) {
                        // White speech bubble container with the favorite's name
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White,
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = fav.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E1E1E),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        // Downward-pointing beak/pointer aiming directly at the top of the red pin
                        Canvas(modifier = Modifier.size(width = 12.dp, height = 7.dp)) {
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                            drawPath(path = path, color = Color.White)
                        }
                    }
                }
            }
        }

        // Floating Action Buttons for Selected Favorite (Delete, Edit, & Set Blue Dot)
        if (selectedFavoriteItem != null && showFavorites) {
            val fav = selectedFavoriteItem!!
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 120.dp)
                    .zIndex(5f)
                    .testTag("selected_favorite_actions_row"),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete favorite button (trash can icon)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xCCE0E0E0),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable {
                            onDeleteFavorite(fav)
                            selectedFavoriteItem = null
                        }
                        .testTag("favorite_delete_action_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Favorit",
                            tint = Color(0xFF424242),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Edit favorite button (green pencil icon)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xCCE0E0E0),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable {
                            onEditFavorite(fav)
                        }
                        .testTag("favorite_edit_action_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Favorit",
                            tint = Color(0xFF00897B),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Move Blue Dot to this Favorite point (explicit user action)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xCCE0E0E0),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable {
                            onLocationSelected(fav.latitude, fav.longitude, true)
                        }
                        .testTag("favorite_set_location_action_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Pindahkan Titik Biru ke Favorit Ini",
                            tint = FakeGpsPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Selected / Active Coordinate: Authentic Google Maps Blue Dot (Directly Draggable!)
        val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, effectiveZoom)
        val (selWorldX, selWorldY) = MapProjection.latLonToWorldPixel(selectedLat, selectedLng, effectiveZoom)
        val selScreenX = centerX + (selWorldX - currWorldX).toFloat()
        val selScreenY = centerY + (selWorldY - currWorldY).toFloat()

        val displayX = if (isDraggingDot) selScreenX + dragOffsetPx.x else selScreenX
        val displayY = if (isDraggingDot) selScreenY + dragOffsetPx.y else selScreenY

        // Active coordinates for display (HUD Card)
        val activeDisplayCoords = if (isDraggingDot) {
            val dragWorldX = currWorldX + (displayX - centerX)
            val dragWorldY = currWorldY + (displayY - centerY)
            MapProjection.worldPixelToLatLon(dragWorldX, dragWorldY, effectiveZoom)
        } else {
            Pair(selectedLat, selectedLng)
        }

        // Google Maps Blue Dot centered exactly at (displayX, displayY)
        GoogleMapsBlueDot(
            isDragging = isDraggingDot,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (displayX - 22.dp.toPx()).roundToInt(),
                        (displayY - 22.dp.toPx()).roundToInt()
                    )
                }
                .pointerInput(centerLat, centerLng, effectiveZoom, selectedLat, selectedLng) {
                    detectDragGestures(
                        onDragStart = {
                            isDraggingDot = true
                            dragOffsetPx = Offset.Zero
                            selectedHistoryItem = null
                            selectedFavoriteItem = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx += dragAmount
                        },
                        onDragEnd = {
                            isDraggingDot = false
                            val finalScreenX = selScreenX + dragOffsetPx.x
                            val finalScreenY = selScreenY + dragOffsetPx.y
                            val finalWorldX = currWorldX + (finalScreenX - centerX)
                            val finalWorldY = currWorldY + (finalScreenY - centerY)
                            val (newLat, newLon) = MapProjection.worldPixelToLatLon(finalWorldX, finalWorldY, effectiveZoom)
                            dragOffsetPx = Offset.Zero
                            onLocationSelected(newLat, newLon, true)
                        },
                        onDragCancel = {
                            isDraggingDot = false
                            dragOffsetPx = Offset.Zero
                        }
                    )
                }
                .testTag("google_maps_blue_dot")
        )

        // Top Floating Google Maps Search Bar with Address Geocoding & Filters
        GoogleMapsSearchBar(
            onMenuClick = onMenuClick,
            onLocationSelected = { sLat, sLng, _ ->
                centerLat = sLat
                centerLng = sLng
                zoomLevel = 16.5
                onLocationSelected(sLat, sLng, true)
            },
            onSearchCoordinateClick = onSearchCoordinateClick,
            onShowFavorites = onShowFavorites,
            onShowHistory = onShowHistory,
            onToggleLayer = {
                val nextType = when (tileManager.currentTileType) {
                    MapTileType.GOOGLE_ROADMAP -> MapTileType.GOOGLE_SATELLITE
                    MapTileType.GOOGLE_SATELLITE -> MapTileType.GOOGLE_TERRAIN
                    MapTileType.GOOGLE_TERRAIN -> MapTileType.GOOGLE_ROADMAP
                }
                tileManager.currentTileType = nextType
            },
            currentTileType = tileManager.currentTileType,
            isMocking = isMocking,
            onStartMock = onStartMock,
            onStopMock = onStopMock,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 4.dp)
                .zIndex(10f)
        )

        // Right-side Google Maps Controls (Recenter, Layer Switcher, Zoom In/Out)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 118.dp, end = 14.dp)
                .zIndex(5f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recenter to target / Mock position
            IconButton(
                onClick = {
                    centerLat = selectedLat
                    centerLng = selectedLng
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White, CircleShape)
                    .testTag("recenter_map_button")
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Pusatkan ke Lokasi",
                    tint = Color(0xFF424242)
                )
            }

            // Layer toggle (Google Maps Standar vs Satelit vs Medan)
            IconButton(
                onClick = {
                    val nextType = when (tileManager.currentTileType) {
                        MapTileType.GOOGLE_ROADMAP -> MapTileType.GOOGLE_SATELLITE
                        MapTileType.GOOGLE_SATELLITE -> MapTileType.GOOGLE_TERRAIN
                        MapTileType.GOOGLE_TERRAIN -> MapTileType.GOOGLE_ROADMAP
                    }
                    tileManager.currentTileType = nextType
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White, CircleShape)
                    .testTag("map_layer_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Ganti Tipe Peta (${tileManager.currentTileType.label})",
                    tint = FakeGpsPrimary
                )
            }

            // Zoom In
            IconButton(
                onClick = { zoomLevel = (zoomLevel + 1.0).coerceAtMost(22.0) },
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White, CircleShape)
                    .testTag("zoom_in_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Perbesar",
                    tint = Color(0xFF424242)
                )
            }

            // Zoom Out
            IconButton(
                onClick = { zoomLevel = (zoomLevel - 1.0).coerceAtLeast(3.0) },
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White, CircleShape)
                    .testTag("zoom_out_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Perkecil",
                    tint = Color(0xFF424242)
                )
            }
        }

        // Bottom Coordinates HUD Card with optional Micro-Precision Nudge Controls
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .testTag("coordinates_hud_card")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lat: ",
                                fontSize = 12.sp,
                                color = Color(0xFF616161),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "%.6f".format(activeDisplayCoords.first),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lng: ",
                                fontSize = 12.sp,
                                color = Color(0xFF616161),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "%.6f".format(activeDisplayCoords.second),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Micro-precision toggle button
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (showNudgePad) FakeGpsPrimary else Color(0xFFEEEEEE),
                        modifier = Modifier
                            .clickable { showNudgePad = !showNudgePad }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("toggle_nudge_pad_button")
                    ) {
                        Text(
                            text = if (showNudgePad) "Tutup" else "Presisi ±1m",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (showNudgePad) Color.White else Color(0xFF424242)
                        )
                    }
                }

                if (showNudgePad) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE0F2F1),
                            modifier = Modifier
                                .clickable {
                                    nudgeStepIndex = (nudgeStepIndex + 1) % nudgeSteps.size
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = nudgeLabels[nudgeStepIndex],
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF00796B)
                            )
                        }

                        val step = nudgeSteps[nudgeStepIndex]

                        // Left (West)
                        IconButton(
                            onClick = {
                                val nextLng = ((selectedLng - step + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                                onLocationSelected(selectedLat, nextLng, true)
                                centerLng = nextLng
                            },
                            modifier = Modifier.size(28.dp).background(Color(0xFFF5F5F5), CircleShape)
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Geser Barat", modifier = Modifier.size(18.dp), tint = Color(0xFF424242))
                        }

                        // Up (North)
                        IconButton(
                            onClick = {
                                val nextLat = (selectedLat + step).coerceIn(-85.0, 85.0)
                                onLocationSelected(nextLat, selectedLng, true)
                                centerLat = nextLat
                            },
                            modifier = Modifier.size(28.dp).background(Color(0xFFF5F5F5), CircleShape)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Geser Utara", modifier = Modifier.size(18.dp), tint = Color(0xFF424242))
                        }

                        // Down (South)
                        IconButton(
                            onClick = {
                                val nextLat = (selectedLat - step).coerceIn(-85.0, 85.0)
                                onLocationSelected(nextLat, selectedLng, true)
                                centerLat = nextLat
                            },
                            modifier = Modifier.size(28.dp).background(Color(0xFFF5F5F5), CircleShape)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Geser Selatan", modifier = Modifier.size(18.dp), tint = Color(0xFF424242))
                        }

                        // Right (East)
                        IconButton(
                            onClick = {
                                val nextLng = ((selectedLng + step + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                                onLocationSelected(selectedLat, nextLng, true)
                                centerLng = nextLng
                            },
                            modifier = Modifier.size(28.dp).background(Color(0xFFF5F5F5), CircleShape)
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Geser Timur", modifier = Modifier.size(18.dp), tint = Color(0xFF424242))
                        }
                    }
                }
            }
        }

        // Bottom Left Google branding logo
        Text(
            text = "Google",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF757575),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
        )

        // Bottom Right Floating Action Button (Play / Stop)
        FloatingActionButton(
            onClick = {
                if (isMocking) onStopMock() else onStartMock()
            },
            containerColor = FakeGpsAccent,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp)
                .size(60.dp)
                .testTag("mock_toggle_fab")
        ) {
            Icon(
                imageVector = if (isMocking) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isMocking) "Stop Mock Location" else "Start Mock Location",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Authentic Google Maps Blue Dot indicator:
 * - Pulsing outer light blue halo
 * - Crisp white ring
 * - Solid royal Google Blue core dot
 * - Visual elevation feedback when dragged
 */
@Composable
fun GoogleMapsBlueDot(
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "google_blue_dot_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (isDragging) 1.15f else 0.85f,
        targetValue = if (isDragging) 1.45f else 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isDragging) 0.45f else 0.35f,
        targetValue = if (isDragging) 0.20f else 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Pulsing accuracy halo (light blue)
        Box(
            modifier = Modifier
                .size((34 * pulseScale).dp)
                .background(Color(0xFF4285F4).copy(alpha = pulseAlpha), CircleShape)
        )

        // 2. White border ring with elevation
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = if (isDragging) 8.dp else 3.dp,
            modifier = Modifier.size(if (isDragging) 24.dp else 20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 3. Inner solid royal blue dot
                Box(
                    modifier = Modifier
                        .size(if (isDragging) 16.dp else 13.dp)
                        .background(Color(0xFF1A73E8), CircleShape)
                )
            }
        }
    }
}

@Composable
fun PinMarker(
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val r = width * 0.38f
        val centerX = width / 2f
        val centerY = r + 1f

        val path = Path().apply {
            moveTo(centerX - r * 0.8f, centerY + r * 0.4f)
            lineTo(centerX, height)
            lineTo(centerX + r * 0.8f, centerY + r * 0.4f)
            close()
        }
        drawPath(path = path, color = color, style = Fill)
        drawCircle(color = color, radius = r, center = Offset(centerX, centerY))
        drawCircle(color = Color.White, radius = r * 0.42f, center = Offset(centerX, centerY))
    }
}
