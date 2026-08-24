package com.stepwatch.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment

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

        seekMin.max = 19000
        seekDaily.max = 28000
        seekStretch.max = 28000

        // Init seek positions
        seekMin.progress = repo.goalMinimum - 1000
        seekDaily.progress = repo.goalDaily - 2000
        seekStretch.progress = repo.goalStretch - 5000
        refreshLabels()

        seekMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                repo.goalMinimum = progress + 1000
                refreshLabels()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        seekDaily.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                repo.goalDaily = progress + 2000
                refreshLabels()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        seekStretch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                repo.goalStretch = progress + 5000
                refreshLabels()
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
}