package com.example.sport.feature.exercises

import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFiltersTest {

    @Test
    fun specificMuscles_replaceTheGroup_andAnotherGroupDropsThem() {
        val chest = LibraryFilters().selectGroup(MuscleGroup.CHEST)
        val upperChest = chest.toggleMuscle(Muscle.CHEST_UPPER)
        assertEquals("Muscolo dello stesso gruppo: il gruppo resta selezionato", MuscleGroup.CHEST, upperChest.group)
        assertEquals("Ma la query usa solo il muscolo specifico", emptySet<MuscleGroup>(), upperChest.toQuery("").muscleGroups)
        assertEquals(setOf(Muscle.CHEST_UPPER), upperChest.toQuery("").muscles)

        val plusTriceps = upperChest.toggleMuscle(Muscle.TRICEPS)
        assertNull("Muscolo di un altro gruppo: il chip del gruppo si spegne", plusTriceps.group)

        val shoulders = plusTriceps.selectGroup(MuscleGroup.SHOULDERS)
        assertEquals(emptySet<Muscle>(), shoulders.muscles)
        assertEquals(setOf(MuscleGroup.SHOULDERS), shoulders.toQuery("").muscleGroups)
    }

    @Test
    fun advancedCount_andClear_leaveQuickChipsAlone() {
        val filters = LibraryFilters(group = MuscleGroup.QUADRICEPS, onlyFavorites = true)
            .toggleEquipment(Equipment.MACHINE)
            .toggleEquipment(Equipment.CABLE)
            .toggleCategory(ExerciseCategory.STRENGTH)
            .copy(includeSecondary = true, onlyCustom = true)
        assertEquals(5, filters.advancedCount)

        val cleared = filters.clearAdvanced()
        assertEquals(0, cleared.advancedCount)
        assertEquals(MuscleGroup.QUADRICEPS, cleared.group)
        assertTrue(cleared.onlyFavorites)
    }

    @Test
    fun toQuery_mapsEveryField() {
        val query = LibraryFilters(
            equipment = setOf(Equipment.DUMBBELL),
            categories = setOf(ExerciseCategory.PLYOMETRIC),
            muscles = setOf(Muscle.GLUTES),
            includeSecondary = true,
            onlyFavorites = true,
            onlyCustom = true,
        ).toQuery("affondi")
        assertEquals("affondi", query.query)
        assertEquals(setOf(Equipment.DUMBBELL), query.equipment)
        assertEquals(setOf(ExerciseCategory.PLYOMETRIC), query.categories)
        assertEquals(setOf(Muscle.GLUTES), query.muscles)
        assertTrue(query.includeSecondaryMuscles && query.onlyFavorites && query.onlyCustom)
    }

    @Test
    fun encodeDecode_roundTrip_andToleratesGarbage() {
        val filters = LibraryFilters(group = MuscleGroup.BICEPS, equipment = setOf(Equipment.CABLE), onlyFavorites = true)
        assertEquals(filters, LibraryFilters.decode(filters.encode()))
        assertEquals(LibraryFilters(), LibraryFilters.decode("{non json"))
        assertEquals(LibraryFilters(), LibraryFilters.decode(null))
        assertTrue(LibraryFilters().isEmpty)
    }
}
