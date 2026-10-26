package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.LocationFavoriteEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupRestoreHelper {

    fun generateDefaultBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "fake_gps_favorit_${dateFormat.format(Date())}.json"
    }

    /**
     * Serializes favorite entities into clean, formatted JSON.
     */
    fun createBackupJson(favorites: List<LocationFavoriteEntity>): String {
        val root = JSONObject()
        root.put("app", "Fake GPS")
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        root.put("date", dateFormat.format(Date()))
        root.put("count", favorites.size)

        val array = JSONArray()
        for (fav in favorites) {
            val item = JSONObject()
            item.put("name", fav.name)
            item.put("latitude", fav.latitude)
            item.put("longitude", fav.longitude)
            item.put("description", fav.description)
            item.put("timestamp", fav.timestamp)
            item.put("sortOrder", fav.sortOrder)
            array.put(item)
        }
        root.put("favorites", array)

        return root.toString(2)
    }

    /**
     * Parses a JSON string and extracts favorite locations.
     * Supports both envelope format { favorites: [...] } and direct array [...].
     */
    fun parseBackupJson(jsonString: String): List<LocationFavoriteEntity> {
        val result = mutableListOf<LocationFavoriteEntity>()
        val trimmed = jsonString.trim()

        val jsonArray: JSONArray = if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            when {
                obj.has("favorites") -> obj.getJSONArray("favorites")
                obj.has("data") -> obj.getJSONArray("data")
                obj.has("locations") -> obj.getJSONArray("locations")
                else -> JSONArray()
            }
        } else if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            return emptyList()
        }

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val name = item.optString("name", item.optString("title", "Lokasi Favorit"))
            val lat = item.optDouble("latitude", item.optDouble("lat", Double.NaN))
            val lng = item.optDouble("longitude", item.optDouble("lng", item.optDouble("lon", Double.NaN)))
            val desc = item.optString("description", item.optString("desc", ""))
            val timestamp = item.optLong("timestamp", System.currentTimeMillis())
            val sortOrder = item.optInt("sortOrder", item.optInt("order", i))

            if (!lat.isNaN() && !lng.isNaN()) {
                result.add(
                    LocationFavoriteEntity(
                        id = 0,
                        name = name.ifBlank { "Lokasi Favorit ${i + 1}" },
                        latitude = lat,
                        longitude = lng,
                        description = desc,
                        timestamp = timestamp,
                        sortOrder = sortOrder
                    )
                )
            }
        }
        return result
    }

    /**
     * Writes backup JSON content to a storage Uri via ContentResolver.
     */
    fun writeBackupToUri(context: Context, uri: Uri, jsonString: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads text content from a storage Uri via ContentResolver.
     */
    fun readBackupFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates a Share Intent so the user can easily send the backup file
     * directly to another device via WhatsApp, Telegram, Drive, Bluetooth, Email, etc.
     */
    fun createShareIntent(context: Context, favorites: List<LocationFavoriteEntity>): Intent? {
        return try {
            val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val fileName = generateDefaultBackupFileName()
            val backupFile = File(backupDir, fileName)
            val jsonContent = createBackupJson(favorites)
            backupFile.writeText(jsonContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Backup Lokasi Favorit Fake GPS")
                putExtra(Intent.EXTRA_TEXT, "File cadangan lokasi favorit Fake GPS (${favorites.size} lokasi). Simpan file ini dan gunakan menu Restore di Fake GPS pada perangkat baru.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(intent, "Kirim Cadangan Lokasi Favorit")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
