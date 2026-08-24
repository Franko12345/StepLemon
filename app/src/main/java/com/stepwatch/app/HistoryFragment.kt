package com.stepwatch.app

import android.os.Bundle
import android.widget.ImageView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryFragment : Fragment() {

    private lateinit var repo: StepRepository
    private lateinit var historyListContainer: LinearLayout
    private lateinit var historyEmpty: TextView
    private lateinit var histAvgSteps: TextView
    private lateinit var histDistance: TextView
    private lateinit var histGoalsMet: TextView
    private lateinit var chip7d: TextView
    private lateinit var chip30d: TextView
    private lateinit var chip90d: TextView
    private lateinit var chipAll: TextView

    private var rangeDays: Int = 7

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = StepRepository(requireContext().applicationContext)
        historyListContainer = view.findViewById(R.id.history_list)
        historyEmpty = view.findViewById(R.id.history_empty)
        histAvgSteps = view.findViewById(R.id.hist_avg_steps)
        histDistance = view.findViewById(R.id.hist_distance)
        histGoalsMet = view.findViewById(R.id.hist_goals_met)
        chip7d = view.findViewById(R.id.chip_7d)
        chip30d = view.findViewById(R.id.chip_30d)
        chip90d = view.findViewById(R.id.chip_90d)
        chipAll = view.findViewById(R.id.chip_all)

        chip7d.setOnClickListener { rangeDays = 7; refresh() }
        chip30d.setOnClickListener { rangeDays = 30; refresh() }
        chip90d.setOnClickListener { rangeDays = 90; refresh() }
        chipAll.setOnClickListener { rangeDays = 365; refresh() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        // v3.3 take 2: keep the native sensor listener alive on this screen too
        // so today's row stays live. See StatsFragment.onResume for the long
        // explanation of why this is needed.
        if (repo.hasNativeSensor()) repo.startNativeSensor()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        repo.stopNativeSensor()
    }

    private fun refresh() {
        // Chip styling
        val active = ColorUtil.primary(context!!)
        val dim = ColorUtil.dim(context!!)
        chip7d.setTextColor(if (rangeDays == 7) active else dim)
        chip30d.setTextColor(if (rangeDays == 30) active else dim)
        chip90d.setTextColor(if (rangeDays == 90) active else dim)
        chipAll.setTextColor(if (rangeDays >= 365) active else dim)

        val history = repo.readMergedHistory(rangeDays)
        historyListContainer.removeAllViews()
        if (history.isEmpty()) {
            historyEmpty.visibility = View.VISIBLE
            histAvgSteps.text = "—"
            histDistance.text = "—"
            histGoalsMet.text = "—"
            return
        }
        historyEmpty.visibility = View.GONE

        // v1.2: avg includes today; goals-met excludes today (day not finished).
        val withData = history.filter { it.steps > 0 }
        val avg = if (withData.isNotEmpty()) withData.sumOf { it.steps } / withData.size else 0L
        val totalKm = history.sumOf { it.steps } * 0.00075
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        val goalsMet = history.count { it.date != todayStr && it.steps >= repo.goalDaily }

        histAvgSteps.text = formatInt(avg)
        histDistance.text = String.format(Locale.getDefault(), "%.1f km", totalKm)
        histGoalsMet.text = goalsMet.toString()

        val inflater = layoutInflater
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val goal = repo.goalDaily.toFloat()
        for (d in history) {
            val row = inflater.inflate(R.layout.item_history, historyListContainer, false)
            val dayLabel = row.findViewById<TextView>(R.id.item_day_label)
            val dateLabel = row.findViewById<TextView>(R.id.item_date_label)
            val stepsVal = row.findViewById<TextView>(R.id.item_steps_value)
            val distanceVal = row.findViewById<TextView>(R.id.item_distance_value)
            val progress = row.findViewById<View>(R.id.item_progress)
            val check = row.findViewById<ImageView>(R.id.item_check)

            val cal = Calendar.getInstance()
            cal.time = sdf.parse(d.date)!!
            dayLabel.text = SimpleDateFormat("EEE", Locale("pt", "BR")).format(cal.time)
            dateLabel.text = String.format(
                Locale.getDefault(), "%d/%d",
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            stepsVal.text = formatInt(d.steps)
            distanceVal.text = String.format(Locale.getDefault(), "%.2f km", d.steps * 0.00075)

            // Progress: 0..1 clamped; visual via width animation isn't trivial here,
            // we just use background drawable on a View with width=match_parent and
            // adjust alpha as a coarse indicator. Simpler: rely on color change.
            val hit = d.steps >= repo.goalDaily
            check.visibility = if (hit) View.VISIBLE else View.GONE
            progress.alpha = if (hit) 1.0f else 0.5f
            progress.setBackgroundResource(
                if (hit) R.drawable.bg_progress_bar_done else R.drawable.bg_progress_bar
            )

            historyListContainer.addView(row)
        }
    }

    private fun formatInt(n: Long): String =
        String.format(Locale.getDefault(), "%,d", n).replace(',', '.')
}