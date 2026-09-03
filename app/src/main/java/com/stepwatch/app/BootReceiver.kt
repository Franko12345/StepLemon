package com.stepwatch.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Após reboot do dispositivo, re-agenda o alarme de rollover de meia-noite
 * se o usuário tiver ligado a preferência. Sem alarme = sem trabalho.
 *
 * Antes da v1.4 (ADR 0008) este receiver era no-op: o sensor TYPE_STEP_COUNTER
 * continua contando no hardware mesmo depois de reboot, então não precisava
 * de serviço. Agora, com o rollover opt-in, precisamos reagendar o alarme
 * (AlarmManager perde alarmes ao reiniciar).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences("stepwatch_rollover", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("enabled", false)
        if (!enabled) {
            Log.w(TAG, "BootReceiver: rollover disabled; nothing to schedule")
            return
        }
        try {
            RolloverScheduler.schedule(appCtx)
            Log.w(TAG, "BootReceiver: rollover enabled; alarm scheduled")
        } catch (e: Exception) {
            Log.w(TAG, "BootReceiver: schedule failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "StepWatch"
    }
}