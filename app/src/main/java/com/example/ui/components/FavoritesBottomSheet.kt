package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    var isReorderMode by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Smooth Auto-scroll during drag when near the top or bottom boundaries of LazyColumn
    LaunchedEffect(draggingItemId) {
        val activeId = draggingItemId ?: return@LaunchedEffect
        while (isActive && draggingItemId == activeId) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val draggedItem = visibleItems.find { it.key == activeId }

            if (draggedItem != null) {
                val itemCenter = draggedItem.offset + (draggedItem.size / 2f) + dragOffsetY
                val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
                val edgeZone = with(density) { 56.dp.toPx() }

                val scrollSpeed = when {
                    itemCenter < viewportStart + edgeZone -> {
                        val dist = (viewportStart + edgeZone - itemCenter).coerceAtLeast(0f)
                        val factor = (dist / edgeZone).coerceIn(0.5f, 3.5f)
                        -12f * factor
                    }
                    itemCenter > viewportEnd - edgeZone -> {
                        val dist = (itemCenter - (viewportEnd - edgeZone)).coerceAtLeast(0f)
                        val factor = (dist / edgeZone).coerceIn(0.5f, 3.5f)
                        12f * factor
                    }
                    else -> 0f
                }

                if (scrollSpeed != 0f) {
                    listState.scrollBy(scrollSpeed)

                    // After scrolling brings hidden items into view, perform swaps if item crossed boundary
                    val currentIdx = localList.indexOfFirst { it.id == activeId }
                    if (currentIdx != -1) {
                        val updatedVisible = listState.layoutInfo.visibleItemsInfo
                        val updatedDragged = updatedVisible.find { it.key == activeId }
                        if (updatedDragged != null) {
                            val currentPos = updatedDragged.offset + (updatedDragged.size / 2f) + dragOffsetY
                            if (scrollSpeed > 0 && currentIdx < localList.lastIndex) {
                                val nextItem = updatedVisible.find { it.index == currentIdx + 1 }
                                if (nextItem != null && currentPos > (nextItem.offset + nextItem.size / 2f)) {
                                    val mutable = localList.toMutableList()
                                    val item = mutable.removeAt(currentIdx)
                                    mutable.add(currentIdx + 1, item)
                                    localList = mutable
                                    dragOffsetY -= nextItem.size
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            } else if (scrollSpeed < 0 && currentIdx > 0) {
                                val prevItem = updatedVisible.find { it.index == currentIdx - 1 }
                                if (prevItem != null && currentPos < (prevItem.offset + prevItem.size / 2f)) {
                                    val mutable = localList.toMutableList()
                                    val item = mutable.removeAt(currentIdx)
                                    mutable.add(currentIdx - 1, item)
                                    localList = mutable
                                    dragOffsetY += prevItem.size
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    }
                }
            }
            delay(16)
        }
    }

    fun handleDragDelta(change: PointerInputChange, dragAmountY: Float, itemId: Long) {
        change.consume()
        dragOffsetY += dragAmountY

        val currentIdx = localList.indexOfFirst { it.id == itemId }
        if (currentIdx == -1) return

        val stepThreshold = with(density) { 34.dp.toPx() }

        if (dragOffsetY > stepThreshold && currentIdx < localList.lastIndex) {
            val mutable = localList.toMutableList()
            val item = mutable.removeAt(currentIdx)
            mutable.add(currentIdx + 1, item)
            localList = mutable
            dragOffsetY -= stepThreshold
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else if (dragOffsetY < -stepThreshold && currentIdx > 0) {
            val mutable = localList.toMutableList()
            val item = mutable.removeAt(currentIdx)
            mutable.add(currentIdx - 1, item)
            localList = mutable
            dragOffsetY += stepThreshold
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun commitDrag() {
        val active = draggingItemId
        draggingItemId = null
        dragOffsetY = 0f
        if (active != null) {
            onReorderFavorites(localList)
        }
    }

    fun moveItem(fromIdx: Int, toIdx: Int) {
        if (fromIdx in localList.indices && toIdx in localList.indices && fromIdx != toIdx) {
            val mutable = localList.toMutableList()
            val item = mutable.removeAt(fromIdx)
            mutable.add(toIdx, item)
            localList = mutable
            onReorderFavorites(mutable)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            coroutineScope.launch {
                listState.animateScrollToItem(toIdx.coerceIn(0, mutable.lastIndex))
            }
        }
    }

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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
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
                            text = if (isReorderMode) "Gunakan tombol panah atau geser nama lokasi" else "Tahan & geser atau buka opsi untuk atur posisi",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (localList.size > 1) {
                    FilterChip(
                        selected = isReorderMode,
                        onClick = { isReorderMode = !isReorderMode },
                        label = {
                            Text(
                                text = if (isReorderMode) "Selesai" else "Atur Posisi",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isReorderMode) Icons.Default.Check else Icons.Default.SwapVert,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FakeGpsPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = FakeGpsPrimary,
                            selectedLeadingIconColor = FakeGpsPrimary
                        ),
                        modifier = Modifier.testTag("toggle_reorder_mode_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(localList, key = { _, fav -> fav.id }) { index, fav ->
                        val isDragging = draggingItemId == fav.id
                        val animatedElevation by animateFloatAsState(
                            targetValue = if (isDragging) 12f else 1f,
                            label = "elevation_${fav.id}"
                        )
                        var showMenu by remember { mutableStateOf(false) }

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            border = if (isDragging) {
                                BorderStroke(2.dp, FakeGpsPrimary)
                            } else if (isReorderMode) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            } else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDragging) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 10f else 1f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragOffsetY
                                        scaleX = 1.03f
                                        scaleY = 1.03f
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
                                            handleDragDelta(change, dragAmount.y, fav.id)
                                        },
                                        onDragEnd = { commitDrag() },
                                        onDragCancel = { commitDrag() }
                                    )
                                }
                                .clickable {
                                    if (draggingItemId == null && !isReorderMode) {
                                        onSelectFavorite(fav)
                                        onDismiss()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Immediate Drag Handle: dragging here starts immediately without waiting
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .pointerInput(fav.id) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingItemId = fav.id
                                                        dragOffsetY = 0f
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        handleDragDelta(change, dragAmount.y, fav.id)
                                                    },
                                                    onDragEnd = { commitDrag() },
                                                    onDragCancel = { commitDrag() }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Pegang untuk menggeser urutan",
                                            tint = if (isDragging) FakeGpsPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // Position Badge or Pin Icon
                                    if (isReorderMode) {
                                        Box(
                                            modifier = Modifier
                                                .background(FakeGpsPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = FakeGpsPrimary
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = FakeGpsFavoriteRed,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // Location Name Only
                                    Text(
                                        text = fav.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isReorderMode) {
                                        // Quick Move Up button
                                        IconButton(
                                            onClick = { moveItem(index, index - 1) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Pindah Ke Atas",
                                                tint = if (index > 0) FakeGpsPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Quick Move Down button
                                        IconButton(
                                            onClick = { moveItem(index, index + 1) },
                                            enabled = index < localList.lastIndex,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Pindah Ke Bawah",
                                                tint = if (index < localList.lastIndex) FakeGpsPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    } else {
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

                                    // Context Menu for Move Actions
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Opsi Urutan",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Pindah ke Paling Atas") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.VerticalAlignTop, contentDescription = null, tint = FakeGpsPrimary)
                                                },
                                                enabled = index > 0,
                                                onClick = {
                                                    showMenu = false
                                                    moveItem(index, 0)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Geser ke Atas") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = null)
                                                },
                                                enabled = index > 0,
                                                onClick = {
                                                    showMenu = false
                                                    moveItem(index, index - 1)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Geser ke Bawah") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.ArrowDownward, contentDescription = null)
                                                },
                                                enabled = index < localList.lastIndex,
                                                onClick = {
                                                    showMenu = false
                                                    moveItem(index, index + 1)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Pindah ke Paling Bawah") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.VerticalAlignBottom, contentDescription = null, tint = FakeGpsPrimary)
                                                },
                                                enabled = index < localList.lastIndex,
                                                onClick = {
                                                    showMenu = false
                                                    moveItem(index, localList.lastIndex)
                                                }
                                            )
                                        }
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
