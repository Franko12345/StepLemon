package com.stepwatch.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * v1.4 — opt-in midnight rollover (ADR 0008).
 *
 * Disparado diariamente entre 23:55 e 00:00 (jitter +5min) pelo AlarmManager
 * configurado em [RolloverScheduler].
 *
 * Fluxo (síncrono dentro de uma Thread off-main via goAsync):
 *  1. Lê Zepp (se instalado/autorizado) via [StepRepository.readZeppHistory].
 *  2. Lê sensor nativo via [StepRepository.readNativeStepsToday].
 *  3. Persiste cada dia não-zero no SharedPreferences "stepwatch_history"
 *     com chave "d_<yyyy-MM-dd>" e fonte ("zepp" ou "sensor").
 *  4. Re-agenda o próximo alarme (idempotente — sempre agenda o próximo).
 *
 * Sem foreground service. Sem notificação persistente. Sem loop.
 * Se o trabalho falhar, o alarme é re-agendado mesmo assim (tentar de novo amanhã).
 */
class MidnightRolloverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RolloverScheduler.ACTION_ROLLOVER) {
            // Não é pra nós — defensivo.
            return
        }
        val pending = goAsync()
        val appCtx = context.applicationContext
        Thread({
            try {
                doRollover(appCtx)
            } catch (e: Exception) {
                Log.w(TAG, "MidnightRolloverReceiver: rollover failed: ${e.message}")
            } finally {
                // Re-agenda mesmo em caso de erro (próxima tentativa é amanhã).
                try {
                    RolloverScheduler.schedule(appCtx)
                } catch (e: Exception) {
                    Log.w(TAG, "MidnightRolloverReceiver: reschedule failed: ${e.message}")
                }
                pending.finish()
            }
        }, "StepLemon-Rollover").start()
    }

    private fun doRollover(context: Context) {
        val repo = StepRepository(context)

        // 1) Zepp — lê os últimos 7 dias e persiste cada um > 0.
        //    readZeppHistory retorna null se Zepp não está instalado ou autorizado;
        //    isso é OK — caímos no sensor nativo no passo 2.
        val zeppHistory = try {
            repo.readZeppHistory(7)
        } catch (e: Exception) {
            Log.w(TAG, "MidnightRolloverReceiver: readZeppHistory threw: ${e.message}")
            null
        }
        val zeppDays = zeppHistory?.size ?: 0
        if (zeppHistory != null) {
            for (day in zeppHistory) {
                if (day.steps > 0L) {
                    repo.saveHistoryEntry(day.date, day.steps, "zepp")
                }
            }
        }

        // 2) Sensor nativo — captura o valor atual de "hoje".
        //    Se o receiver dispara antes da meia-noite (caso comum com jitter 0-5min),
        //    este é o valor final do dia. Se dispara logo após 00:00 (edge case),
        //    vai pro próximo dia — aceitável, é o que temos.
        val nativeToday = try {
            repo.readNativeStepsToday()
        } catch (e: Exception) {
            Log.w(TAG, "MidnightRolloverReceiver: readNativeStepsToday threw: ${e.message}")
            null
        }
        if (nativeToday != null && nativeToday > 0L) {
            val today = repo.todayDate()
            repo.saveHistoryEntry(today, nativeToday, "sensor")
        }

        Log.w(
            TAG,
            "MidnightRolloverReceiver: done; zeppDays=$zeppDays nativeToday=$nativeToday"
        )
    }

    companion object {
        private const val TAG = "StepWatch"
    }
}
