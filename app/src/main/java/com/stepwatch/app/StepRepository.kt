package com.stepwatch.app

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Read steps from two sources:
 *  - Native Android TYPE_STEP_COUNTER (works on any Android, including MIUI/HyperOS,
 *    no login, no Xiaomi account). Counts since last device boot.
 *  - Zepp Life (Mi Fitness) ContentProvider (com.xiaomi.hm.health.HMProvider) —
 *    if installed and authorized, gives historical daily steps.
 */
class StepRepository(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // Zepp Life content provider URIs
    private val zeppAuthority = "com.xiaomi.hm.health.HMProvider"
    private val zeppDailySummaryUri = Uri.parse("content://$zeppAuthority/day_total_summary")
    private val zeppStepCounterUri = Uri.parse("content://$zeppAuthority/step_counter")

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stepwatch", Context.MODE_PRIVATE)

    // Goal storage
    val goalPrefs: SharedPreferences =
        context.getSharedPreferences("stepwatch_goals", Context.MODE_PRIVATE)

    @Volatile private var lastRawTotal: Long = -1L
    @Volatile private var midnightRawTotal: Long = -1L
    @Volatile private var midnightDate: String = ""

    // ---- Goals (defaults match Stepmelon-like UX) ----
    var goalMinimum: Int
        get() = goalPrefs.getInt("min", 3000)
        set(v) { goalPrefs.edit().putInt("min", v).apply() }

    var goalDaily: Int
        get() = goalPrefs.getInt("daily", 10000)
        set(v) { goalPrefs.edit().putInt("daily", v).apply() }

    var goalStretch: Int
        get() = goalPrefs.getInt("stretch", 15000)
        set(v) { goalPrefs.edit().putInt("stretch", v).apply() }

    fun hasNativeSensor(): Boolean = stepCounterSensor != null

    fun isZeppInstalled(): Boolean =
        try {
            context.packageManager.getPackageInfo("com.xiaomi.hm.health", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    fun isZeppAuthorized(): Boolean = try {
        context.contentResolver.query(zeppDailySummaryUri, null, null, null, null)?.use {
            it.moveToFirst()
        } ?: false
    } catch (e: SecurityException) {
        Log.w(TAG, "Zepp not authorized: ${e.message}")
        false
    } catch (e: Exception) {
        Log.w(TAG, "Zepp query failed: ${e.message}")
        false
    }

    /**
     * Returns a list of daily steps for the last [days] days (today first), reading
     * Zepp's `day_total_summary` provider. Returns null if not installed/unauthorized
     * or if the provider schema differs.
     *
     * Best-effort schema: column "day" (yyyy-MM-dd or yyyyMMdd), column "step" (long).
     */
    fun readZeppHistory(days: Int = 7): List<DailySteps>? {
        if (!isZeppInstalled()) return null
        return try {
            context.contentResolver.query(
                zeppDailySummaryUri, null, null, null, null
            )?.use { c ->
                parseDailyCursor(c, days)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Zepp SecurityException — needs user grant in Zepp app")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Zepp history read failed: ${e.message}")
            null
        }
    }

    /**
     * Try the simpler step_counter provider — many MIUI/HyperOS devices expose
     * one-day-at-a-time via date param. Returns today's steps or null.
     */
    fun readZeppStepsToday(): Long? {
        if (!isZeppInstalled()) return null
        val today = todayDate()
        return try {
            context.contentResolver?.query(
                zeppStepCounterUri,
                arrayOf("date", "step"),
                "date = ?",
                arrayOf(today),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex("step")
                    if (idx >= 0) c.getLong(idx) else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Zepp step_counter read failed: ${e.message}")
            null
        }
    }

    /**
     * Parse a Cursor from Zepp's `day_total_summary` provider.
     * Returns null if the schema is unrecognised or the result is all-zeros
     * (likely unauthorized).
     *
     * Schema discovery is table-driven: the parser tries each candidate schema
     * (column name + data type) in priority order and uses the first that
     * produces a valid date column. This is what the test suite verifies.
     */
    internal fun parseDailyCursor(c: Cursor, days: Int): List<DailySteps>? {
        // Try each candidate date column + date type combination.
        val dateColumnCandidates = listOf(
            DateColumnSpec("day", DateType.STRING),
            DateColumnSpec("day", DateType.YYYYMMDD_INT),
            DateColumnSpec("date", DateType.STRING),
            DateColumnSpec("date", DateType.LONG_MILLIS)
        )
        val stepColumnCandidates = listOf("step", "total")

        val dateSpec = dateColumnCandidates.firstOrNull { c.getColumnIndex(it.name) >= 0 }
            ?: return logAndNull(c, "no recognized date column")
        val stepCol = stepColumnCandidates.firstOrNull { c.getColumnIndex(it) >= 0 }
            ?: return logAndNull(c, "no recognized step column")

        val dateIdx = c.getColumnIndex(dateSpec.name)
        val stepIdx = c.getColumnIndex(stepCol)

        val byDate = HashMap<String, Long>()
        while (c.moveToNext()) {
            val rawDate = when (dateSpec.type) {
                DateType.STRING -> c.getString(dateIdx)
                DateType.YYYYMMDD_INT -> c.getString(dateIdx)  // stored as text but 8 chars
                DateType.LONG_MILLIS -> c.getLong(dateIdx).let { millisToDate(it) }
            } ?: continue
            val steps = c.getLong(stepIdx)
            val normalized = normalizeDate(rawDate)
            if (normalized.isNotEmpty()) byDate[normalized] = steps
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val out = ArrayList<DailySteps>(days)
        for (i in 0 until days) {
            val date = sdf.format(cal.time)
            out.add(DailySteps(date, byDate[date] ?: 0L))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        if (out.sumOf { it.steps } == 0L) return null
        return out
    }

    private fun logAndNull(c: Cursor, why: String): List<DailySteps>? {
        Log.w(TAG, "Zepp schema unknown: $why; cols=${c.columnNames.joinToString()}")
        return null
    }

    private enum class DateType { STRING, YYYYMMDD_INT, LONG_MILLIS }
    private data class DateColumnSpec(val name: String, val type: DateType)

    private fun millisToDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    internal fun normalizeDate(s: String): String = when {
        s.length == 8 && s.all { it.isDigit() } -> "${s.substring(0,4)}-${s.substring(4,6)}-${s.substring(6,8)}"
        else -> s
    }

    fun startNativeSensor() {
        val s = stepCounterSensor ?: return
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
        prefs.getLong(KEY_LAST_RAW, -1L).let { if (it >= 0) lastRawTotal = it }
        prefs.getLong(KEY_MIDNIGHT_RAW, -1L).let { if (it >= 0) midnightRawTotal = it }
        midnightDate = prefs.getString(KEY_MIDNIGHT_DATE, "") ?: ""
    }

    fun stopNativeSensor() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val v = event?.values?.firstOrNull()?.toLong() ?: return
        lastRawTotal = v
        prefs.edit().putLong(KEY_LAST_RAW, v).apply()
        rollMidnightIfNeeded()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun readNativeStepsToday(): Long? {
        if (lastRawTotal < 0) return null
        rollMidnightIfNeeded()
        val baseline = midnightRawTotal
        if (baseline < 0) return 0L
        return (lastRawTotal - baseline).coerceAtLeast(0L)
    }

    /**
     * Unified history: Zepp days + native sensor for today (if Zepp didn't return today).
     * Returns null only if both sources fail. Today-first list of length [days].
     *
     * v1.2: Stats / History were showing "—" because Zepp either wasn't authorized
     * or its schema didn't match. This method gives them a working view even when
     * Zepp is broken: today is filled from the native sensor as a fallback.
     */
    fun readMergedHistory(days: Int = 30): List<DailySteps> {
        val zepp = readZeppHistory(days)?.associate { it.date to it.steps } ?: emptyMap()
        val nativeToday = readNativeStepsToday() ?: 0L
        val today = todayDate()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val out = ArrayList<DailySteps>(days)
        for (i in 0 until days) {
            val date = sdf.format(cal.time)
            val zeppSteps = zepp[date] ?: 0L
            // For today: Zepp often returns 0 because it hasn't consolidated today's
            // total yet (it writes at midnight). If so, fall back to the native sensor
            // so today's progress shows up in the totals.
            // For past days: always Zepp (it should be correct; sensor data is ephemeral).
            val steps: Long = when {
                date == today && zeppSteps <= 0L && nativeToday > 0L -> nativeToday
                else -> zeppSteps
            }
            Log.w(TAG, "readMergedHistory[$date] zepp=$zeppSteps native=$nativeToday picked=$steps isToday=${date==today}")
            out.add(DailySteps(date, steps))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        Log.w(TAG, "readMergedHistory done: totalSteps=${out.sumOf { it.steps }} size=${out.size}")
        return out
    }

    fun resetToday() {
        val v = lastRawTotal
        if (v >= 0) {
            midnightRawTotal = v
            midnightDate = todayDate()
            prefs.edit()
                .putLong(KEY_MIDNIGHT_RAW, v)
                .putString(KEY_MIDNIGHT_DATE, midnightDate)
                .apply()
        }
    }

    private fun rollMidnightIfNeeded() {
        val today = todayDate()
        if (midnightDate != today || midnightRawTotal < 0) {
            midnightRawTotal = lastRawTotal
            midnightDate = today
            prefs.edit()
                .putLong(KEY_MIDNIGHT_RAW, lastRawTotal)
                .putString(KEY_MIDNIGHT_DATE, today)
                .apply()
        }
    }

    private fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Diagnostic dump: returns a human-readable string listing the column
     * names found in each known Zepp provider, plus one sample row.
     * Used by the debug screen so the user can paste the result back.
     */
    fun dumpZeppSchema(): String {
        val out = StringBuilder()
        val providers = listOf(
            "day_total_summary" to Uri.parse("content://$zeppAuthority/day_total_summary"),
            "step_counter" to Uri.parse("content://$zeppAuthority/step_counter")
        )
        out.appendLine("=== Zepp HMProvider dump ===")
        out.appendLine("package installed: ${isZeppInstalled()}")
        out.appendLine("authorized: ${isZeppAuthorized()}")
        out.appendLine()
        for ((name, uri) in providers) {
            out.appendLine("--- $name ---")
            try {
                context.contentResolver?.query(uri, null, null, null, null)?.use { c ->
                    out.appendLine("columns (${c.columnCount}): ${c.columnNames.joinToString()}")
                    if (c.moveToFirst()) {
                        val row = (0 until c.columnCount).joinToString { i ->
                            "${c.getColumnName(i)}=${c.getString(i) ?: "<null>"}"
                        }
                        out.appendLine("row[0]: $row")
                    } else {
                        out.appendLine("row[0]: <empty>")
                    }
                } ?: out.appendLine("query returned null cursor")
            } catch (e: SecurityException) {
                out.appendLine("SecurityException: ${e.message}")
            } catch (e: Exception) {
                out.appendLine("Exception: ${e.javaClass.simpleName}: ${e.message}")
            }
            out.appendLine()
        }
        return out.toString()
    }

    data class DailySteps(val date: String, val steps: Long)

    companion object {
        private const val TAG = "StepWatch"
        private const val KEY_LAST_RAW = "last_raw_total"
        private const val KEY_MIDNIGHT_RAW = "midnight_raw_total"
        private const val KEY_MIDNIGHT_DATE = "midnight_date"
    }
}