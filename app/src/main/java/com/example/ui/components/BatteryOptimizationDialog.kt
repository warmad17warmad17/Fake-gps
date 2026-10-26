package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.FakeGpsPrimary
import com.example.util.BatteryOptimizationHelper

@Composable
fun BatteryOptimizationDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    // Refresh state when returning to the app after user alters system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isXiaomi = remember { BatteryOptimizationHelper.isXiaomiDevice() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isIgnored) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIgnored) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (isIgnored) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Jalankan di Latar Belakang",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Tanpa Pengoptimalan Baterai",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Current status card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isIgnored) Color(0xFFE8F8F0) else Color(0xFFFFF8E1)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isIgnored) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isIgnored) Color(0xFF00897B) else Color(0xFFF57C00),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isIgnored) "Status: Bebas Pembatasan (Aktif)" else "Status: Dibatasi Penghemat Baterai",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isIgnored) Color(0xFF00695C) else Color(0xFFB78103)
                            )
                            Text(
                                text = if (isIgnored)
                                    "Aplikasi diizinkan berjalan terus di latar belakang tanpa tertutup otomatis."
                                else
                                    "Sistem HP dapat menghentikan Fake GPS saat layar mati jika pengoptimalan belum dimatikan.",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }

                if (!isIgnored) {
                    Button(
                        onClick = {
                            BatteryOptimizationHelper.requestIgnoreBatteryOptimization(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FakeGpsPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Matikan Pengoptimalan Baterai (Sistem)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // OEM Specific Guides (MIUI / HyperOS / Generic)
                Text(
                    text = "Langkah Tambahan Khusus Perangkat (PENTING):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Step 1: Battery Saver -> No restrictions
                Row(verticalAlignment = Alignment.Top) {
                    Text("1.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FakeGpsPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Penghemat Baterai -> Tanpa Pembatasan", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(
                            "Pada info aplikasi, pilih Penghemat Baterai lalu centang 'Tidak ada batasan' (No restrictions).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Step 2: Autostart
                Row(verticalAlignment = Alignment.Top) {
                    Text("2.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FakeGpsPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Mulai Otomatis (Autostart)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(
                            "Aktifkan 'Mulai Otomatis' agar Fake GPS tetap menyala saat HP dinyalakan kembali.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Step 3: Lock App in Recent Apps
                Row(verticalAlignment = Alignment.Top) {
                    Text("3.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FakeGpsPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Kunci Aplikasi di Layar Recent Apps", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(
                            "Buka Recent Apps (tombol kotak bawah), tekan dan tahan Fake GPS, lalu ketuk ikon Gembok agar tidak terbersihkan.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // Shortcut buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            BatteryOptimizationHelper.openAppInfo(context)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Info Aplikasi", fontSize = 12.sp)
                    }

                    if (isXiaomi) {
                        OutlinedButton(
                            onClick = {
                                val opened = BatteryOptimizationHelper.openXiaomiAutostartSettings(context)
                                if (!opened) {
                                    BatteryOptimizationHelper.openAppInfo(context)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Autostart MIUI", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(containerColor = FakeGpsPrimary)
            ) {
                Text("Tutup")
            }
        }
    )
}
