package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.steparena.game.*

enum class GamePage(val label: String) {
    MATCH("対戦"), RANK("ランク"), LEAGUE("リーグ"), SEASON("シーズン"), ACHIEVEMENTS("実績")
}

object GameTestTags {
    const val SCREEN = "game_screen"
    const val PROMOTION = "promotion_dialog"
    const val HEALTH_CAP = "competitive_health_cap"
    const val EMPTY = "game_empty"
}

@Composable
fun GameScreen(initialPage: GamePage = GamePage.MATCH, vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(initialPage) }
    val promotion = state.notificationEvents.firstOrNull {
        it.type == GameNotificationType.PROMOTION && !it.acknowledged
    }
    promotion?.let { event ->
        AlertDialog(
            modifier = Modifier.testTag(GameTestTags.PROMOTION),
            onDismissRequest = { vm.acknowledgeEvent(event.id) },
            title = { Text("ランク昇格") },
            text = { Text("${event.message}\n${state.profile?.rating ?: 0} RP") },
            confirmButton = {
                TextButton(onClick = { vm.acknowledgeEvent(event.id) }) { Text("閉じる") }
            },
        )
    }
    Column(Modifier.fillMaxSize().testTag(GameTestTags.SCREEN)) {
        ScrollableTabRow(selectedTabIndex = page.ordinal) {
            GamePage.entries.forEach {
                Tab(selected = page == it, onClick = { page = it }, text = { Text(it.label) })
            }
        }
        when (page) {
            GamePage.MATCH -> MatchPage(state)
            GamePage.RANK -> RankPage(state)
            GamePage.LEAGUE -> LeaguePage(state)
            GamePage.SEASON -> SeasonPage(state)
            GamePage.ACHIEVEMENTS -> AchievementPage(state)
        }
    }
}

@Composable
private fun MatchPage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item {
        Text("デイリー対戦", style = MaterialTheme.typography.headlineMedium)
        Text("現在の対戦相手は、あなたの活動量に合わせて生成されるローカル対戦データです。")
    }
    state.todayMatch?.let { match ->
        item {
            GameCard("今日の対戦") {
                Text("あなた ${match.eligibleUserSteps} 対 ${match.opponentName}")
                LinearProgressIndicator(
                    progress = {
                        (match.eligibleUserSteps.toFloat() / match.opponentTargetSteps.coerceAtLeast(1))
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("相手の目標 ${match.opponentTargetSteps}歩")
                Text("対戦有効 ${match.eligibleUserSteps}歩 / 総歩数 ${match.totalUserSteps}歩")
                Text("品質: ${match.competitiveQuality}")
                if (match.restrictedUserSteps + match.excludedUserSteps > 0) {
                    Text("補完・推定・不明品質の歩数は公平性のため制限されます。")
                }
                if (match.totalUserSteps > 30_000) {
                    Text(
                        "健康への配慮から、対戦に使える歩数は1日30,000歩が上限です。",
                        modifier = Modifier.testTag(GameTestTags.HEALTH_CAP),
                    )
                }
            }
        }
    } ?: item { Text("今日の対戦を準備しています。", modifier = Modifier.testTag(GameTestTags.EMPTY)) }
    item { Text("過去の対戦", style = MaterialTheme.typography.titleLarge) }
    val finalized = state.recentMatches.filter { it.status == MatchStatus.FINALIZED }
    if (finalized.isEmpty()) item { Text("確定済みの対戦はまだありません。") }
    items(finalized) {
        GameCard(it.localDate) {
            Text("${it.opponentName}: ${it.outcome}")
            Text("有効 ${it.eligibleUserSteps}歩 / 相手 ${it.opponentTargetSteps}歩")
            Text("rating ${signed(it.ratingDelta)} (${it.ratingBefore} → ${it.ratingAfter})")
        }
    }
}

@Composable
private fun RankPage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { Text("ランク", style = MaterialTheme.typography.headlineMedium) }
    state.profile?.let { profile ->
        item {
            val rank = RankSystem.definition(profile.rating)
            GameCard(rank.displayName) {
                Text("${profile.rating} RP", style = MaterialTheme.typography.headlineSmall)
                Text("勝 ${profile.wins} / 敗 ${profile.losses} / 分 ${profile.draws}")
                Text("連勝 ${profile.currentWinStreak} / 最高 ${profile.bestWinStreak}")
                if (profile.beginnerMatchesRemaining > 0) {
                    Text("初心者保護: 残り${profile.beginnerMatchesRemaining}試合")
                }
            }
        }
    } ?: item { CircularProgressIndicator() }
}

@Composable
private fun LeaguePage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
) {
    item { Text("週間リーグ", style = MaterialTheme.typography.headlineMedium) }
    state.league?.let { league ->
        item {
            Text("${league.weekStartLocalDate}〜${league.weekEndLocalDate}")
            Text("${league.userRank ?: "-"}位 / 10人・${league.userPoints} pt")
            if (league.status == LeagueStatus.FINALIZED) {
                Text("確定: ${LeagueRanking.resultBand(league.userRank ?: 10)}")
            }
        }
        items(participantRows(league.participantsJson)) { (name, points) ->
            ListItem(
                headlineContent = { Text(name, fontWeight = if (name == "You") FontWeight.Bold else null) },
                trailingContent = { Text("$points pt") },
            )
        }
    } ?: item { Text("リーグを準備しています。", modifier = Modifier.testTag(GameTestTags.EMPTY)) }
}

@Composable
private fun SeasonPage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { Text("月間シーズン", style = MaterialTheme.typography.headlineMedium) }
    state.season?.let { season ->
        item {
            GameCard(season.id) {
                Text("状態: ${season.status}")
                Text("rating ${season.startRating} → ${season.endRating ?: state.profile?.rating ?: "-"}")
                Text("勝 ${season.wins} / 敗 ${season.losses} / 分 ${season.draws}")
                Text("対戦有効歩数 ${season.totalEligibleSteps}")
                Text("最高連勝 ${season.bestWinStreak}")
            }
        }
    } ?: item { Text("シーズンを準備しています。", modifier = Modifier.testTag(GameTestTags.EMPTY)) }
}

@Composable
private fun AchievementPage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    item { Text("実績", style = MaterialTheme.typography.headlineMedium) }
    items(achievementDefinitions) { (id, title) ->
        val unlocked = state.achievements.firstOrNull { it.achievementId == id }
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text("進捗 ${unlocked?.progressValue ?: 0}") },
            trailingContent = { Text(if (unlocked != null) "解除済み" else "未解除") },
        )
    }
}

@Composable
private fun GameCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private val achievementDefinitions = listOf(
    "first_1000_steps" to "初回1,000歩",
    "three_day_streak" to "3日連続計測",
    "seven_day_streak" to "7日連続計測",
    "first_win" to "初勝利",
    "three_wins" to "3連勝",
    "five_wins" to "5連勝",
    "daily_10000_steps" to "1日10,000歩",
    "daily_20000_steps" to "1日20,000歩",
    "silver_promotion" to "Silver昇格",
    "season_10_matches" to "1シーズン10試合",
    "seven_days_no_recovery" to "補完なし7日連続",
    "gap_recovery_success" to "欠測補完成功",
)

private fun signed(value: Int?) = value?.let { if (it >= 0) "+$it" else "$it" } ?: "-"
private fun participantRows(json: String): List<Pair<String, Int>> =
    Regex(""""name":"([^"]+)","points":(\d+)""").findAll(json)
        .map { it.groupValues[1] to it.groupValues[2].toInt() }.toList()
