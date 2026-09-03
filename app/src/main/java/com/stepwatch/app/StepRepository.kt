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

    // v1.4: histórico local de passos por dia (escrito pelo MidnightRolloverReceiver
    // ou pelo readMergedHistory quando Zepp não está disponível). ADR 0008.
    private val historyPrefs: SharedPreferences =
        context.getSharedPreferences("stepwatch_history", Context.MODE_PRIVATE)

    @Volatile private var lastRawTotal: Long = -1L
    @Volatile private var midnightRawTotal: Long = -1L
    @Volatile private var midnightDate: String = ""

    init {
        // v3.3 take 2: load the persisted sensor baseline eagerly so any
        // StepRepository instance — StatsFragment's, HistoryFragment's, anyone —
        // has a usable `readNativeStepsToday()` value even before
        // startNativeSensor() is called. Previously, only TodayFragment called
        // startNativeSensor(), so Stats/History always saw lastRawTotal = -1L
        // and silently got nativeToday = 0L via the Elvis fallback, which made
        // today's row stay at Zepp's (zero) value.
        val persistedLast = prefs.getLong(KEY_LAST_RAW, -1L)
        val persistedMidnight = prefs.getLong(KEY_MIDNIGHT_RAW, -1L)
        val persistedMidnightDate = prefs.getString(KEY_MIDNIGHT_DATE, "") ?: ""
        if (persistedLast >= 0L) lastRawTotal = persistedLast
        if (persistedMidnight >= 0L) midnightRawTotal = persistedMidnight
        if (persistedMidnightDate.isNotEmpty()) midnightDate = persistedMidnightDate
        Log.w(
            TAG,
            "ctor: baseline loaded lastRawTotal=$lastRawTotal midnightRawTotal=$midnightRawTotal midnightDate=$midnightDate"
        )
    }

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
            context.contentResolver.query(
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

    private fun parseDailyCursor(c: Cursor, days: Int): List<DailySteps>? {
        val dateIdx = c.getColumnIndex("day").let { if (it >= 0) it else c.getColumnIndex("date") }
        val stepIdx = c.getColumnIndex("step").let { if (it >= 0) it else c.getColumnIndex("total") }
        if (dateIdx < 0 || stepIdx < 0) {
            Log.w(TAG, "Zepp schema unknown; cols=${c.columnNames.joinToString()}")
            return null
        }
        val today = todayDate()
        val byDate = HashMap<String, Long>()
        while (c.moveToNext()) {
            val d = c.getString(dateIdx) ?: continue
            val s = c.getLong(stepIdx)
            val normalized = normalizeDate(d)
            byDate[normalized] = s
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val out = ArrayList<DailySteps>(days)
        for (i in 0 until days) {
            val date = sdf.format(cal.time)
            out.add(DailySteps(date, byDate[date] ?: 0L))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        // Sanity check: if all zeros, probably unauthorized or wrong schema
        if (out.sumOf { it.steps } == 0L) return null
        return out
    }

    private fun normalizeDate(s: String): String = when (s.length) {
        8 -> "${s.substring(0,4)}-${s.substring(4,6)}-${s.substring(6,8)}"
        else -> s
    }

    /**
     * Versão internal de [normalizeDate] para os testes JVM. Mesma implementação;
     * marcada internal para que o StepRepositoryTest (mesmo módulo) consiga chamar.
     */
    internal fun normalizeDateForTest(s: String): String = normalizeDate(s)

    fun startNativeSensor() {
        val s = stepCounterSensor
        if (s == null) {
            Log.w(TAG, "startNativeSensor: no TYPE_STEP_COUNTER on this device")
            return
        }
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
        // v3.3 take 2: the baseline is already loaded in init {}. Re-read here
        // is a no-op for fresh prefs but keeps the call site robust if anything
        // mutates prefs externally between construction and start.
        val persistedLast = prefs.getLong(KEY_LAST_RAW, -1L)
        val persistedMidnight = prefs.getLong(KEY_MIDNIGHT_RAW, -1L)
        val persistedMidnightDate = prefs.getString(KEY_MIDNIGHT_DATE, "") ?: ""
        if (persistedLast >= 0L) lastRawTotal = persistedLast
        if (persistedMidnight >= 0L) midnightRawTotal = persistedMidnight
        if (persistedMidnightDate.isNotEmpty()) midnightDate = persistedMidnightDate
        Log.w(
            TAG,
            "startNativeSensor: registered listener; baseline lastRawTotal=$lastRawTotal midnightRawTotal=$midnightRawTotal midnightDate=$midnightDate"
        )
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
        // v3.3 take 2: do NOT use Elvis (? : 0L) at the call site — that was
        // silently swallowing the "no baseline yet" signal. Return null and let
        // the caller decide via an explicit null check.
        if (lastRawTotal < 0) {
            Log.w(TAG, "readNativeStepsToday: lastRawTotal=-1 (no baseline loaded); returning null")
            return null
        }
        rollMidnightIfNeeded()
        val baseline = midnightRawTotal
        if (baseline < 0) {
            Log.w(TAG, "readNativeStepsToday: midnightRawTotal=-1 after roll; returning 0L")
            return 0L
        }
        val rawDelta = lastRawTotal - baseline
        val result = rawDelta.coerceAtLeast(0L)
        Log.w(
            TAG,
            "readNativeStepsToday: lastRawTotal=$lastRawTotal midnightRawTotal=$midnightRawTotal midnightDate=$midnightDate → $result"
        )
        return result
    }

    /**
     * Unified history: Zepp days + native sensor for today (if Zepp didn't return today).
     * Returns a list of length [days], today-first. The list is never empty: even when
     * both sources fail we return rows with zeros so the UI can render (and the empty
     * state is decided by the caller).
     *
     * v3.3 take 2: explicitly distinguish "Zepp unavailable" (null map) from
     * "Zepp returned 0 for this date". Use the sensor for today in both cases
     * when the sensor has a positive value. No Elvis on the merge logic — every
     * null branch is logged so adb logcat -s StepWatch:V makes the data flow
     * visible.
     */
    fun readMergedHistory(days: Int = 30): List<DailySteps> {
        // Explicit null check, NOT `?: emptyMap()`. If Zepp returned null, we
        // log it and treat as "no Zepp data" (every zeppSteps below will be 0).
        val zeppRaw = readZeppHistory(days)
        val zepp: Map<String, Long> = if (zeppRaw == null) {
            Log.w(TAG, "readMergedHistory: Zepp unavailable (null); falling back to native sensor only")
            emptyMap()
        } else {
            zeppRaw.associate { it.date to it.steps }
        }
        // Explicit null check, NOT `?: 0L`. The earlier Elvis was the actual
        // bug: nativeToday silently became 0 whenever no listener had fired in
        // this repo instance, which is exactly the Stats/History code path.
        val nativeToday = readNativeStepsToday()
        val nativeTodayValue: Long
        if (nativeToday == null) {
            Log.w(TAG, "readMergedHistory: native sensor has no baseline (null); today will only show Zepp value")
            nativeTodayValue = 0L
        } else {
            nativeTodayValue = nativeToday
        }
        // v1.4: histórico local persistido pelo MidnightRolloverReceiver (ADR 0008).
        // É a 3ª camada — depois de Zepp e do sensor nativo. Sem Elvis; usamos
        // lookup explícito e retornamos null quando ausente.
        val localHistory: Map<String, Long> = readLocalHistoryMap()
        val today = todayDate()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val out = ArrayList<DailySteps>(days)
        for (i in 0 until days) {
            val date = sdf.format(cal.time)
            val isToday = (date == today)
            val zeppSteps = zepp[date]
            val zeppStepsValue: Long = if (zeppSteps == null) 0L else zeppSteps
            // For today: if Zepp is missing OR returned 0 (likely because Zepp
            // hasn't consolidated today's total yet — it writes at midnight),
            // use the native sensor value instead so today's progress shows up
            // in totals/streaks/distance. For past days: trust Zepp (sensor is
            // ephemeral — only "since this app instance started").
            //
            // This used to be `zepp[date] ?: when (date) { today -> nativeToday; else -> 0L }`
            // which never fired for today because parseDailyCursor fills today's
            // missing entry with 0 (not null) — so today's native fallback was
            // permanently dead. The Elvis on the merge logic is replaced with
            // an explicit check on the value, not just nullability.
            //
            // v1.4 (ADR 0008): for PAST days, if Zepp is 0, fall back to the
            // local history persisted by MidnightRolloverReceiver. This is
            // what makes "days anteriores somem" stop happening for users who
            // have the rollover opt-in enabled (or who opened the app at
            // 23:55 once).
            val steps: Long
            if (isToday) {
                if (zeppStepsValue <= 0L && nativeTodayValue > 0L) {
                    steps = nativeTodayValue
                } else {
                    steps = zeppStepsValue
                }
            } else {
                // past day: Zepp primeiro, depois histórico local
                if (zeppStepsValue > 0L) {
                    steps = zeppStepsValue
                } else {
                    val localSteps = localHistory[date]
                    steps = if (localSteps == null) 0L else localSteps
                }
            }
            Log.w(
                TAG,
                "readMergedHistory[$date] isToday=$isToday zepp=$zeppStepsValue nativeToday=$nativeTodayValue local=${localHistory[date]} → $steps"
            )
            out.add(DailySteps(date, steps))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val total = out.sumOf { it.steps }
        Log.w(TAG, "readMergedHistory done: totalSteps=$total size=${out.size} today=$today")
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

    internal fun todayDate(): String =
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
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
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

    // ---- v1.4: histórico local (opt-in rollover, ADR 0008) ----

    /**
     * Lê todas as entradas "d_<date>" do SharedPreferences "stepwatch_history"
     * e devolve um Map<String, Long> com chave = "yyyy-MM-dd".
     * Sem Elvis no caller — explicit if/else (ver ADR 0006).
     */
    private fun readLocalHistoryMap(): Map<String, Long> {
        val out = HashMap<String, Long>()
        for ((k, v) in historyPrefs.all) {
            if (k.startsWith("d_") && v is Long) {
                out[k.removePrefix("d_")] = v
            } else if (k.startsWith("d_") && v is Int) {
                // Defensivo: alguns firmwares gravam Int em vez de Long.
                out[k.removePrefix("d_")] = v.toLong()
            }
        }
        Log.w(TAG, "readLocalHistoryMap: ${out.size} entries")
        return out
    }

    /**
     * Persiste um par (data → passos) no SharedPreferences "stepwatch_history".
     * Idempotente: chamar duas vezes com a mesma data sobrescreve.
     *
     * @param source "zepp" ou "sensor" — só pra debug; não usado no merge path.
     */
    fun saveHistoryEntry(date: String, steps: Long, source: String) {
        historyPrefs.edit()
            .putLong(historyKey(date), steps)
            .putString(historySourceKey(date), source)
            .apply()
        Log.w(TAG, "saveHistoryEntry: date=$date steps=$steps source=$source")
    }

    /** Lê um valor do histórico local. Retorna null se não foi persistido. */
    fun loadHistoryEntry(date: String): Long? {
        if (!historyPrefs.contains(historyKey(date))) return null
        return historyPrefs.getLong(historyKey(date), 0L)
    }

    /** Quantas entradas tem no histórico local (debug / Stats futura). */
    fun historySize(): Int = historyPrefs.all.size / 2 // cada entry tem key + source

    private fun historyKey(date: String): String = "d_$date"
    private fun historySourceKey(date: String): String = "s_$date"

    companion object {
        private const val TAG = "StepWatch"
        private const val KEY_LAST_RAW = "last_raw_total"
        private const val KEY_MIDNIGHT_RAW = "midnight_raw_total"
        private const val KEY_MIDNIGHT_DATE = "midnight_date"
    }
}