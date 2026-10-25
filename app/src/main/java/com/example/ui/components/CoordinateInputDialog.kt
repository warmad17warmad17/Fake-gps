package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CityPreset(val name: String, val lat: Double, val lon: Double)

val popularPresets = listOf(
    CityPreset("Surabaya (Tol Banyu Urip)", -7.253427, 112.707224),
    CityPreset("Jakarta (Monas)", -6.175392, 106.827153),
    CityPreset("Jakarta (Bundaran HI)", -6.195046, 106.823024),
    CityPreset("Bandung (Gedung Sate)", -6.902484, 107.618810),
    CityPreset("Yogyakarta (Malioboro)", -7.792618, 110.365842),
    CityPreset("Bali (Kuta)", -8.718501, 115.168602),
    CityPreset("Medan (Merdeka Walk)", 3.590209, 98.678456)
)

@Composable
fun CoordinateInputDialog(
    currentLat: Double,
    currentLng: Double,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var latText by remember { mutableStateOf(currentLat.toString()) }
    var lngText by remember { mutableStateOf(currentLng.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Masukkan Koordinat GPS",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Ketik koordinat Latitude dan Longitude secara manual atau pilih lokasi populer di bawah:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Presets chip list
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    items(popularPresets) { preset ->
                        FilterChip(
                            selected = (latText == preset.lat.toString() && lngText == preset.lon.toString()),
                            onClick = {
                                latText = preset.lat.toString()
                                lngText = preset.lon.toString()
                                errorMessage = null
                            },
                            label = { Text(preset.name, fontSize = 12.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        errorMessage = null
                    },
                    label = { Text("Latitude") },
                    placeholder = { Text("-7.253427") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_latitude_field")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lngText,
                    onValueChange = {
                        lngText = it
                        errorMessage = null
                    },
                    label = { Text("Longitude") },
                    placeholder = { Text("112.707224") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_longitude_field")
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lon = lngText.toDoubleOrNull()
                    if (lat == null || lat < -90.0 || lat > 90.0) {
                        errorMessage = "Latitude tidak valid (antara -90 dan 90)"
                        return@Button
                    }
                    if (lon == null || lon < -180.0 || lon > 180.0) {
                        errorMessage = "Longitude tidak valid (antara -180 dan 180)"
                        return@Button
                    }
                    onConfirm(lat, lon)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_coordinates_button")
            ) {
                Text("Pilih Koordinat")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_coordinates_button")
            ) {
                Text("Batal")
            }
        }
    )
}
