package com.stepwatch.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Helper que agenda / cancela o alarme de rollover de meia-noite.
 * Sem WorkManager, sem foreground service — apenas AlarmManager.
 * Compatível com Doze (setExactAndAllowWhileIdle) e MIUI/HyperOS.
 *
 * ADR 0008 (v1.4): opt-in — só agenda se o usuário ligou a preferência.
 * Default é OFF (preserva privacidade — sem alarme rodando).
 */
object RolloverScheduler {
    private const val TAG = "StepWatch"
    const val ACTION_ROLLOVER = "com.stepwatch.app.ROLLOVER"

    // Request code do PendingIntent (precisa ser estável para match com FLAG_NO_CREATE).
    private const val REQ_CODE = 0x5253 // "RS" Rollover Schedule

    // Janela: 23:55 + 0..5 min de jitter (espalha execuções entre instalações).
    private const val HOUR = 23
    private const val MINUTE = 55
    private const val MAX_JITTER_MIN = 5

    /** PendingIntent com FLAG_UPDATE_CURRENT para schedule. */
    fun pendingIntent(context: Context): PendingIntent {
        val intent = buildIntent(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        return PendingIntent.getBroadcast(context, REQ_CODE, intent, flags)
    }

    /** PendingIntent com FLAG_NO_CREATE — retorna null se nenhum alarme está agendado. */
    fun pendingIntentNoCreate(context: Context): PendingIntent? {
        val intent = buildIntent(context)
        val flags = PendingIntent.FLAG_NO_CREATE or immutableFlag()
        return PendingIntent.getBroadcast(context, REQ_CODE, intent, flags)
    }

    fun isScheduled(context: Context): Boolean = pendingIntentNoCreate(context) != null

    private fun buildIntent(context: Context): Intent =
        Intent(context, MidnightRolloverReceiver::class.java).apply {
            action = ACTION_ROLLOVER
        }

    /**
     * Agenda o alarme para a próxima janela de 23:55 + 0..5min.
     * Se já passou das 23:55 hoje, agenda para amanhã.
     * Tenta setExactAndAllowWhileIdle; cai pra setWindow se SCHEDULE_EXACT_ALARM não granted (Android 12+).
     */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val triggerAt = nextTriggerMillis()
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true

        if (canExact) {
            // setExactAndAllowWhileIdle sobrevive ao Doze do MIUI/HyperOS.
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Log.w(TAG, "RolloverScheduler.schedule: exact allowed; triggerAt=$triggerAt")
        } else {
            // Fallback: janela de ~10 min. Sem permissão SCHEDULE_EXACT_ALARM.
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10L * 60L * 1000L, pi)
            Log.w(TAG, "RolloverScheduler.schedule: exact denied; using setWindow 10min triggerAt=$triggerAt")
        }
    }

    /**
     * Cancela o alarme (se agendado). Idempotente: no-op se nenhum alarme estiver agendado.
     * Mantém os dados já persistidos em SharedPreferences — só para o agendamento futuro.
     */
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentNoCreate(context)
        cancelAlarm(am, pi)
    }

    /**
     * Versão testável: recebe AlarmManager + PendingIntent diretamente.
     * Separada de [cancel] para permitir testes JVM sem Robolectric.
     */
    internal fun cancelAlarm(am: AlarmManager, pi: PendingIntent?) {
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
            Log.w(TAG, "RolloverScheduler.cancelAlarm: canceled")
        } else {
            Log.w(TAG, "RolloverScheduler.cancelAlarm: no PendingIntent; nothing to cancel")
        }
    }

    /**
     * Próximo instante do dia às 23:55 + jitter (0..5 min).
     * Se já passou das 23:55 hoje, agenda para amanhã.
     */
    internal fun nextTriggerMillis(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, HOUR)
            set(java.util.Calendar.MINUTE, MINUTE)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val jitterMs = (Math.random() * MAX_JITTER_MIN * 60 * 1000).toLong()
        cal.add(java.util.Calendar.MILLISECOND, jitterMs.toInt())
        val now = java.util.Calendar.getInstance().timeInMillis
        if (cal.timeInMillis <= now) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
