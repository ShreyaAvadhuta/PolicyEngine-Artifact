package io.github.jqssun.gpssetter.xposed

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import io.github.jqssun.gpssetter.BuildConfig

/**
 * Exposes PolicyEngine2 settings to modules running inside other app
 * sandboxes (rootless LSPatch deployment), where XSharedPreferences
 * cannot cross the app-data boundary.
 */
class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val prefs = ctx.getSharedPreferences(
            "${BuildConfig.APPLICATION_ID}_prefs",
            Context.MODE_PRIVATE
        )
        val cursor = MatrixCursor(arrayOf("key", "value"))
        cursor.addRow(arrayOf("start", if (prefs.getBoolean("start", false)) "1" else "0"))
        cursor.addRow(arrayOf("latitude", prefs.getFloat("latitude", 40.7128F).toString()))
        cursor.addRow(arrayOf("longitude", prefs.getFloat("longitude", -74.0060F).toString()))
        cursor.addRow(arrayOf("system_hooked", if (prefs.getBoolean("system_hooked", false)) "1" else "0"))
        cursor.addRow(arrayOf("random_position", if (prefs.getBoolean("random_position", false)) "1" else "0"))
        cursor.addRow(arrayOf("accuracy_level", prefs.getString("accuracy_level", "10") ?: "10"))
        cursor.addRow(arrayOf("trajectory_mode", prefs.getString("trajectory_mode", "WALKING") ?: "WALKING"))
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}