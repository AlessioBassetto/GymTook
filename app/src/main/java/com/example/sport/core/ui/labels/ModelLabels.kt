package com.example.sport.core.ui.labels

import androidx.annotation.StringRes
import com.example.sport.R
import com.example.sport.core.model.DifficultyLevel
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.MealType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import com.example.sport.core.model.SetType
import com.example.sport.core.model.SplitType

/*
 * Etichette localizzate dei valori di dominio. Il modello resta indipendente dalla lingua; i `when`
 * esaustivi fanno fallire la compilazione se si aggiunge un valore senza la relativa etichetta.
 */

@StringRes
fun Equipment.labelRes(): Int = when (this) {
    Equipment.BODYWEIGHT -> R.string.equipment_bodyweight
    Equipment.DUMBBELL -> R.string.equipment_dumbbell
    Equipment.CABLE -> R.string.equipment_cable
    Equipment.MACHINE -> R.string.equipment_machine
    Equipment.BARBELL -> R.string.equipment_barbell
    Equipment.SMITH_MACHINE -> R.string.equipment_smith_machine
    Equipment.KETTLEBELL -> R.string.equipment_kettlebell
    Equipment.RESISTANCE_BAND -> R.string.equipment_resistance_band
    Equipment.CARDIO_MACHINE -> R.string.equipment_cardio_machine
    Equipment.OTHER -> R.string.equipment_other
}

@StringRes
fun MuscleGroup.labelRes(): Int = when (this) {
    MuscleGroup.ABS -> R.string.muscle_group_abs
    MuscleGroup.FOREARMS -> R.string.muscle_group_forearms
    MuscleGroup.BICEPS -> R.string.muscle_group_biceps
    MuscleGroup.NECK -> R.string.muscle_group_neck
    MuscleGroup.LATS -> R.string.muscle_group_lats
    MuscleGroup.LOWER_BACK -> R.string.muscle_group_lower_back
    MuscleGroup.UPPER_BACK -> R.string.muscle_group_upper_back
    MuscleGroup.CHEST -> R.string.muscle_group_chest
    MuscleGroup.SHOULDERS -> R.string.muscle_group_shoulders
    MuscleGroup.TRAPS -> R.string.muscle_group_traps
    MuscleGroup.TRICEPS -> R.string.muscle_group_triceps
    MuscleGroup.ABDUCTORS -> R.string.muscle_group_abductors
    MuscleGroup.ADDUCTORS -> R.string.muscle_group_adductors
    MuscleGroup.HAMSTRINGS -> R.string.muscle_group_hamstrings
    MuscleGroup.GLUTES -> R.string.muscle_group_glutes
    MuscleGroup.CALVES -> R.string.muscle_group_calves
    MuscleGroup.QUADRICEPS -> R.string.muscle_group_quadriceps
    MuscleGroup.CARDIO_FULL_BODY -> R.string.muscle_group_cardio_full_body
}

@StringRes
fun Muscle.labelRes(): Int = when (this) {
    Muscle.ABS_UPPER -> R.string.muscle_abs_upper
    Muscle.ABS_LOWER -> R.string.muscle_abs_lower
    Muscle.OBLIQUES -> R.string.muscle_obliques
    Muscle.FOREARMS -> R.string.muscle_forearms
    Muscle.BICEPS -> R.string.muscle_biceps
    Muscle.NECK -> R.string.muscle_neck
    Muscle.LATS -> R.string.muscle_lats
    Muscle.LOWER_BACK -> R.string.muscle_lower_back
    Muscle.UPPER_BACK -> R.string.muscle_upper_back
    Muscle.CHEST_UPPER -> R.string.muscle_chest_upper
    Muscle.CHEST_MIDDLE -> R.string.muscle_chest_middle
    Muscle.CHEST_LOWER -> R.string.muscle_chest_lower
    Muscle.SHOULDERS_FRONT -> R.string.muscle_shoulders_front
    Muscle.SHOULDERS_SIDE -> R.string.muscle_shoulders_side
    Muscle.SHOULDERS_REAR -> R.string.muscle_shoulders_rear
    Muscle.TRAPS -> R.string.muscle_traps
    Muscle.TRICEPS -> R.string.muscle_triceps
    Muscle.ABDUCTORS -> R.string.muscle_abductors
    Muscle.ADDUCTORS -> R.string.muscle_adductors
    Muscle.HAMSTRINGS -> R.string.muscle_hamstrings
    Muscle.GLUTES -> R.string.muscle_glutes
    Muscle.CALVES -> R.string.muscle_calves
    Muscle.QUADRICEPS -> R.string.muscle_quadriceps
    Muscle.CARDIO -> R.string.muscle_cardio
    Muscle.FULL_BODY -> R.string.muscle_full_body
}

@StringRes
fun DifficultyLevel.labelRes(): Int = when (this) {
    DifficultyLevel.BEGINNER -> R.string.level_beginner
    DifficultyLevel.INTERMEDIATE -> R.string.level_intermediate
    DifficultyLevel.ADVANCED -> R.string.level_advanced
}

@StringRes
fun Mechanic.labelRes(): Int = when (this) {
    Mechanic.COMPOUND -> R.string.mechanic_compound
    Mechanic.ISOLATION -> R.string.mechanic_isolation
}

@StringRes
fun ExerciseCategory.labelRes(): Int = when (this) {
    ExerciseCategory.STRENGTH -> R.string.category_strength
    ExerciseCategory.OLYMPIC -> R.string.category_olympic
    ExerciseCategory.PLYOMETRIC -> R.string.category_plyometric
    ExerciseCategory.CARDIO -> R.string.category_cardio
}

@StringRes
fun ExerciseTrackingType.labelRes(): Int = when (this) {
    ExerciseTrackingType.WEIGHT_REPS -> R.string.tracking_weight_reps
    ExerciseTrackingType.BODYWEIGHT_REPS -> R.string.tracking_bodyweight_reps
    ExerciseTrackingType.ASSISTED_BODYWEIGHT -> R.string.tracking_assisted
    ExerciseTrackingType.DURATION -> R.string.tracking_duration
    ExerciseTrackingType.DISTANCE_DURATION -> R.string.tracking_distance_duration
    ExerciseTrackingType.INCLINE_TIME -> R.string.tracking_incline_time
}

@StringRes
fun SplitType.labelRes(): Int = when (this) {
    SplitType.PUSH_PULL_LEGS -> R.string.split_push_pull_legs
    SplitType.UPPER_LOWER -> R.string.split_upper_lower
    SplitType.FULL_BODY -> R.string.split_full_body
    SplitType.BRO_SPLIT -> R.string.split_bro_split
    SplitType.CUSTOM -> R.string.split_custom
}

@StringRes
fun MealType.labelRes(): Int = when (this) {
    MealType.BREAKFAST -> R.string.meal_breakfast
    MealType.MORNING_SNACK -> R.string.meal_morning_snack
    MealType.LUNCH -> R.string.meal_lunch
    MealType.AFTERNOON_SNACK -> R.string.meal_afternoon_snack
    MealType.DINNER -> R.string.meal_dinner
    MealType.OTHER -> R.string.meal_other
}

@StringRes
fun SetType.labelRes(): Int = when (this) {
    SetType.WARMUP -> R.string.set_type_warmup
    SetType.WORKING -> R.string.set_type_working
    SetType.DROP -> R.string.set_type_drop
    SetType.FAILURE -> R.string.set_type_failure
}

@StringRes
fun FoodCategory.labelRes(): Int = when (this) {
    FoodCategory.BREAKFAST_SWEETS -> R.string.food_category_breakfast
    FoodCategory.FIRST_COURSES -> R.string.food_category_first_courses
    FoodCategory.MEAT -> R.string.food_category_meat
    FoodCategory.FISH -> R.string.food_category_fish
    FoodCategory.VEGETARIAN -> R.string.food_category_vegetarian
    FoodCategory.SIDES -> R.string.food_category_sides
    FoodCategory.FRUIT_SNACKS_DRINKS -> R.string.food_category_fruit_snacks
    FoodCategory.OTHER -> R.string.food_category_other
}
