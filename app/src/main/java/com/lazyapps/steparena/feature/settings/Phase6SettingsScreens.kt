package com.lazyapps.steparena.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.R
import com.lazyapps.steparena.release.DataManagementRepository
import com.lazyapps.steparena.release.DataUsage
import kotlinx.coroutines.launch

@Composable
fun DataManagementScreen(onDeleted: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { DataManagementRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<DataUsage?>(null) }
    var messageRes by remember { mutableStateOf<Int?>(null) }
    var gameConfirm by remember { mutableIntStateOf(0) }
    var deleteConfirm by remember { mutableIntStateOf(0) }
    var resetConfirm by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    val deletePhrase = stringResource(R.string.data_delete_phrase)
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) scope.launch {
            messageRes = R.string.data_exporting
            messageRes = runCatching { repository.export(uri); R.string.data_exported }
                .getOrElse { R.string.data_export_failed }
        }
    }
    LaunchedEffect(Unit) { usage = runCatching { repository.usage() }.getOrNull() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
            .testTag("data_management_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.data_management_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.data_management_intro))
        usage?.let { UsageSummary(it) } ?: Text(stringResource(R.string.loading))
        Button(
            onClick = { export.launch("StepArena-${java.time.LocalDate.now()}.zip") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.data_export)) }
        OutlinedButton(
            onClick = { gameConfirm = 1 },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.data_reset_game)) }
        OutlinedButton(
            onClick = { deleteConfirm = 1 },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.data_delete_all)) }
        OutlinedButton(
            onClick = { resetConfirm = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.data_reset_settings)) }
        messageRes?.let { Text(stringResource(it)) }
    }
    if (gameConfirm > 0) AlertDialog(
        onDismissRequest = { gameConfirm = 0 },
        title = {
            Text(
                stringResource(
                    if (gameConfirm == 1) R.string.data_reset_game_confirm
                    else R.string.data_reset_game_reconfirm,
                ),
            )
        },
        text = { Text(stringResource(R.string.data_reset_game_explanation)) },
        confirmButton = {
            Button(onClick = {
                if (gameConfirm == 1) gameConfirm = 2 else scope.launch {
                    repository.resetGame()
                    gameConfirm = 0
                    usage = repository.usage()
                }
            }) {
                Text(stringResource(if (gameConfirm == 1) R.string.common_next else R.string.common_reset))
            }
        },
        dismissButton = {
            TextButton(onClick = { gameConfirm = 0 }) { Text(stringResource(R.string.common_cancel)) }
        },
    )
    if (deleteConfirm > 0) AlertDialog(
        onDismissRequest = { deleteConfirm = 0 },
        title = { Text(stringResource(R.string.data_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.data_delete_explanation))
                if (deleteConfirm == 2) OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text(stringResource(R.string.data_delete_phrase_label)) },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = deleteConfirm == 1 || phrase == deletePhrase,
                onClick = {
                    if (deleteConfirm == 1) deleteConfirm = 2 else scope.launch {
                        repository.deleteAll()
                        deleteConfirm = 0
                        onDeleted()
                    }
                },
            ) {
                Text(
                    stringResource(
                        if (deleteConfirm == 1) R.string.common_next
                        else R.string.data_delete_permanently,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { deleteConfirm = 0 }) { Text(stringResource(R.string.common_cancel)) }
        },
    )
    if (resetConfirm) AlertDialog(
        onDismissRequest = { resetConfirm = false },
        title = { Text(stringResource(R.string.data_reset_settings_confirm)) },
        text = { Text(stringResource(R.string.data_reset_settings_explanation)) },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    repository.resetSettings(keepOnboarding = true)
                    resetConfirm = false
                }
            }) { Text(stringResource(R.string.common_reset)) }
        },
        dismissButton = {
            TextButton(onClick = { resetConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun UsageSummary(usage: DataUsage) {
    val unavailable = stringResource(R.string.unavailable)
    val none = stringResource(R.string.none)
    Text(
        stringResource(
            R.string.data_usage_summary,
            usage.hourly,
            usage.daily,
            usage.sessions,
            usage.gaps,
            usage.processedExternal,
            usage.matches,
            usage.leagues,
            usage.seasons,
            usage.achievements,
            usage.notificationEvents,
            usage.databaseBytes?.let(::formatBytes) ?: unavailable,
            usage.dataStoreBytes?.let(::formatBytes) ?: unavailable,
            usage.oldestDate ?: none,
            usage.newestDate ?: none,
        ),
    )
}

private fun formatBytes(value: Long) = if (value < 1024) "${value} B" else "${value / 1024} KiB"

enum class InfoDocument { PRIVACY, TERMS, LICENSES, ABOUT }

@Composable
fun InfoDocumentScreen(document: InfoDocument) {
    val titleRes: Int
    val body: String
    when (document) {
        InfoDocument.PRIVACY -> {
            titleRes = R.string.info_privacy_title
            body = stringResource(R.string.info_privacy_body)
        }
        InfoDocument.TERMS -> {
            titleRes = R.string.info_terms_title
            body = stringResource(R.string.info_terms_body)
        }
        InfoDocument.LICENSES -> {
            titleRes = R.string.info_licenses_title
            body = stringResource(R.string.info_licenses_body)
        }
        InfoDocument.ABOUT -> {
            titleRes = R.string.info_about_title
            body = stringResource(
                R.string.info_about_body,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                java.time.ZoneId.systemDefault(),
            )
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, modifier = Modifier.padding(top = 16.dp))
    }
}
