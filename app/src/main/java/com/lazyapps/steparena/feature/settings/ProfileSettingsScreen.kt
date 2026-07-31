package com.lazyapps.steparena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.activity.UserBodyProfile
import com.lazyapps.steparena.activity.DefaultUserBodyProfileValidator
import com.lazyapps.steparena.activity.DefaultStepLengthEstimator
import androidx.compose.ui.res.stringResource
import com.lazyapps.steparena.R
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.game.PlayerDisplayNamePolicy
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = (context.applicationContext as StepArenaApplication).profileRepository
    val identityRepository = (context.applicationContext as StepArenaApplication).playerIdentityRepository
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var stepLengthCm by remember { mutableStateOf("") }
    var automatic by remember { mutableStateOf(true) }
    var messageRes by remember { mutableStateOf<Int?>(null) }
    var savedProfile by remember { mutableStateOf<UserBodyProfile?>(null) }
    var savedDisplayName by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.profile_saved)
    val validator = remember { DefaultUserBodyProfileValidator() }
    LaunchedEffect(Unit) {
        identityRepository.current().let {
            displayName = it.displayName.orEmpty()
            savedDisplayName = it.displayName
        }
        repository.current().let {
            height = it.heightCm?.toString().orEmpty()
            weight = it.weightKg?.toString().orEmpty()
            stepLengthCm = it.manualStepLengthMeters?.times(100)?.toString().orEmpty()
            automatic = it.useAutomaticStepLength
            savedProfile = it
        }
    }
    val validation = validator.validate(height, weight, if (automatic) "" else stepLengthCm, automatic)
    val displayNameValidation = PlayerDisplayNamePolicy.validate(displayName)
    val draft = validation.profile
    val estimatedCm = draft?.takeIf { automatic && it.heightCm != null }?.let {
        DefaultStepLengthEstimator().estimate(it).meters * 100
    }
    val displayStepLength = estimatedCm?.let { DecimalFormat("0.0").format(it) } ?: stepLengthCm
    val normalizedSaved = savedProfile?.let {
        if (it.useAutomaticStepLength) it.copy(manualStepLengthMeters = null) else it
    }
    val changed = draft != null && (
        draft != normalizedSaved || displayNameValidation.normalized != savedDisplayName
    )
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(StepArenaSpacing.md).testTag("profile_settings"),
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_profile), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.profile_info_action))
            }
        }
        GlassSurface(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.profile_display_name)) },
                supportingText = {
                    Text(stringResource(R.string.profile_display_name_support, PlayerDisplayNamePolicy.MAX_CODE_POINTS))
                },
                isError = !displayNameValidation.isValid,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile_display_name"),
            )
            ProfileField(stringResource(R.string.profile_height), height) { height = it }
            ProfileField(stringResource(R.string.profile_weight), weight) { weight = it }
            ProfileField(
                stringResource(R.string.profile_step_length),
                displayStepLength,
                enabled = !automatic,
                contentDescription = estimatedCm?.let {
                    stringResource(R.string.profile_estimated_step_accessibility, DecimalFormat("0.0").format(it))
                },
            ) { stepLengthCm = it }
            androidx.compose.foundation.layout.Row {
                Checkbox(automatic, { automatic = it })
                Text(stringResource(R.string.profile_auto_step))
            }
            if (automatic && estimatedCm != null) {
                Text(stringResource(R.string.profile_estimated_from_height))
            }
            Button(onClick = {
                val result = validator.validate(
                    height,
                    weight,
                    if (automatic) "" else stepLengthCm,
                    automatic,
                )
                if (!displayNameValidation.isValid) messageRes = R.string.profile_display_name_invalid
                else if (!result.isValid) messageRes = R.string.profile_invalid
                else scope.launch {
                    runCatching {
                        identityRepository.saveDisplayName(displayName)
                        repository.save(requireNotNull(result.profile))
                    }
                        .onSuccess {
                            savedProfile = result.profile
                            savedDisplayName = displayNameValidation.normalized
                            displayName = displayNameValidation.normalized.orEmpty()
                            messageRes = null
                            snackbar.showSnackbar(savedMessage)
                        }
                        .onFailure { messageRes = R.string.profile_invalid }
                }
            }, enabled = changed && displayNameValidation.isValid) {
                Text(stringResource(R.string.profile_save))
            }
            messageRes?.let { Text(stringResource(it)) }
        }
        SnackbarHost(snackbar)
    }
    if (showInfo) ModalBottomSheet(onDismissRequest = { showInfo = false }, modifier = Modifier.testTag("profile_info_sheet")) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.profile_info_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.profile_estimate_note))
            Text(stringResource(R.string.profile_history_policy))
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' || c == ',' }) onValue(it) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().then(
            if (contentDescription == null) Modifier else Modifier.semantics {
                this.contentDescription = contentDescription
            },
        ),
    )
}

private fun String.toNumber(): Double? =
    trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()
