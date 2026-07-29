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
    var message by remember { mutableStateOf<String?>(null) }
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
        Text("プロフィール・計算設定", style = MaterialTheme.typography.headlineMedium)
        GlassSurface(Modifier.fillMaxWidth()) {
            ProfileField("身長 (cm)", height) { height = it }
            ProfileField("体重 (kg)", weight) { weight = it }
            ProfileField("歩幅 (cm)", stepLengthCm) { stepLengthCm = it }
            androidx.compose.foundation.layout.Row {
                Checkbox(automatic, { automatic = it })
                Text("身長から歩幅を自動推定")
            }
            Text("距離: km　速度: km/h　体重: kg")
            Button(onClick = {
                val profile = UserBodyProfile(
                    heightCm = height.toNumber(),
                    weightKg = weight.toNumber(),
                    manualStepLengthMeters = stepLengthCm.toNumber()?.div(100),
                    useAutomaticStepLength = automatic,
                )
                val valid = (profile.heightCm == null || profile.heightCm in 100.0..250.0) &&
                    (profile.weightKg == null || profile.weightKg in 25.0..300.0) &&
                    (profile.manualStepLengthMeters == null || profile.manualStepLengthMeters in 0.2..2.0)
                if (!valid) message = "入力値を確認してください"
                else scope.launch { repository.save(profile); message = "保存しました" }
            }) { Text("保存") }
            message?.let { Text(it) }
        }
        Text("距離・カロリーは推定値です。医療目的の測定には使用できません。")
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
