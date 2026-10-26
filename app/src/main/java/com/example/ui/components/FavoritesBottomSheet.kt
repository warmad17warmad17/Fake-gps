package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.LocationFavoriteEntity
import com.example.ui.theme.FakeGpsFavoriteRed
import com.example.ui.theme.FakeGpsPrimary
import com.example.widget.WidgetHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesBottomSheet(
    sheetState: SheetState,
    favoritesList: List<LocationFavoriteEntity>,
    onSelectFavorite: (LocationFavoriteEntity) -> Unit,
    onEditFavorite: (LocationFavoriteEntity) -> Unit,
    onDeleteFavorite: (LocationFavoriteEntity) -> Unit,
    onReorderFavorites: (List<LocationFavoriteEntity>) -> Unit = {},
    onDismiss: () -> Unit
) {
    var localList by remember(favoritesList) { mutableStateOf(favoritesList) }
    var draggingItemId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(FakeGpsFavoriteRed, CircleShape)
                )
                Column {
                    Text(
                        text = "Lokasi Favorit (${localList.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Tekan lama & geser nama lokasi ke atas/bawah untuk mengatur urutan",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val context = LocalContext.current

            // Quick Pin Widget to Home Screen Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = FakeGpsPrimary.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(FakeGpsPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = FakeGpsPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Widget Layar Utama",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = FakeGpsPrimary
                        )
                        Text(
                            text = "Tampilkan dan pilih lokasi favorit langsung di Home Screen",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            WidgetHelper.pinWidgetToHomeScreen(context)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("pin_widget_button")
                    ) {
                        Text("Pasang", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (localList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Belum ada lokasi favorit disimpan",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pilih lokasi di peta atau dari riwayat untuk menambah favorit",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(localList, key = { it.id }) { fav ->
                        val isDragging = draggingItemId == fav.id
                        val animatedElevation by animateFloatAsState(
                            targetValue = if (isDragging) 8f else 1f,
                            label = "elevation_${fav.id}"
                        )

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            border = if (isDragging) BorderStroke(1.5.dp, FakeGpsPrimary) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDragging) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 2f else 1f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragOffsetY
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .pointerInput(fav.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingItemId = fav.id
                                            dragOffsetY = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y

                                            val currentIdx = localList.indexOfFirst { it.id == fav.id }
                                            if (currentIdx != -1) {
                                                val stepThreshold = with(density) { 52.dp.toPx() }
                                                if (dragOffsetY > stepThreshold && currentIdx < localList.lastIndex) {
                                                    val mutable = localList.toMutableList()
                                                    val targetIdx = currentIdx + 1
                                                    val item = mutable.removeAt(currentIdx)
                                                    mutable.add(targetIdx, item)
                                                    localList = mutable
                                                    dragOffsetY -= stepThreshold
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else if (dragOffsetY < -stepThreshold && currentIdx > 0) {
                                                    val mutable = localList.toMutableList()
                                                    val targetIdx = currentIdx - 1
                                                    val item = mutable.removeAt(currentIdx)
                                                    mutable.add(targetIdx, item)
                                                    localList = mutable
                                                    dragOffsetY += stepThreshold
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggingItemId = null
                                            dragOffsetY = 0f
                                            onReorderFavorites(localList)
                                        },
                                        onDragCancel = {
                                            draggingItemId = null
                                            dragOffsetY = 0f
                                            localList = favoritesList
                                        }
                                    )
                                }
                                .clickable {
                                    if (draggingItemId == null) {
                                        onSelectFavorite(fav)
                                        onDismiss()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Drag handle indicator
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Tahan untuk menggeser urutan",
                                        tint = if (isDragging) FakeGpsPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )

                                    // Location Pin icon
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = FakeGpsFavoriteRed,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    // Location Name Only (coordinates removed to display more names)
                                    Text(
                                        text = fav.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Edit favorite name
                                    IconButton(
                                        onClick = { onEditFavorite(fav) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("edit_favorite_item_${fav.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Nama",
                                            tint = Color(0xFF00897B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Delete favorite item
                                    IconButton(
                                        onClick = { onDeleteFavorite(fav) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("delete_favorite_item_${fav.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus Favorit",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
