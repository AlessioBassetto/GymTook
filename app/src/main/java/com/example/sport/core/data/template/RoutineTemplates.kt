package com.example.sport.core.data.template

import com.example.sport.core.model.DifficultyLevel
import com.example.sport.core.model.SplitType
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

data class TemplateExercise(
    val exerciseId: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    /** Null = recupero di default dell'esercizio. */
    val restSeconds: Int? = null,
)

data class TemplateDay(
    val name: String,
    val weekdays: List<DayOfWeek>,
    val exercises: List<TemplateExercise>,
)

/**
 * Scheda di partenza. Usa solo esercizi del catalogo curato (id stabili, contenuto in italiano):
 * un test verifica che ogni id esista nel catalogo impacchettato.
 */
data class RoutineTemplate(
    val id: String,
    val name: String,
    val description: String,
    val splitType: SplitType,
    val level: DifficultyLevel,
    val days: List<TemplateDay>,
) {
    val sessionsPerWeek: Int get() = days.sumOf { it.weekdays.size }
}

object RoutineTemplates {

    private fun ex(id: String, sets: Int, repsMin: Int, repsMax: Int, rest: Int? = null) =
        TemplateExercise(id, sets, repsMin, repsMax, rest)

    val pushPullLegs = RoutineTemplate(
        id = "ppl_3",
        name = "Push Pull Legs",
        description = "Spinta, tirata e gambe in tre giorni: ogni muscolo una volta a settimana con buon volume. " +
            "Ideale per imparare i multiarticolari.",
        splitType = SplitType.PUSH_PULL_LEGS,
        level = DifficultyLevel.BEGINNER,
        days = listOf(
            TemplateDay(
                name = "Push",
                weekdays = listOf(MONDAY),
                exercises = listOf(
                    ex("barbell_bench_press", 4, 6, 8),
                    ex("incline_dumbbell_press", 3, 8, 10),
                    ex("seated_dumbbell_shoulder_press", 3, 8, 10),
                    ex("dumbbell_lateral_raise", 3, 12, 15),
                    ex("cable_triceps_pushdown", 3, 10, 12),
                    ex("overhead_dumbbell_triceps_extension", 2, 10, 12),
                ),
            ),
            TemplateDay(
                name = "Pull",
                weekdays = listOf(WEDNESDAY),
                exercises = listOf(
                    ex("lat_pulldown", 4, 8, 10),
                    ex("barbell_row", 3, 8, 10),
                    ex("seated_cable_row", 3, 10, 12),
                    ex("face_pull", 3, 12, 15),
                    ex("barbell_curl", 3, 8, 10),
                    ex("dumbbell_hammer_curl", 2, 10, 12),
                ),
            ),
            TemplateDay(
                name = "Gambe",
                weekdays = listOf(FRIDAY),
                exercises = listOf(
                    ex("barbell_back_squat", 4, 6, 8),
                    ex("romanian_deadlift", 3, 8, 10),
                    ex("leg_press", 3, 10, 12),
                    ex("lying_leg_curl", 3, 10, 12),
                    ex("leg_extension", 2, 12, 15),
                    ex("standing_calf_raise_machine", 4, 10, 15),
                ),
            ),
        ),
    )

    val upperLower = RoutineTemplate(
        id = "upper_lower_4",
        name = "Upper / Lower",
        description = "Quattro sedute: parte alta e parte bassa due volte a settimana. Il miglior compromesso " +
            "tra frequenza e recupero per chi si allena con costanza.",
        splitType = SplitType.UPPER_LOWER,
        level = DifficultyLevel.INTERMEDIATE,
        days = listOf(
            TemplateDay(
                name = "Upper A",
                weekdays = listOf(MONDAY),
                exercises = listOf(
                    ex("barbell_bench_press", 4, 6, 8),
                    ex("barbell_row", 4, 6, 8),
                    ex("seated_dumbbell_shoulder_press", 3, 8, 10),
                    ex("lat_pulldown", 3, 10, 12),
                    ex("ez_bar_skull_crusher", 2, 10, 12),
                    ex("barbell_curl", 2, 10, 12),
                ),
            ),
            TemplateDay(
                name = "Lower A",
                weekdays = listOf(TUESDAY),
                exercises = listOf(
                    ex("barbell_back_squat", 4, 6, 8),
                    ex("romanian_deadlift", 3, 8, 10),
                    ex("leg_extension", 3, 12, 15),
                    ex("seated_leg_curl", 3, 10, 12),
                    ex("seated_calf_raise", 4, 12, 15),
                    ex("cable_crunch", 3, 12, 15),
                ),
            ),
            TemplateDay(
                name = "Upper B",
                weekdays = listOf(THURSDAY),
                exercises = listOf(
                    ex("incline_dumbbell_press", 4, 8, 10),
                    ex("chest_supported_machine_row", 4, 8, 10),
                    ex("pull_up", 3, 6, 10),
                    ex("dumbbell_lateral_raise", 3, 12, 15),
                    ex("cable_triceps_pushdown", 3, 10, 12),
                    ex("incline_dumbbell_curl", 3, 10, 12),
                ),
            ),
            TemplateDay(
                name = "Lower B",
                weekdays = listOf(FRIDAY),
                exercises = listOf(
                    ex("barbell_deadlift", 3, 4, 6),
                    ex("leg_press", 3, 10, 12),
                    ex("bulgarian_split_squat", 3, 8, 10),
                    ex("lying_leg_curl", 3, 10, 12),
                    ex("standing_calf_raise_machine", 4, 10, 12),
                    ex("hanging_leg_raise", 3, 10, 15),
                ),
            ),
        ),
    )

    val fullBody = RoutineTemplate(
        id = "full_body_3",
        name = "Full body",
        description = "Tutto il corpo in ogni seduta, alternando A e B. Poco tempo in palestra, alta frequenza: " +
            "perfetta per principianti e periodi impegnati.",
        splitType = SplitType.FULL_BODY,
        level = DifficultyLevel.BEGINNER,
        days = listOf(
            TemplateDay(
                name = "Full body A",
                weekdays = listOf(MONDAY, FRIDAY),
                exercises = listOf(
                    ex("barbell_back_squat", 3, 6, 8),
                    ex("barbell_bench_press", 3, 6, 8),
                    ex("barbell_row", 3, 8, 10),
                    ex("seated_dumbbell_shoulder_press", 2, 10, 12),
                    ex("lying_leg_curl", 2, 10, 12),
                    ex("crunch", 3, 12, 15),
                ),
            ),
            TemplateDay(
                name = "Full body B",
                weekdays = listOf(WEDNESDAY),
                exercises = listOf(
                    ex("romanian_deadlift", 3, 8, 10),
                    ex("incline_dumbbell_press", 3, 8, 10),
                    ex("lat_pulldown", 3, 8, 10),
                    ex("leg_press", 3, 10, 12),
                    ex("dumbbell_lateral_raise", 2, 12, 15),
                    ex("cable_triceps_pushdown", 2, 10, 12),
                ),
            ),
        ),
    )

    val all: List<RoutineTemplate> = listOf(pushPullLegs, upperLower, fullBody)
}
