package com.lazyapps.steparena.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object OnboardingTestTags {
    const val SCREEN = "onboarding_screen"
    const val NEXT = "onboarding_next"
    const val BACK = "onboarding_back"
}

data class OnboardingPage(val title: String, val body: String, val action: String = "次へ")

val onboardingPages = listOf(
    OnboardingPage("StepArenaへようこそ", "毎日の散歩を試合に変える、日本向けの歩数アプリです。"),
    OnboardingPage("端末で歩数を計測", "端末の歩数センサーを使い、アプリを閉じても計測を続けます。省電力設定により停止する場合があります。"),
    OnboardingPage("身体活動へのアクセス", "歩数を取得するため、次の画面で身体活動権限を確認します。", "権限を確認"),
    OnboardingPage("通知について", "計測中の状態、停止操作、更新状況を常駐通知でお知らせします。", "通知を確認"),
    OnboardingPage("バッテリー制限", "現在の省電力状態を診断します。必要な場合だけ設定画面を案内します。"),
    OnboardingPage("計測テスト", "センサー対応を確認してから計測を開始してください。"),
    OnboardingPage("計測準備完了", "今日の歩数計測を開始できます。", "ホームへ"),
)

@Composable
fun OnboardingScreen(step: Int, onNext: () -> Unit, onBack: () -> Unit) {
    val page = onboardingPages[step.coerceIn(onboardingPages.indices)]
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).testTag(OnboardingTestTags.SCREEN),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("${step + 1} / ${onboardingPages.size}", color = MaterialTheme.colorScheme.secondary)
        Text(page.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 24.dp))
        Button(onClick = onNext, modifier = Modifier.testTag(OnboardingTestTags.NEXT)) { Text(page.action) }
        if (step > 0) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.padding(top = 8.dp).testTag(OnboardingTestTags.BACK),
            ) { Text("戻る") }
        }
    }
}
