package com.example.sport.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.BuildConfig
import com.example.sport.R
import com.example.sport.core.backup.BackupSummary
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.EffortScale
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.settings.UserSettings
import com.example.sport.core.update.ManualCheckResult
import com.example.sport.core.ui.format.Formatters

@Immutable
data class SettingsActions(
    val onBack: () -> Unit,
    val onWeightUnit: (WeightUnit) -> Unit,
    val onEffortScale: (EffortScale) -> Unit,
    val onExport: () -> Unit,
    val onRestore: () -> Unit,
    val onConfirmRestore: () -> Unit,
    val onCancelRestore: () -> Unit,
    val onCheckUpdates: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pending by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Selettori di sistema (Storage Access Framework): nessun permesso di archiviazione.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        uri?.let(viewModel::onExport)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onRestoreFilePicked)
    }

    LaunchedEffect(viewModel) {
        val resources = context.resources
        fun counts(summary: BackupSummary) = arrayOf(
            resources.getQuantityString(R.plurals.count_workouts, summary.sessions, summary.sessions),
            resources.getQuantityString(R.plurals.count_weighins, summary.bodyWeights, summary.bodyWeights),
        )
        viewModel.events.collect { event ->
            val message = when (event) {
                is SettingsEvent.Exported -> context.getString(R.string.settings_exported, *counts(event.summary))
                is SettingsEvent.Restored -> context.getString(R.string.settings_restored, *counts(event.summary))
                SettingsEvent.ExportFailed -> context.getString(R.string.settings_export_failed)
                SettingsEvent.RestoreFailed -> context.getString(R.string.settings_restore_failed)
                SettingsEvent.InvalidFile -> context.getString(R.string.settings_invalid_file)
                is SettingsEvent.UpdateCheck -> context.getString(
                    when (event.result) {
                        ManualCheckResult.OFFLINE -> R.string.settings_update_offline
                        ManualCheckResult.FAILED -> R.string.settings_update_failed
                        ManualCheckResult.UP_TO_DATE, ManualCheckResult.FOUND -> R.string.settings_update_none
                    },
                )
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    SettingsContent(
        settings = settings,
        busy = busy,
        pendingRestore = pending,
        actions = SettingsActions(
            onBack = onBack,
            onWeightUnit = viewModel::onWeightUnit,
            onEffortScale = viewModel::onEffortScale,
            onExport = { exportLauncher.launch(viewModel.suggestedFileName()) },
            // Alcuni gestori di file non riconoscono il tipo JSON: si accettano anche testo e binario.
            onRestore = { restoreLauncher.launch(arrayOf(JSON_MIME, "text/*", "application/octet-stream")) },
            onConfirmRestore = viewModel::onConfirmRestore,
            onCancelRestore = viewModel::onCancelRestore,
            onCheckUpdates = viewModel::onCheckUpdates,
        ),
        snackbarHostState = snackbarHostState,
        updatesEnabled = viewModel.updatesEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    settings: UserSettings,
    busy: Boolean,
    pendingRestore: PendingRestore?,
    actions: SettingsActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    updatesEnabled: Boolean = false,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
                verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingL),
            ) {
                SettingsCard(stringResource(R.string.settings_units), stringResource(R.string.settings_units_hint)) {
                    ChoiceRow(
                        options = WeightUnit.entries,
                        selected = settings.weightUnit,
                        label = { unit ->
                            when (unit) {
                                WeightUnit.KG -> stringResource(R.string.settings_kg) + " (kg)"
                                WeightUnit.LB -> stringResource(R.string.settings_lb) + " (lb)"
                            }
                        },
                        onSelect = actions.onWeightUnit,
                    )
                }
                SettingsCard(stringResource(R.string.settings_effort), stringResource(R.string.settings_effort_hint)) {
                    ChoiceRow(
                        options = EffortScale.entries,
                        selected = settings.effortScale,
                        label = { scale ->
                            when (scale) {
                                EffortScale.RPE -> stringResource(R.string.settings_rpe)
                                EffortScale.RIR -> stringResource(R.string.settings_rir)
                            }
                        },
                        onSelect = actions.onEffortScale,
                    )
                }
                SettingsCard(stringResource(R.string.settings_data), stringResource(R.string.settings_data_hint)) {
                    SecondaryActionButton(
                        text = stringResource(R.string.settings_export),
                        onClick = actions.onExport,
                        enabled = !busy,
                        icon = Icons.Outlined.FileUpload,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryActionButton(
                        text = stringResource(R.string.settings_restore),
                        onClick = actions.onRestore,
                        enabled = !busy,
                        icon = Icons.Outlined.FileDownload,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsCard(stringResource(R.string.settings_about), stringResource(R.string.settings_offline)) {
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (updatesEnabled) {
                        SecondaryActionButton(
                            text = stringResource(R.string.settings_check_updates),
                            onClick = actions.onCheckUpdates,
                            enabled = !busy,
                            icon = Icons.Outlined.SystemUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }
    pendingRestore?.let { RestoreDialog(it, actions) }
}

@Composable
private fun SettingsCard(title: String, hint: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
        SectionHeader(title)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
                content()
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Scelta esclusiva a pulsanti segmentati, alti 56dp come il resto dell'app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.heightIn(min = SportDimens.TouchTargetMin),
            ) {
                Text(label(option))
            }
        }
    }
}

@Composable
private fun RestoreDialog(pending: PendingRestore, actions: SettingsActions) {
    val summary = pending.summary
    AlertDialog(
        onDismissRequest = actions.onCancelRestore,
        title = { Text(stringResource(R.string.settings_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
                pending.exportedOn?.let { Text(stringResource(R.string.settings_restore_dated, Formatters.longDate(it))) }
                Text(
                    stringResource(
                        R.string.settings_restore_contents,
                        pluralStringResource(R.plurals.count_workouts, summary.sessions, summary.sessions),
                        pluralStringResource(R.plurals.count_routines, summary.routines, summary.routines),
                        pluralStringResource(R.plurals.count_weighins, summary.bodyWeights, summary.bodyWeights),
                        pluralStringResource(R.plurals.count_diet_plans, summary.dietPlans, summary.dietPlans),
                        pluralStringResource(R.plurals.count_custom_exercises, summary.customExercises, summary.customExercises),
                        pluralStringResource(R.plurals.count_user_foods, summary.userFoods, summary.userFoods),
                        pluralStringResource(R.plurals.count_diary_entries, summary.diaryEntries, summary.diaryEntries),
                    ),
                )
                Text(stringResource(R.string.settings_restore_warning), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirmRestore) { Text(stringResource(R.string.settings_restore_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onCancelRestore) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

private const val JSON_MIME = "application/json"
