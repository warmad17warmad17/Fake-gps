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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationFavoriteEntity
import com.example.data.model.LocationHistoryEntity
import com.example.ui.theme.FakeGpsAccent
import com.example.ui.theme.FakeGpsFavoriteRed
import com.example.ui.theme.FakeGpsHistoryBlue
import com.example.ui.theme.FakeGpsPrimary
import kotlinx.coroutines.delay
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
    onLocationSelected: (Double, Double) -> Unit,
    onStartMock: () -> Unit,
    onStopMock: () -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onAddHistoryToFavorite: (LocationHistoryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val tileManager = remember { TileManager(coroutineScope) }

    // Map Camera state (Surabaya coordinates matching screenshot)
    var centerLat by remember { mutableDoubleStateOf(selectedLat) }
    var centerLng by remember { mutableDoubleStateOf(selectedLng) }
    var zoomLevel by remember { mutableDoubleStateOf(15.0) }
    var tileRefreshTrigger by remember { mutableIntStateOf(0) }

    // Selected history item for showing Delete & Add to Favorite action popup
    var selectedHistoryItem by remember { mutableStateOf<LocationHistoryEntity?>(null) }

    // Listen to tiles updates
    LaunchedEffect(Unit) {
        tileManager.tileRefreshTrigger.collect {
            tileRefreshTrigger++
        }
    }

    // Keep camera synchronized if selected coordinate changes externally
    LaunchedEffect(selectedLat, selectedLng) {
        val dist = kotlin.math.abs(selectedLat - centerLat) + kotlin.math.abs(selectedLng - centerLng)
        if (dist > 0.05) {
            centerLat = selectedLat
            centerLng = selectedLng
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val centerX = widthPx / 2f
        val centerY = heightPx / 2f

        // Fetch visible Google Maps tiles outside Draw phase
        LaunchedEffect(centerLat, centerLng, zoomLevel, widthPx, heightPx, tileManager.currentTileType) {
            delay(40)
            val zoomInt = zoomLevel.roundToInt().coerceIn(3, 19)
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

            val neededTiles = mutableListOf<Triple<Int, Int, Int>>()
            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    if (ty in 0 until maxTilesAcross) {
                        val normTx = ((tx % maxTilesAcross) + maxTilesAcross) % maxTilesAcross
                        neededTiles.add(Triple(zoomInt, normTx, ty))
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
                        val newZoom = (zoomLevel + (zoom - 1f) * 1.8).coerceIn(3.0, 19.0)
                        zoomLevel = newZoom

                        val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, zoomLevel)
                        val newWorldX = currWorldX - pan.x
                        val newWorldY = currWorldY - pan.y
                        val (newLat, newLon) = MapProjection.worldPixelToLatLon(newWorldX, newWorldY, zoomLevel)
                        centerLat = newLat.coerceIn(-85.0, 85.0)
                        centerLng = ((newLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            selectedHistoryItem = null // Close popup on tap elsewhere
                            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, zoomLevel)
                            val tapWorldX = currWorldX + (tapOffset.x - centerX)
                            val tapWorldY = currWorldY + (tapOffset.y - centerY)
                            val (tapLat, tapLon) = MapProjection.worldPixelToLatLon(tapWorldX, tapWorldY, zoomLevel)
                            onLocationSelected(tapLat, tapLon)
                        },
                        onDoubleTap = {
                            zoomLevel = (zoomLevel + 1.0).coerceAtMost(19.0)
                        }
                    )
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val trigger = tileRefreshTrigger

            val bgColor = if (tileManager.currentTileType == MapTileType.GOOGLE_SATELLITE) Color(0xFF1E262C) else Color(0xFFF2F4F7)
            drawRect(bgColor)

            val zoomInt = zoomLevel.roundToInt().coerceIn(3, 19)
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

            // Draw Google Maps Slippy Tiles
            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    if (ty in 0 until maxTilesAcross) {
                        val normTx = ((tx % maxTilesAcross) + maxTilesAcross) % maxTilesAcross
                        val tileBitmap = tileManager.getCachedTile(zoomInt, normTx, ty)

                        if (tileBitmap != null) {
                            val tileScreenX = (tx * tileSize - minWorldX).toFloat()
                            val tileScreenY = (ty * tileSize - minWorldY).toFloat()
                            drawImage(
                                image = tileBitmap,
                                topLeft = Offset(tileScreenX, tileScreenY)
                            )
                        }
                    }
                }
            }
        }

        // Overlay Markers for History (BLUE pins)
        if (showHistory) {
            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, zoomLevel)
            for (hist in historyList) {
                val (hwX, hwY) = MapProjection.latLonToWorldPixel(hist.latitude, hist.longitude, zoomLevel)
                val sx = centerX + (hwX - currWorldX).toFloat()
                val sy = centerY + (hwY - currWorldY).toFloat()

                if (sx >= -40 && sx <= widthPx + 40 && sy >= -60 && sy <= heightPx + 40) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((sx - 15.dp.toPx()).roundToInt(), (sy - 34.dp.toPx()).roundToInt()) }
                            .clickable {
                                // Select this history pin and open the action popup
                                selectedHistoryItem = hist
                                onLocationSelected(hist.latitude, hist.longitude)
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
            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, zoomLevel)
            val (hwX, hwY) = MapProjection.latLonToWorldPixel(hist.latitude, hist.longitude, zoomLevel)
            val hx = centerX + (hwX - currWorldX).toFloat()
            val hy = centerY + (hwY - currWorldY).toFloat()

            if (hx >= -60 && hx <= widthPx + 60 && hy >= -80 && hy <= heightPx + 60) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (hx - 70.dp.toPx()).roundToInt(),
                                (hy - 110.dp.toPx()).roundToInt()
                            )
                        }
                        .size(width = 140.dp, height = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Floating Action Pill: Delete & Add to Favorite (Circled in red in screenshot)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xEEFFFFFF),
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    modifier = Modifier.size(22.dp)
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
                                    modifier = Modifier.size(22.dp)
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
            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, zoomLevel)
            for (fav in favoritesList) {
                val (fwX, fwY) = MapProjection.latLonToWorldPixel(fav.latitude, fav.longitude, zoomLevel)
                val sx = centerX + (fwX - currWorldX).toFloat()
                val sy = centerY + (fwY - currWorldY).toFloat()

                if (sx >= -40 && sx <= widthPx + 40 && sy >= -60 && sy <= heightPx + 40) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((sx - 15.dp.toPx()).roundToInt(), (sy - 34.dp.toPx()).roundToInt()) }
                            .clickable {
                                onLocationSelected(fav.latitude, fav.longitude)
                            }
                    ) {
                        PinMarker(
                            color = FakeGpsFavoriteRed,
                            size = 30.dp
                        )
                    }
                }
            }
        }

        // Selected / Active Coordinate: Authentic Google Maps Blue Dot
        run {
            val (currWorldX, currWorldY) = MapProjection.latLonToWorldPixel(centerLat, centerLng, zoomLevel)
            val (selWorldX, selWorldY) = MapProjection.latLonToWorldPixel(selectedLat, selectedLng, zoomLevel)
            val selScreenX = centerX + (selWorldX - currWorldX).toFloat()
            val selScreenY = centerY + (selWorldY - currWorldY).toFloat()

            // Google Maps Blue Dot centered exactly at (selScreenX, selScreenY)
            GoogleMapsBlueDot(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (selScreenX - 22.dp.toPx()).roundToInt(),
                            (selScreenY - 22.dp.toPx()).roundToInt()
                        )
                    }
                    .testTag("google_maps_blue_dot")
            )
        }

        // Top Right Controls (Recenter, Layer Switcher, Zoom In/Out)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
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
                onClick = { zoomLevel = (zoomLevel + 1.0).coerceAtMost(19.0) },
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

        // Bottom Left Coordinates HUD Card (Exact style from screenshot)
        Card(
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .testTag("coordinates_hud_card")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Latitude: ",
                        fontSize = 13.sp,
                        color = Color(0xFF212121),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "%.6f".format(selectedLat),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF212121),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Longitude: ",
                        fontSize = 13.sp,
                        color = Color(0xFF212121),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "%.6f".format(selectedLng),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF212121),
                        fontFamily = FontFamily.Monospace
                    )
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
 */
@Composable
fun GoogleMapsBlueDot(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "google_blue_dot_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
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
            shadowElevation = 3.dp,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 3. Inner solid royal blue dot
                Box(
                    modifier = Modifier
                        .size(13.dp)
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
