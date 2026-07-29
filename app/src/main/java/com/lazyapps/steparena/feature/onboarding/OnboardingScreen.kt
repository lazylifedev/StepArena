package com.lazyapps.steparena.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

object OnboardingTestTags {
    const val SCREEN = "onboarding_screen"
    const val NEXT = "onboarding_next"
    const val BACK = "onboarding_back"
}

data class OnboardingPage(val title: String, val body: String, val action: String = "次へ")

val onboardingPages = listOf(
    OnboardingPage("歩くことを、毎日の楽しいチャレンジに。", "StepArenaは端末の歩数センサーで日々の活動を記録する歩数計アプリです。チャレンジパートナーは端末内で自動生成され、実在ユーザーとのオンライン対戦ではありません。アカウントは不要で、通常データは端末内に保存します。無理な歩行を促すものではありません。"),
    OnboardingPage("計測方法", "Step Counterを利用します。精度やバックグラウンド動作は端末により異なり、再起動や省電力設定で停止する場合があります。Health Connectは任意・既定OFFの補完機能です。医療用測定ではありません。"),
    OnboardingPage("必要なときに権限を確認", "身体活動は計測開始時、通知は計測通知が必要なとき、Health Connectの歩数読取は設定でONにしたときだけ説明して要求します。拒否しても記録の閲覧、設定、ヘルプは利用できます。"),
    OnboardingPage("チャレンジのルール", "今日のチャレンジ対象歩数を、自動生成されたパートナーの目標と比べます。対象歩数の上限は1日30,000歩です。補完・推定歩数には制限がありますが、通常の歩数記録は上限後も残ります。"),
    OnboardingPage("準備ができました", "まずは歩数計測を開始してください。Health Connectは後から設定できます。"),
)

@Composable
fun OnboardingScreen(
    step: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onStartTracking: () -> Unit = onNext,
    onLater: () -> Unit = onNext,
) {
    val page = onboardingPages[step.coerceIn(onboardingPages.indices)]
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(OnboardingTestTags.SCREEN),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("${step + 1} / ${onboardingPages.size}", color = MaterialTheme.colorScheme.secondary)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(page.title, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() })
            Text(page.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 24.dp))
        }
        if (step == onboardingPages.lastIndex) {
            Button(onClick = onStartTracking, modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.NEXT)) {
                Text("歩数計測を開始")
            }
            OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text("あとで開始")
            }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.NEXT)) { Text(page.action) }
        }
        if (step > 0) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.BACK),
            ) { Text("戻る") }
        }
    }
}
