package io.github.jqssun.gpssetter.xposed

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellIdentityLte
import android.telephony.CellSignalStrengthLte
import java.util.concurrent.Executor
import android.telephony.TelephonyManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.jqssun.gpssetter.BuildConfig
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import java.util.ArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object LocationHook {

    private const val MAPS_API_KEY = "YOUR_GOOGLE_MAPS_API_KEY"

    private val FALLBACK_TRAJECTORY = arrayOf(
        doubleArrayOf(39.9125000, 116.4035000),
        doubleArrayOf(39.9130000, 116.4042000),
        doubleArrayOf(39.9136000, 116.4051000),
        doubleArrayOf(39.9140000, 116.4058000),
        doubleArrayOf(39.9138000, 116.4065000),
        doubleArrayOf(39.9132000, 116.4060000),
        doubleArrayOf(39.9126000, 116.4050000)
    )


    private var trajectoryPoints: Array<DoubleArray> = FALLBACK_TRAJECTORY
    private var trajectoryLoaded = false

    private const val SPOOF_SSID = "SWATCH-2.4G"
    private const val SPOOF_BSSID = "00:1F:7A:22:EB:1D"

    private data class FakeAP(
        val bssid: String,
        val ssid: String,
        val level: Int,
        val frequency: Int
    )

    private val FAKE_APS = listOf(
        FakeAP("00:1F:7A:22:EB:1D", "SWATCH-2.4G",    -62, 2447),
        FakeAP("00:1F:7A:1D:C1:F7", "xda2.4G",         -65, 2437),
        FakeAP("00:1F:7A:1D:BC:FD", "BLA_Store-2.4G",  -68, 2412),
        FakeAP("00:1C:C2:2F:88:18", "AP-8818",          -71, 2422),
        FakeAP("00:0F:60:0C:CA:AB", "dftc-s",           -74, 2437),
    )


    private data class FakeCell(
        val mcc: Int,
        val mnc: Int,
        val tac: Int,
        val ci: Int,
        val dbm: Int,
        val rssi: Int,
        val registered: Boolean
    )

    private val FAKE_CELLS = listOf(
        FakeCell(460, 0, 4183, 127238839, -65, 18, true),
        FakeCell(460, 0, 4183, 127238838, -71, 14, false),
        FakeCell(460, 0, 4183, 90401218,  -74, 12, false),
        FakeCell(460, 0, 4183, 90401219,  -78, 11, false),
        FakeCell(460, 0, 4183, 18694458,  -81, 10, false),
        FakeCell(460, 0, 4183, 90401220,  -83, 9,  false),
        FakeCell(460, 0, 4183, 18694457,  -86, 8,  false),
    )

    private var trajectoryIndex = 0
    var newlat: Double = FALLBACK_TRAJECTORY[0][0]
    var newlng: Double = FALLBACK_TRAJECTORY[0][1]
    private var currentBearing: Float = 90.0f
    private var elevationProfile: Array<Double> = arrayOf(
        43.2, 43.8, 44.1, 44.5, 45.0, 45.3, 45.8
    )

    private var currentElevation: Double = 45.0
    private var isPaused: Boolean = false
    private var pauseCountdown: Int = 0
    private val random = java.util.Random()

    private var accuracy: Float = 10.0f
    private val settings = Xshare()
    private var mLastUpdated: Long = 0

    private val ignorePkg = ArrayList<String>().apply {
        add("com.android.location.fused")
        add(BuildConfig.APPLICATION_ID)
    }

    private val context: Context by lazy {
        AndroidAppHelper.currentApplication() as Context
    }

    private var scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor()
    private var schedulerStarted = false
    private var currentMode: String = "WALKING"

    private val currentSpeed: Float
        get() = when (settings.trajectoryMode) {
            "CYCLING" -> 4.0f
            "RUNNING" -> 3.0f
            else      -> 1.4f
        }

    private fun advancementInterval(fromIdx: Int): Long {
        val toIdx = (fromIdx + 1) % trajectoryPoints.size
        val a = trajectoryPoints[fromIdx]
        val b = trajectoryPoints[toIdx]
        val meters = haversineMeters(a[0], a[1], b[0], b[1])
        val speedMps = currentSpeed.toDouble().coerceAtLeast(0.1)
        return ((meters / speedMps) * 1000).toLong().coerceIn(500L, 15000L)
    }


    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
    private fun calculateBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = Math.sin(dLon) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon)
        val bearing = Math.toDegrees(Math.atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }

    private fun decodePolyline(encoded: String): List<DoubleArray> {
        val poly = ArrayList<DoubleArray>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dLat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dLng
            poly.add(doubleArrayOf(lat / 1e5, lng / 1e5))
        }
        return poly
    }

    private fun fetchBeijingTrajectory() {
        Thread {
            try {
                val origin = "39.9125000,116.4035000"
                val destination = "39.9138000,116.4065000"
                val urlStr = "https://maps.googleapis.com/maps/api/directions/json" +
                        "?origin=$origin" +
                        "&destination=$destination" +
                        "&mode=walking" +
                        "&key=$MAPS_API_KEY"

                val response = URL(urlStr).openStream()
                    .bufferedReader()
                    .use { it.readText() }

                val json = JSONObject(response)
                val status = json.getString("status")

                if (status == "OK") {
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val polyline = routes.getJSONObject(0)
                            .getJSONObject("overview_polyline")
                            .getString("points")
                        val decoded = decodePolyline(polyline)
                        if (decoded.size > 2) {
                            trajectoryPoints = decoded.toTypedArray()
                            trajectoryLoaded = true
                            // Interpolate elevation across dynamic route points
                            val base = arrayOf(43.2, 43.8, 44.1, 44.5, 45.0, 45.3, 45.8)
                            elevationProfile = Array(trajectoryPoints.size) { i ->
                                val ratio = i.toDouble() / maxOf(trajectoryPoints.size - 1, 1)
                                val idx = (ratio * (base.size - 1)).toInt().coerceIn(0, base.size - 2)
                                val blend = (ratio * (base.size - 1)) - idx
                                base[idx] * (1 - blend) + base[idx + 1] * blend
                            }
                            XposedBridge.log("PE2: Google Maps trajectory loaded — ${decoded.size} waypoints")

                        }
                    }
                } else {
                    XposedBridge.log("PE2: Google Maps API status=$status using fallback")
                }
            } catch (e: Exception) {
                XposedBridge.log("PE2: trajectory fetch failed: $e using fallback")
            }
        }.start()
    }

    private fun fetchElevationProfile(points: Array<DoubleArray>) {
        Thread {
            try {
                // Take max 50 points to avoid URL length limit
                val maxPoints = minOf(points.size, 50)
                val step = if (points.size > 50) points.size / 50 else 1
                val sampledPoints = (0 until maxPoints).map { i ->
                    points[minOf(i * step, points.size - 1)]
                }

                val locations = sampledPoints.joinToString("|") {
                    "${it[0]},${it[1]}"
                }
                val urlStr = "https://maps.googleapis.com/maps/api/elevation/json" +
                        "?locations=${locations}" +
                        "&key=$MAPS_API_KEY"

                XposedBridge.log("PE2: fetching elevation for ${sampledPoints.size} points")

                val connection = URL(urlStr).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val response = connection.inputStream.bufferedReader().use { it.readText() }

                XposedBridge.log("PE2: elevation response received length=${response.length}")

                val json = JSONObject(response)
                val status = json.getString("status")

                XposedBridge.log("PE2: elevation API status=$status")

                if (status == "OK") {
                    val results = json.getJSONArray("results")
                    // Interpolate back to full trajectory size
                    val elevations = Array(points.size) { i ->
                        val sampledIdx = minOf(i / step, results.length() - 1)
                        results.getJSONObject(sampledIdx).getDouble("elevation")
                    }
                    elevationProfile = elevations
                    val minElev = elevations.min() ?: 45.0
                    val maxElev = elevations.max() ?: 45.0
                    XposedBridge.log("PE2: elevation profile loaded ${elevations.size} points range=${
                        "%.1f".format(minElev)}m to ${"%.1f".format(maxElev)}m")
                } else {
                    XposedBridge.log("PE2: elevation API failed status=$status")
                }
            } catch (e: Exception) {
                XposedBridge.log("PE2: elevation fetch exception: ${e.message}")
            }
        }.start()
    }

    private fun startTrajectoryScheduler() {
        if (schedulerStarted) return
        schedulerStarted = true
        currentMode = settings.trajectoryMode
        scheduler.scheduleAtFixedRate({
            val newMode = settings.trajectoryMode
            if (newMode != currentMode) {
                currentMode = newMode
                schedulerStarted = false
                scheduler.shutdown()
                scheduler = Executors.newSingleThreadScheduledExecutor()
                startTrajectoryScheduler()
                return@scheduleAtFixedRate
            }
            // Handle pause countdown
            if (isPaused) {
                pauseCountdown--
                if (pauseCountdown <= 0) {
                    isPaused = false
                    XposedBridge.log("PE2: pause ended resuming trajectory")
                } else {
                    XposedBridge.log("PE2: paused at waypoint $trajectoryIndex countdown=$pauseCountdown")
                    return@scheduleAtFixedRate
                }
            }

            trajectoryIndex = (trajectoryIndex + 1) % trajectoryPoints.size
            val point = trajectoryPoints[trajectoryIndex]
            newlat = point[0]
            newlng = point[1]

// Update elevation from profile
            currentElevation = if (trajectoryIndex < elevationProfile.size) {
                elevationProfile[trajectoryIndex]
            } else {
                45.0
            }

// Dynamic bearing to next waypoint
            val nextIndex = (trajectoryIndex + 1) % trajectoryPoints.size
            val nextPoint = trajectoryPoints[nextIndex]
            currentBearing = calculateBearing(newlat, newlng, nextPoint[0], nextPoint[1])

// Random pause at waypoints — 15% chance, simulates traffic light or crossing
            if (!isPaused && random.nextInt(100) < 15) {
                isPaused = true
                pauseCountdown = (3 + random.nextInt(5)) // 3-7 intervals = ~6-14 seconds at walking pace
                XposedBridge.log("PE2: pause triggered at waypoint $trajectoryIndex for $pauseCountdown intervals")
            }

            mLastUpdated = System.currentTimeMillis()
            XposedBridge.log("PE2: traj → idx=$trajectoryIndex lat=$newlat lng=$newlng alt=$currentElevation bearing=$currentBearing mode=$currentMode")

        }, advancementInterval(trajectoryIndex), advancementInterval(trajectoryIndex), TimeUnit.MILLISECONDS)
    }

    private fun updateLocation() {
        try {
            mLastUpdated = System.currentTimeMillis()
            accuracy = try {
                settings.accuracy!!.toFloat()
            } catch (e: Exception) {
                10.0f
            }
        } catch (e: Exception) {
            Timber.tag("PE2").e(e, "updateLocation failed for %s", context.packageName)
        }
    }

    @SuppressLint("NewApi")
    private fun buildSpoofedLocation(originLocation: Location?): Location {
        val location: Location
        if (originLocation == null) {
            location = Location(LocationManager.GPS_PROVIDER)
            location.time = System.currentTimeMillis() - 300
            location.elapsedRealtimeNanos = System.nanoTime() - 300_000_000L
        } else {
            location = Location(originLocation.provider)
            location.time = originLocation.time
            location.bearing = originLocation.bearing
            location.bearingAccuracyDegrees = originLocation.bearingAccuracyDegrees
            location.elapsedRealtimeNanos = originLocation.elapsedRealtimeNanos
            location.verticalAccuracyMeters = originLocation.verticalAccuracyMeters
        }
        location.latitude = newlat
        location.longitude = newlng
        location.altitude = currentElevation

// Add small random variance to speed — makes movement look human not robotic
// Walking: 1.2-1.6, Cycling: 3.6-4.4, Running: 2.7-3.3
        val speedVariance = (random.nextFloat() - 0.5f) * 0.4f * currentSpeed / 1.4f
        location.speed = if (isPaused) 0.0f else (currentSpeed + speedVariance).coerceAtLeast(0.1f)
        location.accuracy = accuracy
        location.speedAccuracyMetersPerSecond = 0.5f
        location.bearing = currentBearing

        try {
            HiddenApiBypass.invoke(
                location::class.java, location, "setIsFromMockProvider", false
            )
        } catch (e: Exception) {
            XposedBridge.log("PE2: mock flag hide failed: $e")
        }
        return location
    }

    private fun setIntField(obj: Any, clazz: Class<*>, fieldName: String, value: Int) {
        try {
            val f = clazz.getDeclaredField(fieldName)
            f.isAccessible = true
            f.setInt(obj, value)
        } catch (e: Exception) {
            try {
                val f = clazz.superclass?.getDeclaredField(fieldName) ?: return
                f.isAccessible = true
                f.setInt(obj, value)
            } catch (e2: Exception) {
                XposedBridge.log("PE2: setIntField $fieldName failed: $e2")
            }
        }
    }

    private fun setBooleanField(obj: Any, clazz: Class<*>, fieldName: String, value: Boolean) {
        try {
            val f = clazz.getDeclaredField(fieldName)
            f.isAccessible = true
            f.setBoolean(obj, value)
        } catch (e: Exception) {
            XposedBridge.log("PE2: setBooleanField $fieldName failed: $e")
        }
    }

    private fun setObjectField(obj: Any, clazz: Class<*>, fieldName: String, value: Any) {
        try {
            val f = clazz.getDeclaredField(fieldName)
            f.isAccessible = true
            f.set(obj, value)
        } catch (e: Exception) {
            try {
                val f = clazz.superclass?.getDeclaredField(fieldName) ?: return
                f.isAccessible = true
                f.set(obj, value)
            } catch (e2: Exception) {
                XposedBridge.log("PE2: setObjectField $fieldName failed: $e2")
            }
        }
    }

    private fun buildFakeCellInfo(cell: FakeCell, classLoader: ClassLoader): CellInfoLte? {
        return try {
            val cellInfoLteClass = XposedHelpers.findClass(
                "android.telephony.CellInfoLte", classLoader
            )
            val cellIdentityLteClass = XposedHelpers.findClass(
                "android.telephony.CellIdentityLte", classLoader
            )
            val cellSignalStrengthLteClass = XposedHelpers.findClass(
                "android.telephony.CellSignalStrengthLte", classLoader
            )
            val cellInfoClass = XposedHelpers.findClass(
                "android.telephony.CellInfo", classLoader
            )

            val cellInfo = XposedHelpers.newInstance(cellInfoLteClass) as CellInfoLte
            val identity = XposedHelpers.newInstance(cellIdentityLteClass) as CellIdentityLte
            val signal = XposedHelpers.newInstance(
                cellSignalStrengthLteClass
            ) as CellSignalStrengthLte

            setIntField(identity, cellIdentityLteClass, "mMcc", cell.mcc)
            setIntField(identity, cellIdentityLteClass, "mMnc", cell.mnc)
            setIntField(identity, cellIdentityLteClass, "mTac", cell.tac)
            setIntField(identity, cellIdentityLteClass, "mCi", cell.ci)
            setIntField(identity, cellIdentityLteClass, "mPci", 123)
            setIntField(identity, cellIdentityLteClass, "mEarfcn", 1825)

            setIntField(signal, cellSignalStrengthLteClass, "mRssi", cell.dbm)
            setIntField(signal, cellSignalStrengthLteClass, "mRsrp", cell.dbm)
            setIntField(signal, cellSignalStrengthLteClass, "mRsrq", -9)
            setIntField(signal, cellSignalStrengthLteClass, "mRssnr", 10)
            setIntField(signal, cellSignalStrengthLteClass, "mCqi", cell.rssi)

            setObjectField(cellInfo, cellInfoLteClass, "mCellIdentityLte", identity)
            setObjectField(cellInfo, cellInfoLteClass, "mCellSignalStrengthLte", signal)

            try {
                setBooleanField(cellInfo, cellInfoClass, "mRegistered", cell.registered)
            } catch (e: Exception) {
                XposedBridge.log("PE2: mRegistered LTE failed: $e")
            }

            XposedBridge.log("PE2: built LTE cell TAC=${cell.tac} CI=${cell.ci}")
            cellInfo

        } catch (e: Exception) {
            XposedBridge.log("PE2: buildFakeCellInfo LTE failed: $e")
            null
        }
    }

    private fun buildFakeCellList(classLoader: ClassLoader): ArrayList<CellInfo> {
        val fakeList = ArrayList<CellInfo>()
        for (cell in FAKE_CELLS) {
            val cellInfo = buildFakeCellInfo(cell, classLoader)
            if (cellInfo != null) fakeList.add(cellInfo)
        }
        return fakeList
    }

    @SuppressLint("NewApi")
    fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {

        if (lpparam.packageName == "android") {
            XposedBridge.log("PE2: system_server loaded - starting trajectory scheduler")
            fetchBeijingTrajectory()
            startTrajectoryScheduler()

            if (!settings.isStarted || !settings.isHookedSystem) return
            if (System.currentTimeMillis() - mLastUpdated > 200) updateLocation()

            if (Build.VERSION.SDK_INT < 34) {
                val lmsClass = XposedHelpers.findClass(
                    "com.android.server.LocationManagerService",
                    lpparam.classLoader
                )

                XposedHelpers.findAndHookMethod(
                    lmsClass, "getLastLocation",
                    LocationRequest::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = buildSpoofedLocation(null)
                        }
                    }
                )

                for (method in lmsClass.declaredMethods) {
                    if (method.returnType == Boolean::class.java) {
                        if (method.name == "addGnssBatchingCallback" ||
                            method.name == "addGnssMeasurementsListener" ||
                            method.name == "addGnssNavigationMessageListener"
                        ) {
                            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    param.result = false
                                }
                            })
                        }
                    }
                }

                XposedHelpers.findAndHookMethod(
                    "com.android.server.LocationManagerService.Receiver",
                    lpparam.classLoader,
                    "callLocationChangedLocked",
                    Location::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.args[0] = buildSpoofedLocation(param.args[0] as? Location)
                        }
                    }
                )

            } else {
                val lmsClass = XposedHelpers.findClass(
                    "com.android.server.location.LocationManagerService",
                    lpparam.classLoader
                )

                for (method in lmsClass.declaredMethods) {
                    if (method.name == "getLastLocation" &&
                        method.returnType == Location::class.java
                    ) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = buildSpoofedLocation(null)
                                XposedBridge.log("PE2: getLastLocation → $newlat, $newlng")
                            }
                        })
                    }
                }

                for (method in lmsClass.declaredMethods) {
                    if (method.name == "startGnssBatch" ||
                        method.name == "addGnssAntennaInfoListener" ||
                        method.name == "addGnssMeasurementsListener" ||
                        method.name == "addGnssNavigationMessageListener" ||
                        method.name == "registerGnssStatusCallback" ||
                        method.name == "addGnssStatusListener"
                    ) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = null
                                XposedBridge.log("PE2: GNSS listener blocked: ${method.name}")
                            }
                        })
                    }
                }

                XposedHelpers.findAndHookMethod(
                    lmsClass,
                    "injectLocation",
                    Location::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.args[0] = buildSpoofedLocation(param.args[0] as? Location)
                            XposedBridge.log("PE2: injectLocation → $newlat, $newlng")
                        }
                    }
                )

                // Hook GPS provider delivery directly for apps using GPS provider
                for (method in lmsClass.declaredMethods) {
                    if (method.name == "reportLocation" ||
                        method.name == "handleLocationChanged" ||
                        method.name == "reportLocationLocked"
                    ) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val loc = param.args.filterIsInstance<Location>().firstOrNull()
                                if (loc != null) {
                                    val idx = param.args.indexOf(loc)
                                    param.args[idx] = buildSpoofedLocation(loc)
                                    XposedBridge.log("PE2: reportLocation hooked → $newlat, $newlng")
                                }
                            }
                        })
                    }
                }
            }

        } else {

            if (ignorePkg.contains(lpparam.packageName)) return

            val locationClass = XposedHelpers.findClass(
                "android.location.Location",
                lpparam.classLoader
            )
            val interval = 80

            for (method in locationClass.declaredMethods) {
                when (method.name) {

                    "getLatitude" -> XposedBridge.hookMethod(
                        method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (System.currentTimeMillis() - mLastUpdated > interval)
                                    updateLocation()
                                if (settings.isStarted) param.result = newlat
                            }
                        })

                    "getLongitude" -> XposedBridge.hookMethod(
                        method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (System.currentTimeMillis() - mLastUpdated > interval)
                                    updateLocation()
                                if (settings.isStarted) param.result = newlng
                            }
                        })

                    "getAccuracy" -> XposedBridge.hookMethod(
                        method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (settings.isStarted) param.result = accuracy
                            }
                        })

                    "getSpeed" -> XposedBridge.hookMethod(
                        method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (settings.isStarted) param.result = if (isPaused) 0.0f else currentSpeed
                            }
                        })

                    "getBearing" -> XposedBridge.hookMethod(
                        method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (settings.isStarted) param.result = currentBearing
                            }
                        })

                    "getAltitude" -> XposedBridge.hookMethod(
                        method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (settings.isStarted) param.result = currentElevation
                            }
                        })
                }
            }

            XposedHelpers.findAndHookMethod(
                locationClass, "set", Location::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (System.currentTimeMillis() - mLastUpdated > interval) updateLocation()
                        if (settings.isStarted) {
                            param.args[0] = buildSpoofedLocation(param.args[0] as? Location)
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager",
                lpparam.classLoader,
                "getLastKnownLocation",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (System.currentTimeMillis() - mLastUpdated > interval) updateLocation()
                        if (settings.isStarted) {
                            param.result = buildSpoofedLocation(null)
                        }
                    }
                }
            )
// Actively deliver spoofed location to GPS_PROVIDER listeners
            try {
                XposedHelpers.findAndHookMethod(
                    "android.location.LocationManager",
                    lpparam.classLoader,
                    "requestLocationUpdates",
                    String::class.java,
                    Long::class.java,
                    Float::class.java,
                    android.location.LocationListener::class.java,
                    android.os.Looper::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!settings.isStarted) return
                            val provider = param.args[0] as? String ?: return
                            val listener = param.args[3] as? android.location.LocationListener ?: return
                            // Start a background thread that delivers spoofed locations
                            Thread {
                                while (settings.isStarted) {
                                    try {
                                        val loc = buildSpoofedLocation(null)
                                        loc.provider = provider
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            try {
                                                listener.onLocationChanged(loc)
                                                XposedBridge.log("PE2: pushed spoofed loc to $provider listener")
                                            } catch (e: Exception) {
                                                XposedBridge.log("PE2: listener push failed: $e")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        XposedBridge.log("PE2: delivery thread error: $e")
                                    }
                                    Thread.sleep(advancementInterval(trajectoryIndex))
                                }
                            }.start()
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("PE2: requestLocationUpdates hook failed: $e")
            }


            try {
                XposedHelpers.findAndHookMethod(
                    "android.net.wifi.WifiManager",
                    lpparam.classLoader,
                    "getConnectionInfo",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!settings.isStarted) return
                            val info = param.result as? WifiInfo ?: return
                            try {
                                XposedHelpers.setObjectField(info, "mSSID", "\"$SPOOF_SSID\"")
                                XposedHelpers.setObjectField(info, "mBSSID", SPOOF_BSSID)
                                param.result = info
                                XposedBridge.log("PE2: WiFi spoofed → $SPOOF_SSID")
                            } catch (e: Exception) {
                                XposedBridge.log("PE2: WiFi spoof failed: $e")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("PE2: WiFi hook failed: $e")
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "android.net.wifi.WifiManager",
                    lpparam.classLoader,
                    "getScanResults",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!settings.isStarted) return
                            val results = ArrayList<ScanResult>()
                            for (ap in FAKE_APS) {
                                try {
                                    val sr = XposedHelpers.newInstance(ScanResult::class.java) as ScanResult
                                    XposedHelpers.setObjectField(sr, "SSID", ap.ssid)
                                    XposedHelpers.setObjectField(sr, "BSSID", ap.bssid)
                                    XposedHelpers.setIntField(sr, "level", ap.level)
                                    XposedHelpers.setIntField(sr, "frequency", ap.frequency)
                                    results.add(sr)
                                } catch (e: Exception) {
                                    XposedBridge.log("PE2: ScanResult build failed: $e")
                                }
                            }
                            param.result = results
                            XposedBridge.log("PE2: getScanResults → ${results.size} Beijing APs")
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("PE2: getScanResults hook failed: $e")
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    "getCellLocation",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!settings.isStarted) return
                            param.result = null
                            XposedBridge.log("PE2: getCellLocation → null (LTE device)")
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("PE2: getCellLocation hook failed: $e")
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    "getAllCellInfo",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!settings.isStarted) return
                            try {
                                val fakeList = buildFakeCellList(lpparam.classLoader)
                                param.result = fakeList
                                XposedBridge.log("PE2: getAllCellInfo → ${fakeList.size} Beijing LTE towers")
                            } catch (e: Exception) {
                                XposedBridge.log("PE2: getAllCellInfo spoof failed: $e")
                                param.result = ArrayList<CellInfo>()
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("PE2: getAllCellInfo hook failed: $e")
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    "requestCellInfoUpdate",
                    Executor::class.java,
                    TelephonyManager.CellInfoCallback::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!settings.isStarted) return
                            try {
                                val fakeList = buildFakeCellList(lpparam.classLoader)
                                val callback =
                                    param.args[1] as TelephonyManager.CellInfoCallback
                                callback.onCellInfo(fakeList)
                                param.result = null
                                XposedBridge.log("PE2: requestCellInfoUpdate → ${fakeList.size} Beijing LTE towers")
                            } catch (e: Exception) {
                                XposedBridge.log("PE2: requestCellInfoUpdate spoof failed: $e")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("PE2: requestCellInfoUpdate hook failed: $e")
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.PendingIntent",
                    lpparam.classLoader,
                    "send",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!settings.isStarted) return
                            XposedBridge.log("PE2: geofence PendingIntent intercepted")
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("PE2: geofence hook failed: $e")
            }

            try {
                val gnssCallbackClass = XposedHelpers.findClass(
                    "android.location.GnssMeasurementsEvent\$Callback",
                    lpparam.classLoader
                )
                for (method in gnssCallbackClass.declaredMethods) {
                    if (method.name == "onGnssMeasurementsReceived") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!settings.isStarted) return
                                param.result = null
                                XposedBridge.log("PE2: GnssMeasurementsEvent suppressed")
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                XposedBridge.log("PE2: GnssMeasurements hook failed: $e")
            }

            try {
                val gnssStatusCallbackClass = XposedHelpers.findClass(
                    "android.location.GnssStatus\$Callback",
                    lpparam.classLoader
                )
                for (method in gnssStatusCallbackClass.declaredMethods) {
                    if (method.name == "onSatelliteStatusChanged") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!settings.isStarted) return
                                param.result = null
                                XposedBridge.log("PE2: GnssStatus suppressed")
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                XposedBridge.log("PE2: GnssStatus hook failed: $e")
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "android.location.LocationManager",
                    lpparam.classLoader,
                    "registerGnssMeasurementsCallback",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!settings.isStarted) return
                            param.result = false
                            XposedBridge.log("PE2: registerGnssMeasurementsCallback blocked")
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("PE2: registerGnssMeasurementsCallback hook failed: $e")
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "android.location.LocationManager",
                    lpparam.classLoader,
                    "registerGnssStatusCallback",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!settings.isStarted) return
                            param.result = false
                            XposedBridge.log("PE2: registerGnssStatusCallback blocked")
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("PE2: registerGnssStatusCallback hook failed: $e")
            }
        }
    }
}
