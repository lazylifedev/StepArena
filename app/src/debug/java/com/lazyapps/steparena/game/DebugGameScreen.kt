package com.lazyapps.steparena.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object DebugGameTestTags {
    const val SCREEN = "debug_game_screen"
    const val WARNING = "debug_game_warning"
    const val MODE_BANNER = "debug_game_mode_banner"
    const val START_ISOLATED = "debug_game_start_isolated"
    const val RETURN_NORMAL = "debug_game_return_normal"
    fun action(action: DebugGameScenario) = "debug_game_${action.name.lowercase()}"
}

@Composable
fun DebugGameScreen(
    onClose: () -> Unit,
    onRun: (DebugGameScenario) -> Unit,
    isolated: Boolean = true,
    onStartIsolated: () -> Unit = {},
    onReturnNormal: () -> Unit = {},
) {
    var pending by remember { mutableStateOf<DebugGameScenario?>(null) }
    var pendingModeChange by remember { mutableStateOf<Boolean?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().testTag(DebugGameTestTags.SCREEN),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("開発用ゲームシナリオ", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (isolated) "隔離テストデータ" else "通常データ（シナリオ操作は禁止）",
                color = if (isolated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(DebugGameTestTags.MODE_BANNER),
            )
            Text(
                if (isolated) {
                    "操作対象はDebug専用DBだけです。"
                } else {
                    "シナリオを実行するには隔離モードを開始してください。"
                },
                modifier = Modifier.testTag(DebugGameTestTags.WARNING),
            )
            if (isolated) {
                OutlinedButton(
                    onClick = { pendingModeChange = false },
                    modifier = Modifier.testTag(DebugGameTestTags.RETURN_NORMAL),
                ) { Text("通常データへ戻る") }
            } else {
                Button(
                    onClick = { pendingModeChange = true },
                    modifier = Modifier.testTag(DebugGameTestTags.START_ISOLATED),
                ) { Text("隔離シナリオを開始") }
            }
            Button(onClick = onClose) { Text("閉じる") }
        }
        if (isolated) debugCategories.forEach { (title, actions) ->
            item { Text(title, style = MaterialTheme.typography.titleLarge) }
            items(actions) { (action, label) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        OutlinedButton(
                            onClick = { pending = action },
                            modifier = Modifier.fillMaxWidth().testTag(DebugGameTestTags.action(action)),
                        ) { Text(label) }
                    }
                }
            }
        }
    }
    pendingModeChange?.let { enterIsolated ->
        AlertDialog(
            onDismissRequest = { pendingModeChange = null },
            title = { Text(if (enterIsolated) "隔離シナリオを開始" else "通常データへ戻る") },
            text = {
                Text(
                    if (enterIsolated) {
                        "通常データには触れず、Debug専用DBと設定へ切り替えます。"
                    } else {
                        "Debugデータを残したまま通常DBへ戻ります。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingModeChange = null
                    if (enterIsolated) onStartIsolated() else onReturnNormal()
                }) { Text("切り替える") }
            },
            dismissButton = {
                TextButton(onClick = { pendingModeChange = null }) { Text("キャンセル") }
            },
        )
    }
    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Debug操作の確認") },
            text = { Text("「${labelOf(action)}」を実行して端末内データを書き換えます。") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    onRun(action)
                }) { Text("実行") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("キャンセル") } },
        )
    }
}

private val debugCategories = listOf(
    "歩数" to listOf(
        DebugGameScenario.SET_MEASURED_STEPS to "今日の自前実測歩数を設定",
        DebugGameScenario.COUNTER_100 to "Counterへ+100",
        DebugGameScenario.COUNTER_1000 to "Counterへ+1,000",
        DebugGameScenario.COUNTER_5000 to "Counterへ+5,000",
        DebugGameScenario.ADD_RECOVERED to "RECOVERED歩数を追加",
        DebugGameScenario.ADD_HEALTH_CONNECT to "Health Connect補完歩数を追加",
        DebugGameScenario.ADD_ESTIMATED to "ESTIMATED歩数を追加",
        DebugGameScenario.ADD_UNKNOWN to "UNKNOWN歩数を追加",
        DebugGameScenario.ABNORMAL_STEPS to "異常歩数を追加",
        DebugGameScenario.OVER_30000 to "日次30,000歩超過",
    ),
    "試合" to listOf(
        DebugGameScenario.CREATE_MATCH to "今日の試合を生成",
        DebugGameScenario.SET_NPC_LOW to "NPC目標を低く設定",
        DebugGameScenario.SET_NPC_4000 to "NPC目標を4,000歩に設定",
        DebugGameScenario.SET_NPC_HIGH to "NPC目標を高く設定",
        DebugGameScenario.WIN to "勝利状態を作る",
        DebugGameScenario.LOSS to "敗北状態を作る",
        DebugGameScenario.DRAW to "DRAW状態を作る",
        DebugGameScenario.NO_CONTEST to "NO_CONTEST状態を作る",
        DebugGameScenario.FINALIZE to "試合を確定",
        DebugGameScenario.DOUBLE_FINALIZE to "同じ試合を再確定",
        DebugGameScenario.RESET_TODAY_MATCH to "今日の試合をリセット",
    ),
    "rating・ランク" to listOf(
        DebugGameScenario.ADD_RATING to "rating +25",
        DebugGameScenario.REMOVE_RATING to "rating -20",
        DebugGameScenario.PROMOTION_READY to "昇格直前",
        DebugGameScenario.DEMOTION_READY to "降格直前",
        DebugGameScenario.PROMOTE to "BronzeからSilver昇格",
        DebugGameScenario.THREE_WIN_STREAK to "連勝3",
        DebugGameScenario.FIVE_WIN_STREAK to "連勝5",
        DebugGameScenario.THREE_LOSS_STREAK to "連敗3",
        DebugGameScenario.END_BEGINNER_PERIOD to "初心者期間終了",
    ),
    "リーグ・シーズン" to listOf(
        DebugGameScenario.LEAGUE_CREATE to "週間リーグ生成",
        DebugGameScenario.LEAGUE_FIRST to "週間1位",
        DebugGameScenario.LEAGUE_FIFTH to "週間5位",
        DebugGameScenario.LEAGUE_TENTH to "週間10位",
        DebugGameScenario.LEAGUE_FINALIZE to "週間確定",
        DebugGameScenario.MONTH_END to "月末相当",
        DebugGameScenario.SEASON_FINALIZE to "シーズン確定",
        DebugGameScenario.NEXT_SEASON to "次シーズン生成",
    ),
    "実績" to listOf(
        DebugGameScenario.FIRST_WIN_ACHIEVEMENT to "初勝利解除",
        DebugGameScenario.FIRST_STEP_ACHIEVEMENT to "1,000歩解除",
        DebugGameScenario.THREE_WIN_STREAK to "3連勝解除",
        DebugGameScenario.PROMOTE to "Silver昇格解除",
        DebugGameScenario.EVALUATE_ACHIEVEMENTS to "実績再評価",
        DebugGameScenario.DUPLICATE_ACHIEVEMENT to "同一実績の再解除試行",
        DebugGameScenario.RESET_ACHIEVEMENTS to "全実績初期化",
    ),
    "時間" to listOf(
        DebugGameScenario.NEXT_DAY to "翌日へ進める",
        DebugGameScenario.NEXT_WEEK to "翌週へ進める",
        DebugGameScenario.NEXT_MONTH to "翌月へ進める",
        DebugGameScenario.CHANGE_TIME_ZONE to "ZoneId変更",
        DebugGameScenario.CLOCK_ROLLBACK to "時刻巻き戻し相当",
        DebugGameScenario.SAME_DAY_REPROCESS to "同日再処理",
    ),
    "保守" to listOf(
        DebugGameScenario.RERUN_WORK_MANAGER to "Game Maintenanceを実行",
        DebugGameScenario.RESET_DEBUG_DATA to "Debugゲーム・当日活動データを初期化",
    ),
)

private fun labelOf(action: DebugGameScenario) =
    debugCategories.asSequence().flatMap { it.second.asSequence() }
        .firstOrNull { it.first == action }?.second ?: action.name
