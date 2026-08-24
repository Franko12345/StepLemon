package com.stepwatch.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class StatsFragment : Fragment() {

    private lateinit var repo: StepRepository
    private lateinit var weeklyChart: BarChartView
    private lateinit var lifetimeTotalSteps: TextView
    private lateinit var lifetimeTotalDistance: TextView
    private lateinit var lifetimeGoalsMet: TextView
    private lateinit var lifetimeDaysTracked: TextView
    private lateinit var lifetimeCurrentStreak: TextView
    private lateinit var lifetimeLongestStreak: TextView
    private lateinit var pbMostSteps: TextView
    private lateinit var pbLongestStreak: TextView
    private lateinit var statsEmpty: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_stats, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = StepRepository(requireContext().applicationContext)
        weeklyChart = view.findViewById(R.id.weekly_chart)
        lifetimeTotalSteps = view.findViewById(R.id.lifetime_total_steps)
        lifetimeTotalDistance = view.findViewById(R.id.lifetime_total_distance)
        lifetimeGoalsMet = view.findViewById(R.id.lifetime_goals_met)
        lifetimeDaysTracked = view.findViewById(R.id.lifetime_days_tracked)
        lifetimeCurrentStreak = view.findViewById(R.id.lifetime_current_streak)
        lifetimeLongestStreak = view.findViewById(R.id.lifetime_longest_streak)
        pbMostSteps = view.findViewById(R.id.pb_most_steps)
        pbLongestStreak = view.findViewById(R.id.pb_longest_streak)
        statsEmpty = view.findViewById(R.id.stats_empty)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val history = repo.readMergedHistory(30)
        if (history.isEmpty()) {
            statsEmpty.visibility = View.VISIBLE
            weeklyChart.bars = emptyList()
            lifetimeTotalSteps.text = "—"
            lifetimeTotalDistance.text = "—"
            lifetimeGoalsMet.text = "—"
            lifetimeDaysTracked.text = "—"
            lifetimeCurrentStreak.text = "—"
            lifetimeLongestStreak.text = "—"
            pbMostSteps.text = "—"
            pbLongestStreak.text = "—"
            return
        }
        statsEmpty.visibility = View.GONE

        // Today-first list; oldest day is at index size-1.
        // v1.2: include today in totals + chart. Exclude today from goals-met +
        // streak (the day isn't finished, those numbers lie until midnight).
        val today = history.first()  // history[0] = today
        val past = history.drop(1)

        // Last 7 days chart: today + 6 previous (chronological: oldest first)
        val last7 = (past.take(6) + today).reversed()
        weeklyChart.targetLine = repo.goalDaily.toFloat()
        weeklyChart.bars = last7.map {
            val cal = Calendar.getInstance()
            cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.date)!!
            val dayName = SimpleDateFormat("EEE", Locale("pt", "BR")).format(cal.time)
            BarChartView.Bar(dayName.substring(0..2), it.steps.toFloat())
        }

        val totalSteps = history.sumOf { it.steps }
        val totalKm = totalSteps * 0.00075
        val daysWithData = history.count { it.steps > 0 }
        // Goals-met and streaks only count PAST days — today's progress is partial.
        val goalsMet = past.count { it.steps >= repo.goalDaily }

        // Streak: consecutive PAST days from yesterday backwards with steps >= goal.
        // (If today qualifies, that's a bonus but not yet a "streak day".)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val byDate = past.associateBy { it.date }
        var currentStreak = 0
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        while (true) {
            val d = sdf.format(cal.time)
            val day = byDate[d]
            if (day != null && day.steps >= repo.goalDaily) {
                currentStreak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else break
        }

        // Longest streak over past days only.
        val sortedDates = past.sortedBy { it.date }
        var longest = 0
        var run = 0
        var prev: Date? = null
        for (d in sortedDates) {
            val cur = sdf.parse(d.date)!!
            if (prev == null || ((cur.time - prev.time) / 86400000L) == 1L) {
                run = if (d.steps >= repo.goalDaily) run + 1 else 0
            } else {
                run = if (d.steps >= repo.goalDaily) 1 else 0
            }
            longest = max(longest, run)
            prev = cur
        }

        lifetimeTotalSteps.text = formatInt(totalSteps)
        lifetimeTotalDistance.text = String.format(Locale.getDefault(), "%.1f km", totalKm)
        lifetimeGoalsMet.text = goalsMet.toString()
        lifetimeDaysTracked.text = daysWithData.toString()
        lifetimeCurrentStreak.text = "$currentStreak dias"
        lifetimeLongestStreak.text = "$longest dias"

        val most = history.maxOfOrNull { it.steps } ?: 0L
        pbMostSteps.text = formatInt(most)
        pbLongestStreak.text = "$longest dias"
    }

    private fun formatInt(n: Long): String =
        String.format(Locale.getDefault(), "%,d", n).replace(',', '.')
}