package com.stepwatch.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var repo: StepRepository
    private lateinit var seekMin: SeekBar
    private lateinit var seekDaily: SeekBar
    private lateinit var seekStretch: SeekBar
    private lateinit var valMinGoal: TextView
    private lateinit var valDailyGoal: TextView
    private lateinit var valStretchGoal: TextView
    private lateinit var btnReset: Button
    private lateinit var settingsSource: TextView
    private lateinit var settingsZeppStatus: TextView
    private lateinit var rootView: View

    // Preset definitions — keep in sync with tickets/10-goal-preset-chips.md.
    private val minPresets = listOf(1000, 2000, 3000, 5000, 7000)
    private val dailyPresets = listOf(3000, 5000, 8000, 10000, 12000, 15000, 20000)
    private val stretchPresets = listOf(10000, 12000, 15000, 20000, 25000, 30000)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = StepRepository(requireContext().applicationContext)
        seekMin = view.findViewById(R.id.seek_min)
        seekDaily = view.findViewById(R.id.seek_daily)
        seekStretch = view.findViewById(R.id.seek_stretch)
        valMinGoal = view.findViewById(R.id.val_min_goal)
        valDailyGoal = view.findViewById(R.id.val_daily_goal)
        valStretchGoal = view.findViewById(R.id.val_stretch_goal)
        btnReset = view.findViewById(R.id.btn_reset)
        settingsSource = view.findViewById(R.id.settings_source)
        settingsZeppStatus = view.findViewById(R.id.settings_zepp_status)
        rootView = view

        seekMin.max = 19000
        seekDaily.max = 28000
        seekStretch.max = 28000

        // Init seek positions
        seekMin.progress = repo.goalMinimum - 1000
        seekDaily.progress = repo.goalDaily - 2000
        seekStretch.progress = repo.goalStretch - 5000
        refreshLabels()

        // Add preset chips above each each SeekBar.
        addPresetChipsAbove(view.findViewById(R.id.chips_min_anchor), minPresets, "min")
        addPresetChipsAbove(view.findViewById(R.id.chips_daily_anchor), dailyPresets, "daily")
        addPresetChipsAbove(view.findViewById(R.id.chips_stretch_anchor), stretchPresets, "stretch")

        seekMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                repo.goalMinimum = progress + 1000
                refreshLabels()
                refreshChipHighlights(minChips, repo.goalMinimum)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        seekDaily.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                repo.goalDaily = progress + 2000
                refreshLabels()
                refreshChipHighlights(dailyChips, repo.goalDaily)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        seekStretch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                repo.goalStretch = progress + 5000
                refreshLabels()
                refreshChipHighlights(stretchChips, repo.goalStretch)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnReset.setOnClickListener {
            repo.resetToday()
        }

        view.findViewById<Button>(R.id.btn_debug_zepp).setOnClickListener {
            val text = repo.dumpZeppSchema()
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            startActivity(android.content.Intent.createChooser(intent, "Compartilhar schema Zepp"))
        }

        refreshSource()
    }

    override fun onResume() {
        super.onResume()
        refreshSource()
    }

    private fun refreshLabels() {
        valMinGoal.text = String.format("%,d", repo.goalMinimum).replace(',', '.')
        valDailyGoal.text = String.format("%,d", repo.goalDaily).replace(',', '.')
        valStretchGoal.text = String.format("%,d", repo.goalStretch).replace(',', '.')
        refreshChipHighlights(minChips, repo.goalMinimum)
        refreshChipHighlights(dailyChips, repo.goalDaily)
        refreshChipHighlights(stretchChips, repo.goalStretch)
    }

    private fun refreshSource() {
        val zeppSteps = if (repo.isZeppInstalled()) repo.readZeppStepsToday() else null
        val zeppAuthorized = repo.isZeppAuthorized()
        val source = when {
            zeppSteps != null && zeppSteps >= 0 -> getString(R.string.source_zepp)
            repo.hasNativeSensor() -> getString(R.string.source_sensor)
            else -> getString(R.string.source_none)
        }
        settingsSource.text = source
        settingsZeppStatus.text = when {
            !repo.isZeppInstalled() -> getString(R.string.zepp_not_installed)
            !zeppAuthorized -> getString(R.string.zepp_denied)
            else -> getString(R.string.zepp_authorized)
        }
    }

    // ---- Preset chips ----

    private val minChips = mutableListOf<Pair<Int, TextView>>()
    private val dailyChips = mutableListOf<Pair<Int, TextView>>()
    private val stretchChips = mutableListOf<Pair<Int, TextView>>()

    private fun addPresetChipsAbove(
        anchor: ViewGroup?, presets: List<Int>, which: String
    ) {
        if (anchor == null) return
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val pad = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, pad, 0, pad)
        }
        val list = mutableListOf<Pair<Int, TextView>>()
        for (value in presets) {
            val chip = TextView(ctx).apply {
                text = formatPresetLabel(value)
                setBackgroundResource(R.drawable.bg_goal_chip)
                setTextColor(ContextCompat.getColor(ctx, R.color.lemon_on_surface))
                setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
                textSize = 13f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (which == "min") seekMin.progress = value - 1000
                    else if (which == "daily") seekDaily.progress = value - 2000
                    else if (which == "stretch") seekStretch.progress = value - 5000
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(ctx, 6)
            }
            row.addView(chip, params)
            list.add(value to chip)
        }
        anchor.addView(row)
        val target = when (which) {
            "min" -> minChips
            "daily" -> dailyChips
            "stretch" -> stretchChips
            else -> null
        }
        if (target != null) {
            target.clear()
            target.addAll(list)
        }
    }

    private fun refreshChipHighlights(chips: List<Pair<Int, TextView>>, currentValue: Int) {
        val ctx = requireContext()
        for ((value, chip) in chips) {
            chip.isSelected = (value == currentValue)
            chip.setTextColor(ContextCompat.getColor(
                ctx,
                if (chip.isSelected) R.color.lemon_on_primary else R.color.lemon_on_surface
            ))
        }
    }

    private fun formatPresetLabel(v: Int): String = when {
        v >= 1000 -> String.format(Locale.US, "%.1fk", v / 1000.0).replace(".0k", "k")
        else -> v.toString()
    }

    private fun dp(ctx: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), ctx.resources.displayMetrics
        ).toInt()
}