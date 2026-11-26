package com.example.sport.core.ui.setup

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.TreadmillSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Regolazione del tapis roulant: Pendenza (%) e Velocità (km/h) con i passi rapidi. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TreadmillSetupFieldsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun treadmillRows_haveStepButtons_andSaveItalianValuesWithinLimits() {
        val rows = mutableStateListOf(*initialSetupRows(emptyList(), TreadmillSettings.PARAM_NAMES).toTypedArray())
        composeRule.setContent { SportTheme { SetupFields(rows = rows, notes = "", onNotesChange = {}) } }

        composeRule.onNodeWithText("Pendenza (%)").assertIsDisplayed()
        composeRule.onNodeWithText("Velocità (km/h)").assertIsDisplayed()

        // Pendenza: 6 digitato, poi +0,5 → 6,5.
        composeRule.onNodeWithContentDescription("Pendenza (%)").performTextReplacement("6")
        composeRule.onNodeWithContentDescription("+0,5 di Pendenza (%)").performClick()
        // Velocità: 5 digitato, +0,5 e +0,1 → 5,6; poi −0,1 → 5,5.
        composeRule.onNodeWithContentDescription("Velocità (km/h)").performTextReplacement("5")
        composeRule.onNodeWithContentDescription("+0,5 di Velocità (km/h)").performClick()
        composeRule.onNodeWithContentDescription("+0,1 di Velocità (km/h)").performClick()
        composeRule.onNodeWithContentDescription("−0,1 di Velocità (km/h)").performClick()
        composeRule.onNode(hasText("6,5") and hasSetTextAction()).assertIsDisplayed()
        composeRule.onNode(hasText("5,5") and hasSetTextAction()).assertIsDisplayed()

        assertEquals(
            listOf(MachineSetupParam(TreadmillSettings.INCLINE, "6,5"), MachineSetupParam(TreadmillSettings.SPEED, "5,5")),
            rows.toParams(),
        )
    }

    @Test
    fun outOfRangeValues_showAnError_andAreNotSaved() {
        val rows = mutableStateListOf(*initialSetupRows(emptyList(), TreadmillSettings.PARAM_NAMES).toTypedArray())
        composeRule.setContent { SportTheme { SetupFields(rows = rows, notes = "", onNotesChange = {}) } }

        composeRule.onNodeWithContentDescription("Pendenza (%)").performTextReplacement("55")
        composeRule.onNodeWithText("Valore fra 0 e 40").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("−0,5 di Velocità (km/h)").performClick()
        assertEquals("Non si scende sotto zero", "0", rows[1].value)
        assertEquals(listOf(MachineSetupParam(TreadmillSettings.INCLINE, ""), MachineSetupParam(TreadmillSettings.SPEED, "0")), rows.toParams())
    }

    @Test
    fun savedProfileWithoutTreadmillRows_getsThemBack_beforeTheOtherEntries() {
        val rows = initialSetupRows(listOf(MachineSetupParam("Note cinghia", "tesa")), TreadmillSettings.PARAM_NAMES)
        assertEquals(listOf(TreadmillSettings.INCLINE, TreadmillSettings.SPEED, "Note cinghia"), rows.map { it.name })
    }
}
