package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FakeGpsPrimary

@Composable
fun AddFavoriteDialog(
    latitude: Double,
    longitude: Double,
    initialTitle: String = "",
    initialDescription: String = "",
    onConfirm: (title: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle.ifBlank { "Location" }) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Favorite Data",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF212121)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("favorite_title_input")
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("favorite_description_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Koordinat: %.6f, %.6f".format(latitude, longitude),
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color(0xFF616161)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(title.ifBlank { "Location" }, description)
                },
                modifier = Modifier.testTag("save_favorite_confirm_button")
            ) {
                Text(
                    text = "OKE",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0097A7),
                    fontSize = 15.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("save_favorite_cancel_button")
            ) {
                Text(
                    text = "BATAL",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0097A7),
                    fontSize = 15.sp
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(4.dp)
    )
}
