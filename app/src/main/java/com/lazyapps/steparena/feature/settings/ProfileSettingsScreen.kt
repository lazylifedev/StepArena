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
import com.lazyapps.steparena.activity.UserBodyProfile
import com.lazyapps.steparena.activity.DefaultUserBodyProfileValidator
import androidx.compose.ui.res.stringResource
import com.lazyapps.steparena.R
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import kotlinx.coroutines.launch

@Composable
fun ProfileSettingsScreen(modifier: Modifier = Modifier) {
    val repository = (LocalContext.current.applicationContext as StepArenaApplication).profileRepository
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var stepLengthCm by remember { mutableStateOf("") }
    var automatic by remember { mutableStateOf(true) }
    var messageRes by remember { mutableStateOf<Int?>(null) }
    val validator = remember { DefaultUserBodyProfileValidator() }
    LaunchedEffect(Unit) {
        repository.current().let {
            height = it.heightCm?.toString().orEmpty()
            weight = it.weightKg?.toString().orEmpty()
            stepLengthCm = it.manualStepLengthMeters?.times(100)?.toString().orEmpty()
            automatic = it.useAutomaticStepLength
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(StepArenaSpacing.md).testTag("profile_settings"),
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
    ) {
        Text(stringResource(R.string.settings_profile), style = MaterialTheme.typography.headlineMedium)
        GlassSurface(Modifier.fillMaxWidth()) {
            ProfileField(stringResource(R.string.profile_height), height) { height = it }
            ProfileField(stringResource(R.string.profile_weight), weight) { weight = it }
            ProfileField(stringResource(R.string.profile_step_length), stepLengthCm) { stepLengthCm = it }
            androidx.compose.foundation.layout.Row {
                Checkbox(automatic, { automatic = it })
                Text(stringResource(R.string.profile_auto_step))
            }
            Text(stringResource(R.string.profile_units))
            Button(onClick = {
                val result = validator.validate(height, weight, stepLengthCm, automatic)
                if (!result.isValid) messageRes = R.string.profile_invalid
                else scope.launch {
                    runCatching { repository.save(requireNotNull(result.profile)) }
                        .onSuccess { messageRes = R.string.profile_saved }
                        .onFailure { messageRes = R.string.profile_invalid }
                }
            }) { Text(stringResource(R.string.profile_save)) }
            messageRes?.let { Text(stringResource(it)) }
        }
        Text(stringResource(R.string.profile_history_policy))
    }
}

@Composable
private fun ProfileField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' || c == ',' }) onValue(it) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun String.toNumber(): Double? =
    trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()
