package com.example.sport.core.data.progress

import com.example.sport.core.database.relation.InclineSetRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class InclineProgressTest {

    private val t0 = Instant.parse("2026-11-02T07:00:00Z")

    private fun row(session: Long, day: Long, incline: Double?, seconds: Int) =
        InclineSetRow(sessionId = session, performedAt = t0.plus(Duration.ofDays(day)), inclinePercent = incline, durationSeconds = seconds)

    private fun session(incline: Double, seconds: Int, id: Long = 1) =
        InclineSession(sessionId = id, performedAt = t0, totalSeconds = seconds, averageIncline = incline, maxIncline = incline, setCount = 1)

    @Test
    fun sessions_sumTheTime_andWeightTheInclineByMinutes() {
        val sessions = inclineSessions(
            listOf(
                row(session = 2, day = 3, incline = 12.0, seconds = 20 * 60),
                row(session = 2, day = 3, incline = 15.0, seconds = 5 * 60),
                row(session = 1, day = 0, incline = 10.0, seconds = 25 * 60),
            ),
        )

        assertEquals("Dalla più vecchia", listOf(1L, 2L), sessions.map { it.sessionId })
        val second = sessions[1]
        assertEquals(25 * 60, second.totalSeconds)
        assertEquals("20 min al 12% pesano più di 5 min al 15%", 12.6, second.averageIncline, 1e-9)
        assertEquals(15.0, second.maxIncline, 0.0)
        assertEquals(2, second.setCount)
    }

    @Test
    fun sessions_treatAMissingInclineAsFlat() {
        val session = inclineSessions(listOf(row(1, 0, null, 600), row(1, 0, 10.0, 600))).single()
        assertEquals(5.0, session.averageIncline, 1e-9)
        assertEquals(10.0, session.maxIncline, 0.0)
    }

    @Test
    fun trend_recognisesTheThreeWaysToImprove() {
        val base = session(incline = 10.0, seconds = 25 * 60)

        assertEquals(InclineTrend.FIRST, inclineTrend(null, base))
        assertEquals(InclineTrend.LONGER_SAME_INCLINE, inclineTrend(base, session(10.0, 30 * 60)))
        assertEquals(InclineTrend.STEEPER_SAME_TIME, inclineTrend(base, session(11.0, 25 * 60)))
        assertEquals(InclineTrend.STEEPER_AND_LONGER, inclineTrend(base, session(11.0, 28 * 60)))
        listOf(InclineTrend.LONGER_SAME_INCLINE, InclineTrend.STEEPER_SAME_TIME, InclineTrend.STEEPER_AND_LONGER)
            .forEach { assertTrue("$it è un miglioramento", it.isImprovement) }
    }

    @Test
    fun trend_tradeOffsAndDropsAreNotImprovements() {
        val base = session(incline = 10.0, seconds = 25 * 60)

        assertEquals(InclineTrend.STEEPER, inclineTrend(base, session(12.0, 20 * 60)))
        assertEquals(InclineTrend.LONGER, inclineTrend(base, session(8.0, 35 * 60)))
        assertEquals(InclineTrend.LOWER, inclineTrend(base, session(9.0, 25 * 60)))
        assertEquals(InclineTrend.LOWER, inclineTrend(base, session(10.0, 20 * 60)))
        listOf(InclineTrend.FIRST, InclineTrend.STEEPER, InclineTrend.LONGER, InclineTrend.SAME, InclineTrend.LOWER)
            .forEach { assertFalse("$it non è un miglioramento", it.isImprovement) }
    }

    @Test
    fun trend_ignoresRoundingDifferences() {
        val base = session(incline = 10.0, seconds = 25 * 60)
        assertEquals("Un quarto di punto e mezzo minuto sono 'uguale'", InclineTrend.SAME, inclineTrend(base, session(10.2, 25 * 60 + 30)))
        assertEquals("Mezzo punto (uno scatto del tapis) è già più pendenza", InclineTrend.STEEPER_SAME_TIME, inclineTrend(base, session(10.5, 25 * 60)))
    }
}
