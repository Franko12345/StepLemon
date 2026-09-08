package com.stepwatch.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Today screen: triple donut + 3 goal cards + 3 stat cards.
 * Data source preference: Zepp if installed AND returns a value, else native sensor.
 */
class TodayFragment : Fragment() {

    private lateinit var repo: StepRepository

    private lateinit var donut: TripleDonutView
    private lateinit var stepsValue: TextView
    private lateinit var sourcePill: TextView
    private lateinit var goalMinValue: TextView
    private lateinit var goalDailyValue: TextView
    private lateinit var goalStretchValue: TextView
    private lateinit var goalMinCard: LinearLayout
    private lateinit var goalDailyCard: LinearLayout
    private lateinit var goalStretchCard: LinearLayout
    private lateinit var cardStreakValue: TextView
    private lateinit var cardDistanceValue: TextView
    private lateinit var cardFloorsValue: TextView
    private lateinit var zeppRequiredText: TextView
    // Ticket 12 / ADR 0009: subtle caption when the sensor baseline was
    // captured in this session (on resume, not at first sensor event).
    private lateinit var captionPreOpen: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_today, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = StepRepository(requireContext().applicationContext)

        donut = view.findViewById(R.id.donut)
        stepsValue = view.findViewById(R.id.steps_value)
        sourcePill = view.findViewById(R.id.source_pill)
        goalMinValue = view.findViewById(R.id.goal_min_value)
        goalDailyValue = view.findViewById(R.id.goal_daily_value)
        goalStretchValue = view.findViewById(R.id.goal_stretch_value)
        goalMinCard = view.findViewById(R.id.goal_min_card)
        goalDailyCard = view.findViewById(R.id.goal_daily_card)
        goalStretchCard = view.findViewById(R.id.goal_stretch_card)
        cardStreakValue = view.findViewById(R.id.card_streak_value)
        cardDistanceValue = view.findViewById(R.id.card_distance_value)
        cardFloorsValue = view.findViewById(R.id.card_floors_value)
        zeppRequiredText = view.findViewById(R.id.zepp_required_text)
        captionPreOpen = view.findViewById(R.id.caption_pre_open)

        // Update goal values text from prefs
        goalMinValue.text = formatK(repo.goalMinimum)
        goalDailyValue.text = formatK(repo.goalDaily)
        goalStretchValue.text = formatK(repo.goalStretch)

        donut.goalMin = repo.goalMinimum
        donut.goalDaily = repo.goalDaily
        donut.goalStretch = repo.goalStretch

        goalMinCard.setOnClickListener { openSettings() }
        goalDailyCard.setOnClickListener { openSettings() }
        goalStretchCard.setOnClickListener { openSettings() }
    }

    private fun openSettings() {
        val activity = requireActivity()
        if (activity is MainActivity) activity.selectTab(R.id.nav_settings)
    }

    override fun onResume() {
        super.onResume()
        if (repo.hasNativeSensor()) repo.startNativeSensor()
        handler.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        repo.stopNativeSensor()
        handler.removeCallbacks(refresher)
    }

    private fun refresh() {
        // Try Zepp first
        val zeppSteps = if (repo.isZeppInstalled()) repo.readZeppStepsToday() else null
        val zeppAuthorized = repo.isZeppAuthorized()

        val nativeSteps = repo.readNativeStepsToday()
        val hasZepp = zeppSteps != null && zeppSteps >= 0
        val steps = zeppSteps ?: nativeSteps ?: 0L

        donut.steps = steps
        stepsValue.text = if (steps == 0L) "0" else formatInt(steps)
        // Hoist once: the field is @Volatile internal and used 3× below.
        val captured = repo.midnightCapturedAt
        sourcePill.text = when {
            hasZepp -> getString(R.string.source_zepp)
            nativeSteps != null && captured != null ->
                getString(R.string.source_sensor_captured, formatHourMinute(captured))
            nativeSteps != null -> getString(R.string.source_sensor)
            else -> getString(R.string.source_none)
        }
        // Caption only when (a) the active source is the native sensor AND
        // (b) the baseline was captured in this process lifetime (so the user
        // can tell the count started at app-open, not at first step).
        val captionVisible = !hasZepp && nativeSteps != null && captured != null
        captionPreOpen.visibility = if (captionVisible) View.VISIBLE else View.GONE

        // Highlight active goal card
        val active = when {
            steps >= repo.goalStretch -> goalStretchCard
            steps >= repo.goalDaily -> goalDailyCard
            else -> goalMinCard
        }
        active.isSelected = true

        // Stats: distance ≈ steps * 0.00075 km, calories ≈ steps * 0.04
        val km = steps * 0.00075
        val cal = (steps * 0.04).roundToInt()
        cardDistanceValue.text = String.format(Locale.getDefault(), "%.2f km", km)
        cardStreakValue.text = "0"  // streak needs multi-day; we set later via Zepp history
        cardFloorsValue.text = cal.toString()  // we hijack to show calories

        // Show "zepp required" hint if not installed/authorized AND no sensor
        val noZepp = !repo.isZeppInstalled() || !zeppAuthorized
        zeppRequiredText.visibility = if (noZepp) View.VISIBLE else View.GONE
    }

    private fun formatK(n: Int): String = when {
        n >= 1000 -> String.format(Locale.getDefault(), "%.1fk", n / 1000.0)
        else -> n.toString()
    }

    private fun formatInt(n: Long): String =
        String.format(Locale.getDefault(), "%,d", n).replace(',', '.')

    private fun formatHourMinute(epochMs: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}