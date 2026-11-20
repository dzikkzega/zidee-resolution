package com.zidee.ress

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal const val RELEASE_URL = "https://github.com/dzikkzega/zidee-resolution/releases/latest"
private const val UPDATE_URL = "https://dzikkzega.github.io/zidee-resolution-updates/latest.json"
internal const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L

internal data class UpdateVersion(val code: Int, val name: String)

internal fun shouldCheck(
    now: Long,
    lastCheck: Long,
): Boolean = lastCheck <= 0 || now < lastCheck || now - lastCheck >= CHECK_INTERVAL

internal fun availableUpdate(
    code: Int,
    name: String,
    current: Int,
    dismissed: Int,
): UpdateVersion? =
    if (code > current && code != dismissed && name.isNotBlank() && name.length <= 100) {
        UpdateVersion(code, name)
    } else {
        null
    }

internal suspend fun checkForUpdate(context: Context): UpdateVersion? {
    val preferences = context.getSharedPreferences("update_check", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    if (!shouldCheck(now, preferences.getLong("last_check", 0))) return null
    return withContext(Dispatchers.IO) {
        try {
            val connection = URL(UPDATE_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val text = connection.inputStream.bufferedReader().use { it.readTextLimited() } ?: return@withContext null
                val json = JSONObject(text)
                val code = json.get("versionCode")
                val name = json.get("versionName")
                if (code !is Int || code <= 0 || name !is String || name.isBlank() || name.length > 100) {
                    return@withContext null
                }
                preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
                availableUpdate(code, name, BuildConfig.VERSION_CODE, preferences.getInt("dismissed", 0))
            } finally {
                connection.disconnect()
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            null
        }
    }
}

private fun java.io.Reader.readTextLimited(): String? {
    val buffer = CharArray(4097)
    var size = 0
    while (size < buffer.size) {
        val count = read(buffer, size, buffer.size - size)
        if (count < 0) break
        size += count
    }
    return if (size > 4096) null else String(buffer, 0, size)
}
