package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FakeGpsPrimary
import com.example.util.GpsLockerBridge

@Composable
fun GpsLockerIntegrationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isInstalled = GpsLockerBridge.isGpsLockerInstalled(context)
    val isWaitingCooldown by GpsLockerBridge.isWaitingCooldown.collectAsState()
    val countdownRemaining by GpsLockerBridge.cooldownSecondsRemaining.collectAsState()
    val lastStatus by GpsLockerBridge.lastStatusMessage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(FakeGpsPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = FakeGpsPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sinkronisasi GPS Locker",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "by SilentLexx UA",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Status installation badge
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isInstalled) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isInstalled) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isInstalled) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isInstalled) "GPS Locker Terdeteksi" else "GPS Locker Belum Terpasang",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isInstalled) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                            Text(
                                text = if (isInstalled)
                                    "Aplikasi otomatis mengontrol lock/unlock bergantian."
                                else
                                    "Jika Anda menginstal GPS Locker, sinkronisasi otomatis akan langsung aktif.",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }

                if (isWaitingCooldown) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF1976D2),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Menstabilkan GPS: $countdownRemaining detik tersisa sebelum GPS Locker mengunci kembali.",
                                fontSize = 11.sp,
                                color = Color(0xFF0D47A1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Cara Kerja Pergantian Otomatis:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                StepRow(
                    number = "1",
                    title = "Saat Mock Lokasi Aktif",
                    desc = "Aplikasi secara otomatis mengirimkan perintah berhenti/pause ke GPS Locker agar tidak terjadi bentrok penerimaan satelit."
                )

                Spacer(modifier = Modifier.height(6.dp))

                StepRow(
                    number = "2",
                    title = "Saat Mock Lokasi Dimatikan",
                    desc = "Test provider dilepas secara bersih dan chip GPS fisik di-kickstart kembali agar terhubung ke satelit asli."
                )

                Spacer(modifier = Modifier.height(6.dp))

                StepRow(
                    number = "3",
                    title = "Jeda 10 Detik Pasca Non-Aktif",
                    desc = "Setelah mock non-aktif lebih dari 10 detik, sinyal perintah lock dikirimkan ke GPS Locker untuk mengunci sinyal satelit asli perangkat."
                )
            }
        },
        confirmButton = {
            Row {
                if (isInstalled) {
                    OutlinedButton(
                        onClick = {
                            GpsLockerBridge.openGpsLockerApp(context)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buka GPS Locker")
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = FakeGpsPrimary)
                ) {
                    Text("Mengerti")
                }
            }
        }
    )
}

@Composable
private fun StepRow(number: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(FakeGpsPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FakeGpsPrimary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = desc, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
        }
    }
}
