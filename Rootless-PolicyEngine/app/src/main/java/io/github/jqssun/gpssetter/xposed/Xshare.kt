package io.github.jqssun.gpssetter.xposed

import android.app.AndroidAppHelper
import android.net.Uri
import de.robv.android.xposed.XSharedPreferences
import io.github.jqssun.gpssetter.BuildConfig

class Xshare {
    private var xPref: XSharedPreferences? = null
    private fun pref(): XSharedPreferences {
        xPref = XSharedPreferences(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}_prefs")
        return xPref as XSharedPreferences
    }

    // ===== ROOTLESS CONFIG BRIDGE — DELETE THIS BLOCK TO REVERT =====
    companion object {
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.config"
        private var cache: Map<String, String>? = null
        private var cacheTime: Long = 0L
        private const val CACHE_MS = 1000L
    }

    private fun viaProvider(): Map<String, String>? {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheTime < CACHE_MS) return it }
        return try {
            val ctx = AndroidAppHelper.currentApplication() ?: return null
            val cursor = ctx.contentResolver.query(
                Uri.parse("content://$AUTHORITY/prefs"), null, null, null, null
            ) ?: return null
            val map = HashMap<String, String>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    map[c.getString(0)] = c.getString(1)
                }
            }
            if (map.isEmpty()) return null
            cache = map
            cacheTime = now
            map
        } catch (e: Exception) {
            null
        }
    }
    // ===== END ROOTLESS CONFIG BRIDGE =====

    val isStarted: Boolean
        // ORIGINAL: get() = pref().getBoolean("start", false)
        get() = viaProvider()?.get("start")?.let { it == "1" }
            ?: pref().getBoolean("start", false)

    val getLat: Double
        // ORIGINAL: get() = pref().getFloat("latitude", 45.0000000.toFloat()).toDouble()
        get() = viaProvider()?.get("latitude")?.toDoubleOrNull()
            ?: pref().getFloat("latitude", 45.0000000.toFloat()).toDouble()

    val getLng: Double
        // ORIGINAL: get() = pref().getFloat("longitude", 0.0000000.toFloat()).toDouble()
        get() = viaProvider()?.get("longitude")?.toDoubleOrNull()
            ?: pref().getFloat("longitude", 0.0000000.toFloat()).toDouble()

    val isHookedSystem: Boolean
        // ORIGINAL: get() = pref().getBoolean("system_hooked", true)
        get() = viaProvider()?.get("system_hooked")?.let { it == "1" }
            ?: pref().getBoolean("system_hooked", true)

    val isRandomPosition: Boolean
        // ORIGINAL: get() = pref().getBoolean("random_position", false)
        get() = viaProvider()?.get("random_position")?.let { it == "1" }
            ?: pref().getBoolean("random_position", false)

    val accuracy: String?
        // ORIGINAL: get() = pref().getString("accuracy_level", "10")
        get() = viaProvider()?.get("accuracy_level")
            ?: pref().getString("accuracy_level", "10")

    val trajectoryMode: String
        // ORIGINAL: get() = pref().getString("trajectory_mode", "WALKING") ?: "WALKING"
        get() = viaProvider()?.get("trajectory_mode")
            ?: pref().getString("trajectory_mode", "WALKING") ?: "WALKING"

    val reload = pref().reload()
}



//
//package io.github.jqssun.gpssetter.xposed
//
//import de.robv.android.xposed.XSharedPreferences
//import io.github.jqssun.gpssetter.BuildConfig
//
//class Xshare {
//
//    private var xPref: XSharedPreferences? = null
//
//    private fun pref(): XSharedPreferences {
//        xPref = XSharedPreferences(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}_prefs")
//        return xPref as XSharedPreferences
//    }
//
//    val isStarted: Boolean
//        get() = pref().getBoolean("start", false)
//
//    val getLat: Double
//        get() = pref().getFloat("latitude", 45.0000000.toFloat()).toDouble()
//
//    val getLng: Double
//        get() = pref().getFloat("longitude", 0.0000000.toFloat()).toDouble()
//
//    val isHookedSystem: Boolean
//        get() = pref().getBoolean("system_hooked", true)
//
//    val isRandomPosition: Boolean
//        get() = pref().getBoolean("random_position", false)
//
//    val accuracy: String?
//        get() = pref().getString("accuracy_level", "10")
//
//    val trajectoryMode: String
//        get() = pref().getString("trajectory_mode", "WALKING") ?: "WALKING"
//
//    val reload = pref().reload()
//}