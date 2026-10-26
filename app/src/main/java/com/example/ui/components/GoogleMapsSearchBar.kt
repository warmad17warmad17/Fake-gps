package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SearchResultItem
import com.example.data.repository.AddressSearchManager
import com.example.ui.map.MapTileType
import com.example.ui.theme.FakeGpsAccent
import com.example.ui.theme.FakeGpsFavoriteRed
import com.example.ui.theme.FakeGpsPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GoogleMapsSearchBar(
    onMenuClick: () -> Unit,
    onLocationSelected: (Double, Double, String) -> Unit,
    onSearchCoordinateClick: () -> Unit,
    onShowFavorites: () -> Unit,
    onShowHistory: () -> Unit,
    onToggleLayer: () -> Unit,
    currentTileType: MapTileType,
    isMocking: Boolean,
    onStartMock: () -> Unit,
    onStopMock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val searchManager = remember { AddressSearchManager() }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isDropdownOpen by remember { mutableStateOf(false) }

    fun performSearch(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 2) {
            searchResults = emptyList()
            isSearching = false
            isDropdownOpen = false
            return
        }
        searchJob = coroutineScope.launch {
            delay(300)
            isSearching = true
            val results = searchManager.searchAddress(q)
            searchResults = results
            isSearching = false
            isDropdownOpen = results.isNotEmpty()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // 1. Google Maps Authentic Floating Search Bar
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Menu Button (Hamburger)
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(40.dp).testTag("search_bar_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Buka Menu",
                        tint = Color(0xFF424242),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Google Maps Pin icon
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = FakeGpsFavoriteRed,
                    modifier = Modifier.size(20.dp).padding(start = 2.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Text Input field
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Telusuri di sini",
                            color = Color(0xFF757575),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            performSearch(it)
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFF212121),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                performSearch(searchQuery)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("address_search_input")
                    )
                }

                // Loading Indicator / Clear Button
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(2.dp),
                        strokeWidth = 2.dp,
                        color = FakeGpsPrimary
                    )
                } else if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            searchResults = emptyList()
                            isDropdownOpen = false
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Hapus Pencarian",
                            tint = Color(0xFF757575),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { performSearch(searchQuery) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari Alamat",
                            tint = Color(0xFF757575),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Quick Play / Stop indicator in search bar
                IconButton(
                    onClick = {
                        if (isMocking) onStopMock() else onStartMock()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isMocking) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                            CircleShape
                        )
                        .testTag("search_bar_quick_mock_button")
                ) {
                    Icon(
                        imageVector = if (isMocking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isMocking) "Stop Mock" else "Start Mock",
                        tint = if (isMocking) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Search Autocomplete Results Dropdown
        AnimatedVisibility(
            visible = isDropdownOpen && searchResults.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(searchResults) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLocationSelected(item.latitude, item.longitude, item.name)
                                    searchQuery = item.name
                                    isDropdownOpen = false
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFFF0F4F8), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = FakeGpsPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF212121),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.displayName,
                                    fontSize = 12.sp,
                                    color = Color(0xFF757575),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                    }
                }
            }
        }

        // 3. Quick Filter Chips matching Google Maps UI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Favorit Chip
            FilterChip(
                selected = false,
                onClick = onShowFavorites,
                label = { Text("Favorit", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                leadingIcon = {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = FakeGpsFavoriteRed, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(18.dp),
                colors = FilterChipDefaults.filterChipColors(containerColor = Color.White),
                elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
                modifier = Modifier.testTag("quick_chip_favorites")
            )

            // Riwayat Chip
            FilterChip(
                selected = false,
                onClick = onShowHistory,
                label = { Text("Riwayat", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                leadingIcon = {
                    Icon(Icons.Default.History, contentDescription = null, tint = FakeGpsPrimary, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(18.dp),
                colors = FilterChipDefaults.filterChipColors(containerColor = Color.White),
                elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
                modifier = Modifier.testTag("quick_chip_history")
            )

            // Lapisan Peta Chip
            FilterChip(
                selected = false,
                onClick = onToggleLayer,
                label = {
                    val label = when (currentTileType) {
                        MapTileType.GOOGLE_ROADMAP -> "Peta Jalan"
                        MapTileType.GOOGLE_SATELLITE -> "Satelit"
                        MapTileType.GOOGLE_TERRAIN -> "Medan"
                    }
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                },
                leadingIcon = {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF00897B), modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(18.dp),
                colors = FilterChipDefaults.filterChipColors(containerColor = Color.White),
                elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
                modifier = Modifier.testTag("quick_chip_layer")
            )

            // Input Koordinat Chip
            FilterChip(
                selected = false,
                onClick = onSearchCoordinateClick,
                label = { Text("Input Koordinat", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                leadingIcon = {
                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = FakeGpsAccent, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(18.dp),
                colors = FilterChipDefaults.filterChipColors(containerColor = Color.White),
                elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
                modifier = Modifier.testTag("quick_chip_coords")
            )
        }
    }
}
