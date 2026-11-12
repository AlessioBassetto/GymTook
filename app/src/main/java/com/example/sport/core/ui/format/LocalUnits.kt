package com.example.sport.core.ui.format

import androidx.compose.runtime.compositionLocalOf
import com.example.sport.core.model.EffortScale
import com.example.sport.core.model.WeightUnit

/**
 * Unità di peso scelta nelle impostazioni, fornita alla radice dell'app. I valori arrivano sempre
 * in kg: le schermate la passano a [Formatters] solo per mostrarli. Nei test vale kg.
 */
val LocalWeightUnit = compositionLocalOf { WeightUnit.KG }

/** Scala dello sforzo (RPE o RIR) mostrata dal logger. */
val LocalEffortScale = compositionLocalOf { EffortScale.RPE }
