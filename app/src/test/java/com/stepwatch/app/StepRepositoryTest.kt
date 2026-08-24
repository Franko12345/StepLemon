/*
 * StepRepository Zepp parser tests (#06).
 *
 * These tests intentionally cover only what is safe to test as pure JVM:
 *
 * - normalizeDate() — pure String manipulation, no Android types touched.
 * - Schema acceptance — currently tested manually via the in-app "Debug: schema
 *   Zepp" button (ticket 05). A future PR with Robolectric could add Cursor
 *   stubbing here, but Mockito + the AGP android.jar stub returns null for
 *   MatrixCursor.getColumnNames(), which makes the parser's column discovery
 *   throw before our assertion can run. See ADR 0005.
 *
 * Keep these tests pure. Manual schema coverage stays in the dump flow.
 */
package com.stepwatch.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StepRepositoryTest {

    /**
     * Tests normalizeDate() — the only pure-Kotlin method in StepRepository
     * that's worth verifying. All other parse logic depends on Android's
     * Cursor / MatrixCursor which the JVM stub doesn't implement.
     */
    private fun repo(): StepRepository {
        val ctx = org.mockito.Mockito.mock(android.content.Context::class.java, org.mockito.Mockito.RETURNS_DEFAULTS)
        val stubManager = org.mockito.Mockito.mock(android.hardware.SensorManager::class.java)
        org.mockito.Mockito.`when`(ctx.getSystemService(android.content.Context.SENSOR_SERVICE))
            .thenReturn(stubManager)
        val stubPrefs = org.mockito.Mockito.mock(android.content.SharedPreferences::class.java)
        org.mockito.Mockito.`when`(ctx.getSharedPreferences(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(stubPrefs)
        return StepRepository(ctx)
    }

    @Test
    fun normalize_date_already_dashed_passthrough() {
        assertEquals("2025-08-24", repo().normalizeDate("2025-08-24"))
    }

    @Test
    fun normalize_date_compact_becomes_dashed() {
        assertEquals("2025-08-24", repo().normalizeDate("20250824"))
    }

    @Test
    fun normalize_date_non_date_passthrough() {
        assertEquals("not-a-date", repo().normalizeDate("not-a-date"))
    }

    @Test
    fun normalize_date_empty_returns_empty() {
        assertEquals("", repo().normalizeDate(""))
    }
}