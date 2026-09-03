/*
 * StepRepository Zepp parser tests (#06) + opt-in rollover tests (ADR 0008).
 *
 * These tests intentionally cover only what is safe to test as pure JVM:
 *
 * - normalizeDate() — pure String manipulation, no Android types touched.
 * - Schema acceptance — currently tested manually via the in-app "Debug: schema
 *   Zepp" button (ticket 05). A future PR with Robolectric could add Cursor
 *   stubbing here, but Mockito + the AGP android.jar stub returns null for
 *   MatrixCursor.getColumnNames(), which makes the parser's column discovery
 *   throw before our assertion can run. See ADR 0005.
 * - Rollover persistence + AlarmManager cancel — pure SharedPreferences /
 *   AlarmManager interaction via Mockito. ADR 0008.
 *
 * Keep these tests pure. Manual schema coverage stays in the dump flow.
 */
package com.stepwatch.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.RETURNS_DEFAULTS

class StepRepositoryTest {

    /**
     * Tests normalizeDate() — the only pure-Kotlin method in StepRepository
     * that's worth verifying. All other parse logic depends on Android's
     * Cursor / MatrixCursor which the JVM stub doesn't implement.
     */
    private fun repo(): StepRepository {
        val ctx = mock(android.content.Context::class.java, RETURNS_DEFAULTS)
        val stubManager = mock(android.hardware.SensorManager::class.java)
        `when`(ctx.getSystemService(android.content.Context.SENSOR_SERVICE))
            .thenReturn(stubManager)
        val stubPrefs = mock(android.content.SharedPreferences::class.java)
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(stubPrefs)
        return StepRepository(ctx)
    }

    @Test
    fun normalize_date_already_dashed_passthrough() {
        assertEquals("2025-08-24", repo().normalizeDateForTest("2025-08-24"))
    }

    @Test
    fun normalize_date_compact_becomes_dashed() {
        assertEquals("2025-08-24", repo().normalizeDateForTest("20250824"))
    }

    @Test
    fun normalize_date_non_date_passthrough() {
        assertEquals("not-a-date", repo().normalizeDateForTest("not-a-date"))
    }

    @Test
    fun normalize_date_empty_returns_empty() {
        assertEquals("", repo().normalizeDateForTest(""))
    }

    // ---- v1.4 (ADR 0008): opt-in rollover ----

    /**
     * Constrói um StepRepository com SharedPreferences distintos por nome.
     * - "stepwatch" → sensor baseline
     * - "stepwatch_goals" → metas
     * - "stepwatch_history" → histórico local (alvo do teste)
     */
    private fun repoWithSeparatePrefs(
        historyPrefs: android.content.SharedPreferences
    ): StepRepository {
        val ctx = mock(android.content.Context::class.java, RETURNS_DEFAULTS)
        val stubManager = mock(android.hardware.SensorManager::class.java)
        `when`(ctx.getSystemService(android.content.Context.SENSOR_SERVICE))
            .thenReturn(stubManager)

        val sensorPrefs = mock(android.content.SharedPreferences::class.java)
        val goalPrefs = mock(android.content.SharedPreferences::class.java)
        // Importante: quando usar matchers, TODOS os args devem ser matchers.
        // Caso contrário Mockito lança InvalidUseOfMatchersException.
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            val name = invocation.arguments[0] as String
            when (name) {
                "stepwatch" -> sensorPrefs
                "stepwatch_goals" -> goalPrefs
                "stepwatch_history" -> historyPrefs
                else -> sensorPrefs
            }
        }

        return StepRepository(ctx)
    }

    @Test
    fun rollover_save_persists_to_history_prefs() {
        val historyPrefs = mock(android.content.SharedPreferences::class.java)
        val editor = mock(android.content.SharedPreferences.Editor::class.java)
        `when`(historyPrefs.edit()).thenReturn(editor)
        `when`(editor.putLong(anyString(), anyLong())).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)

        val repo = repoWithSeparatePrefs(historyPrefs)
        repo.saveHistoryEntry("2025-01-15", 8421L, "zepp")

        // Verifica que gravou com as chaves corretas e os valores esperados.
        verify(editor).putLong("d_2025-01-15", 8421L)
        verify(editor).putString("s_2025-01-15", "zepp")
        verify(editor).apply()
    }

    @Test
    fun rollover_cancel_calls_alarm_manager_cancel() {
        val am = mock(android.app.AlarmManager::class.java)
        // Variável tipada não-nullable pra resolver a ambiguidade
        // de overload cancel(PendingIntent) vs cancel(OnAlarmListener).
        val pi: android.app.PendingIntent = mock(android.app.PendingIntent::class.java)
        RolloverScheduler.cancelAlarm(am, pi)
        verify(am).cancel(pi)
        verify(pi).cancel()
    }

    @Test
    fun rollover_cancel_null_pi_is_noop() {
        val am = mock(android.app.AlarmManager::class.java)
        RolloverScheduler.cancelAlarm(am, null)
        // Sem PendingIntent, AlarmManager.cancel NÃO deve ser chamado.
        // any<PendingIntent>() desambigua vs cancel(OnAlarmListener).
        // Caminho completo pra não conflitar com kotlin.collections.any.
        verify(am, never()).cancel(
            org.mockito.ArgumentMatchers.any(android.app.PendingIntent::class.java)
        )
    }
}