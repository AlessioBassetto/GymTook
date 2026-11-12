package com.example.sport.core.data.progress

import com.example.sport.core.database.relation.CompletedSetRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class ProgressMathTest {

    private val day0 = LocalDate.of(2026, 10, 1)
    private val t0 = Instant.parse("2026-10-01T07:00:00Z")

    private fun set(exercise: String, session: Long, dayOffset: Long, weight: Double, reps: Int) = CompletedSetRow(
        exerciseId = exercise,
        exerciseName = exercise.replaceFirstChar { it.uppercase() },
        sessionId = session,
        performedAt = t0.plus(Duration.ofDays(dayOffset)),
        weightKg = weight,
        reps = reps,
    )

    @Test
    fun oneRepMax_usesEpley_upToTwelveReps() {
        assertEquals(100.0, estimateOneRepMax(100.0, 1)!!, 1e-9)
        assertEquals(116.6667, estimateOneRepMax(100.0, 5)!!, 1e-4)
        assertEquals(140.0, estimateOneRepMax(100.0, 12)!!, 1e-9)
        assertNull("Oltre 12 ripetizioni la stima non è affidabile", estimateOneRepMax(100.0, 13))
        assertNull(estimateOneRepMax(0.0, 5))
        assertNull(estimateOneRepMax(80.0, 0))
    }

    @Test
    fun movingAverage_usesCalendarDays_notEntryCount() {
        val entries = listOf(
            DatedWeight(day0.plusDays(8), 79.0),
            DatedWeight(day0, 81.0),
            DatedWeight(day0.plusDays(2), 80.0),
            DatedWeight(day0.plusDays(6), 79.5),
        )
        val points = movingAverage(entries)
        assertEquals(listOf(day0, day0.plusDays(2), day0.plusDays(6), day0.plusDays(8)), points.map { it.date })
        assertEquals(81.0, points[0].averageKg, 1e-9)
        assertEquals(80.5, points[1].averageKg, 1e-9)
        assertEquals("Giorni 0–6: tre pesate", (81.0 + 80.0 + 79.5) / 3, points[2].averageKg, 1e-9)
        assertEquals("Giorni 2–8: il giorno 0 esce dalla finestra", (80.0 + 79.5 + 79.0) / 3, points[3].averageKg, 1e-9)
    }

    @Test
    fun weeklyChange_needsAWeekOfHistory() {
        val shortHistory = movingAverage(listOf(DatedWeight(day0, 80.0), DatedWeight(day0.plusDays(3), 79.0)))
        assertNull(weeklyChange(shortHistory))

        val points = movingAverage((0..14L).map { DatedWeight(day0.plusDays(it), 80.0 - it * 0.1) })
        val change = weeklyChange(points)!!
        assertEquals("Media in calo di 0,7 kg in una settimana", -0.7, change, 1e-9)
    }

    @Test
    fun records_takeTheFirstTimeAValueIsReached() {
        val sets = listOf(
            set("panca", session = 1, dayOffset = 0, weight = 100.0, reps = 5),
            set("panca", session = 1, dayOffset = 0, weight = 100.0, reps = 5),
            set("panca", session = 2, dayOffset = 3, weight = 105.0, reps = 3),
            set("panca", session = 3, dayOffset = 7, weight = 105.0, reps = 3),
            set("panca", session = 3, dayOffset = 7, weight = 90.0, reps = 12),
            set("squat", session = 2, dayOffset = 3, weight = 140.0, reps = 5),
        )
        val records = personalRecords(sets)
        assertEquals("Prima l'esercizio con il record più recente", listOf("panca", "squat"), records.map { it.exerciseId })

        val bench = records.first()
        assertEquals(105.0, bench.heaviestSet.valueKg, 0.0)
        assertEquals("Il record eguagliato nella sessione 3 non conta", 2L, bench.heaviestSet.sessionId)
        assertEquals("90 × 12 = 126 kg stimati batte 105 × 3 = 115,5", 3L, bench.bestOneRepMax!!.sessionId)
        assertEquals(126.0, bench.bestOneRepMax.valueKg, 1e-9)
        assertEquals("105 × 3 + 90 × 12 batte 2 × 100 × 5", 1395.0, bench.bestSessionVolume.valueKg, 0.0)
        assertEquals(3L, bench.bestSessionVolume.sessionId)
        assertEquals(t0.plus(Duration.ofDays(7)), bench.latestAchievedAt)
    }

    @Test
    fun heaviestSet_prefersMoreRepsAtTheSameWeight() {
        val records = personalRecords(
            listOf(
                set("curl", 1, 0, 40.0, 8),
                set("curl", 2, 2, 40.0, 10),
                set("curl", 3, 4, 40.0, 9),
            ),
        ).single()
        assertEquals(10, records.heaviestSet.reps)
        assertEquals(2L, records.heaviestSet.sessionId)
    }

    @Test
    fun trends_compareFirstAndLatestSession() {
        val trends = exerciseTrends(
            listOf(
                set("panca", 1, 0, 100.0, 5),
                set("squat", 2, 2, 140.0, 5),
                set("panca", 3, 7, 105.0, 5),
                set("panca", 3, 7, 80.0, 15),
            ),
        )
        assertEquals(listOf("panca", "squat"), trends.map { it.exerciseId })
        val bench = trends.first()
        assertEquals(2, bench.sessionCount)
        assertEquals(122.5, bench.latestOneRepMax!!, 1e-9)
        assertEquals(122.5 - 116.6666667, bench.oneRepMaxChange!!, 1e-6)
        assertNull("Una sola sessione: nessuna variazione", trends.last().oneRepMaxChange)
    }
}
