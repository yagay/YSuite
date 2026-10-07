package com.yagay.ysuite.feature.yparam

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.yagay.ysuite.feature.yparam.api.YParamAppSummary
import com.yagay.ysuite.feature.yparam.api.YParamDefaults
import com.yagay.ysuite.feature.yparam.api.YParamOverrides
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

internal class YParamRepository(
    private val context: Context,
) {
    private val prefs =
        context.getSharedPreferences(
            "ysuite_yparam_overrides",
            Context.MODE_PRIVATE,
        )
    private val packageManager = context.packageManager

    fun apps(): List<YParamAppSummary> =
        packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .map { info ->
                val packageName = info.packageName
                YParamAppSummary(
                    packageName = packageName,
                    label =
                        runCatching {
                            packageManager
                                .getApplicationLabel(info)
                                .toString()
                        }.getOrDefault(packageName),
                    system =
                        info.flags and
                            ApplicationInfo.FLAG_SYSTEM !=
                            0,
                    enabled = info.enabled,
                    overrideCount =
                        read(packageName).overrideCount(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

    fun read(packageName: String): YParamOverrides =
        decode(prefs.getString(packageName, null))

    fun save(
        packageName: String,
        value: YParamOverrides,
    ) {
        val edit = prefs.edit()
        if (value.overrideCount() == 0) {
            edit.remove(packageName)
        } else {
            edit.putString(packageName, encode(value))
        }
        check(edit.commit()) {
            "Unable to persist parameter overrides"
        }
    }

    fun reset(packageName: String) {
        check(prefs.edit().remove(packageName).commit()) {
            "Unable to remove parameter overrides"
        }
    }

    fun defaults(packageName: String): YParamDefaults {
        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val orientation =
            launchOrientation(packageName)
                ?.toString()
                ?: "unspecified"
        val night =
            when (
                configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
            ) {
                Configuration.UI_MODE_NIGHT_YES -> "dark"
                Configuration.UI_MODE_NIGHT_NO -> "light"
                else -> "unspecified"
            }
        val refreshRate =
            runCatching {
                context.display?.refreshRate
            }.getOrNull()
        return YParamDefaults(
            densityDpi = metrics.densityDpi,
            widthPixels = metrics.widthPixels,
            heightPixels = metrics.heightPixels,
            smallestWidthDp =
                configuration.smallestScreenWidthDp,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            fontScale = configuration.fontScale,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi,
            refreshRate = refreshRate,
            localeTag = Locale.getDefault().toLanguageTag(),
            timeZoneId = TimeZone.getDefault().id,
            nightMode = night,
            orientation = orientation,
        )
    }

    private fun launchOrientation(
        packageName: String,
    ): Int? =
        runCatching {
            val component =
                packageManager
                    .getLaunchIntentForPackage(packageName)
                    ?.component
                    ?: return@runCatching null
            packageManager
                .getActivityInfo(component, 0)
                .screenOrientation
                .takeUnless {
                    it ==
                        ActivityInfo
                            .SCREEN_ORIENTATION_UNSPECIFIED
                }
        }.getOrNull()

    private fun encode(value: YParamOverrides): String =
        JSONObject().apply {
            putIfNotNull("densityDpi", value.densityDpi)
            putIfNotNull("widthPixels", value.widthPixels)
            putIfNotNull("heightPixels", value.heightPixels)
            putIfNotNull("smallestWidthDp", value.smallestWidthDp)
            putIfNotNull("screenWidthDp", value.screenWidthDp)
            putIfNotNull("screenHeightDp", value.screenHeightDp)
            putIfNotNull("fontScale", value.fontScale)
            putIfNotNull("xdpi", value.xdpi)
            putIfNotNull("ydpi", value.ydpi)
            putIfNotNull("refreshRate", value.refreshRate)
            putIfNotNull("localeTag", value.localeTag)
            putIfNotNull("timeZoneId", value.timeZoneId)
            putIfNotNull("nightMode", value.nightMode)
            putIfNotNull("orientation", value.orientation)
            putIfNotNull("userAgent", value.userAgent)
            putIfNotNull("allowScreenshots", value.allowScreenshots)
            putIfNotNull("keepScreenOn", value.keepScreenOn)
            putIfNotNull("locationMode", value.locationMode)
            putIfNotNull("latitude", value.latitude)
            putIfNotNull("longitude", value.longitude)
            putIfNotNull("altitude", value.altitude)
            putIfNotNull("accuracy", value.accuracy)
            putIfNotNull("speed", value.speed)
            putIfNotNull("bearing", value.bearing)
            putIfNotNull("randomRadiusMeters", value.randomRadiusMeters)
            putIfNotNull(
                "locationUpdateIntervalMs",
                value.locationUpdateIntervalMs,
            )
        }.toString()

    private fun decode(raw: String?): YParamOverrides {
        if (raw.isNullOrBlank()) return YParamOverrides()
        return runCatching {
            val value = JSONObject(raw)
            YParamOverrides(
                densityDpi = value.optIntOrNull("densityDpi"),
                widthPixels = value.optIntOrNull("widthPixels"),
                heightPixels = value.optIntOrNull("heightPixels"),
                smallestWidthDp = value.optIntOrNull("smallestWidthDp"),
                screenWidthDp = value.optIntOrNull("screenWidthDp"),
                screenHeightDp = value.optIntOrNull("screenHeightDp"),
                fontScale = value.optFloatOrNull("fontScale"),
                xdpi = value.optFloatOrNull("xdpi"),
                ydpi = value.optFloatOrNull("ydpi"),
                refreshRate = value.optFloatOrNull("refreshRate"),
                localeTag = value.optStringOrNull("localeTag"),
                timeZoneId = value.optStringOrNull("timeZoneId"),
                nightMode = value.optStringOrNull("nightMode"),
                orientation = value.optStringOrNull("orientation"),
                userAgent = value.optStringOrNull("userAgent"),
                allowScreenshots = value.optBooleanOrNull("allowScreenshots"),
                keepScreenOn = value.optBooleanOrNull("keepScreenOn"),
                locationMode = value.optStringOrNull("locationMode"),
                latitude = value.optDoubleOrNull("latitude"),
                longitude = value.optDoubleOrNull("longitude"),
                altitude = value.optDoubleOrNull("altitude"),
                accuracy = value.optFloatOrNull("accuracy"),
                speed = value.optFloatOrNull("speed"),
                bearing = value.optFloatOrNull("bearing"),
                randomRadiusMeters =
                    value.optFloatOrNull("randomRadiusMeters"),
                locationUpdateIntervalMs =
                    value.optIntOrNull(
                        "locationUpdateIntervalMs",
                    ),
            )
        }.getOrDefault(YParamOverrides())
    }

    private fun JSONObject.putIfNotNull(
        key: String,
        value: Any?,
    ) {
        if (value != null && value.toString().isNotBlank()) {
            put(key, value)
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optFloatOrNull(key: String): Float? =
        if (has(key) && !isNull(key)) optDouble(key).toFloat() else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) {
            optString(key).takeIf { it.isNotBlank() }
        } else {
            null
        }

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null
}
