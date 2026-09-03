package com.stepwatch.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            selectTab(item.itemId)
            true
        }

        if (savedInstanceState == null) {
            selectTab(R.id.nav_today)
            bottomNav.selectedItemId = R.id.nav_today
        }

        ensurePermission()

        // v1.4 (ADR 0008): reagenda o alarme de rollover se a preferência
        // estiver ON. Feito num Handler.post pra não atrasar a primeira
        // renderização. Idempotente: se já estiver agendado, é no-op.
        Handler(Looper.getMainLooper()).post {
            ensureRolloverAlarm()
        }
    }

    /**
     * Se o usuário ativou o rollover em uma sessão anterior mas o dispositivo
     * foi reiniciado (AlarmManager esquece alarmes no boot), reagenda aqui.
     */
    private fun ensureRolloverAlarm() {
        val prefs = getSharedPreferences("stepwatch_rollover", MODE_PRIVATE)
        val enabled = prefs.getBoolean("enabled", false)
        if (!enabled) return
        if (RolloverScheduler.isScheduled(this)) {
            // Já está agendado — não duplicar.
            return
        }
        try {
            RolloverScheduler.schedule(this)
        } catch (e: Exception) {
            // Permissão SCHEDULE_EXACT_ALARM negada, etc. Silencioso — toggle vai re-tentar.
        }
    }

    fun selectTab(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_today -> TodayFragment()
            R.id.nav_stats -> StatsFragment()
            R.id.nav_history -> HistoryFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> TodayFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_host, fragment)
            .commit()
        bottomNav.menu.findItem(itemId)?.isChecked = true
    }

    private fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQ_PERM
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private const val REQ_PERM = 1001
    }
}