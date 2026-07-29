package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.component.RankBadge
import com.lazyapps.steparena.core.designsystem.component.RankProgressBar
import com.lazyapps.steparena.core.designsystem.component.StepProgressRing
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import com.lazyapps.steparena.game.*
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class GamePage(val label: String) {
    MATCH("チャレンジ"), RANK("歩行ランク"), LEAGUE("週間グループ"),
    SEASON("月間記録"), ACHIEVEMENTS("達成記録")
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
            title = { Text("歩行ランクが更新されました") },
            text = { Text("歩行レート ${formatNumber(state.profile?.rating ?: 0)}") },
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
        Text("今日のチャレンジ", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Text("毎日の歩数を、端末内で自動生成されたパートナーと比べます。実在するユーザーとのオンライン対戦ではありません。")
    }
    state.todayMatch?.let { match ->
        item {
            GameCard("今日の進捗") {
                StepProgressRing(
                    progress = (match.eligibleUserSteps.toFloat() / match.opponentTargetSteps.coerceAtLeast(1))
                        .coerceIn(0f, 1f),
                    description = "今日のチャレンジ進捗。あなた${match.eligibleUserSteps}歩、パートナー目標${match.opponentTargetSteps}歩",
                    modifier = Modifier.size(152.dp),
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("あなた", style = MaterialTheme.typography.labelLarge)
                        Text("${formatNumber(match.eligibleUserSteps)}歩", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Text("パートナー目標 ${formatNumber(match.opponentTargetSteps)}歩")
                Text(progressText(match.eligibleUserSteps, match.opponentTargetSteps))
                Text("チャレンジ対象歩数 ${formatNumber(match.eligibleUserSteps)}歩")
                if (match.restrictedUserSteps + match.excludedUserSteps > 0) {
                    Text("一部の補完歩数は公平性のため制限されています。")
                }
                if (match.totalUserSteps > 30_000) {
                    Text(
                        "健康への配慮から、チャレンジに使える歩数は1日30,000歩が上限です。",
                        modifier = Modifier.testTag(GameTestTags.HEALTH_CAP),
                    )
                }
            }
        }
    } ?: item { Text("今日のチャレンジを準備しています。", modifier = Modifier.testTag(GameTestTags.EMPTY)) }
    item { Text("過去のチャレンジ", style = MaterialTheme.typography.titleLarge) }
    val finalized = state.recentMatches.filter { it.status == MatchStatus.FINALIZED }
    if (finalized.isEmpty()) item { Text("記録されたチャレンジはまだありません。") }
    items(finalized) {
        GameCard(formatDate(it.localDate)) {
            Text(it.outcome?.displayName() ?: "集計中", style = MaterialTheme.typography.titleMedium)
            Text("あなた ${formatNumber(it.eligibleUserSteps)}歩")
            Text("パートナー目標 ${formatNumber(it.opponentTargetSteps)}歩")
            Text("歩行レート ${formatNumber(it.ratingBefore)} → ${it.ratingAfter?.let(::formatNumber) ?: "―"}")
        }
    }
}

@Composable
private fun RankPage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { Text("歩行ランク", style = MaterialTheme.typography.headlineMedium) }
    state.profile?.let { profile ->
        item {
            val rank = RankSystem.definition(profile.rating)
            GameCard(rank.displayName) {
                RankBadge(rank.displayName)
                Text("歩行レート ${formatNumber(profile.rating)}", style = MaterialTheme.typography.headlineSmall)
                Text("目標達成 ${profile.wins}回 / あと一歩 ${profile.losses}回 / 同じ歩数 ${profile.draws}回")
                Text("連続達成 ${profile.currentWinStreak}回 / 最長 ${profile.bestWinStreak}回")
                val next = nextRankProgress(profile.rating)
                if (next == null) {
                    Text("現在の最高ランクです")
                } else {
                    Text("次の歩行ランク ${next.rank.displayName}")
                    Text("次の歩行ランクまで ${formatNumber(next.remaining)}")
                    RankProgressBar(
                        progress = next.progress,
                        description = "次の歩行ランクまでの進捗 ${(next.progress * 100).toInt()}パーセント",
                    )
                }
                if (profile.beginnerMatchesRemaining > 0) {
                    Text("初心者サポート 残り${profile.beginnerMatchesRemaining}回")
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
    item {
        Text("週間グループ", style = MaterialTheme.typography.headlineMedium)
        Text("自動生成された10人分の歩数スコアと比べて、今週の位置を確認できます。実在するユーザーの記録ではありません。")
    }
    state.league?.let { league ->
        item {
            Text("${formatDate(league.weekStartLocalDate)}〜${formatDate(league.weekEndLocalDate)}")
            Text("あなたの週間順位 ${league.userRank ?: "-"}位 / 10人")
            Text("週間ポイント ${formatNumber(league.userPoints)}")
            if (league.status == LeagueStatus.FINALIZED) {
                Text("週間記録を集計しました")
            }
        }
        items(participantRows(league.participantsJson)) { (name, points) ->
            ListItem(
                headlineContent = { Text(participantDisplayName(name), fontWeight = if (name == "You") FontWeight.Bold else null) },
                trailingContent = { Text("${formatNumber(points)}ポイント") },
            )
        }
    } ?: item { Text("週間グループを準備しています。", modifier = Modifier.testTag(GameTestTags.EMPTY)) }
}

@Composable
private fun SeasonPage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { Text("月間記録", style = MaterialTheme.typography.headlineMedium) }
    state.season?.let { season ->
        item {
            GameCard(formatMonth(season.id)) {
                Text(season.status.displayName())
                Text("歩行レート ${formatNumber(season.startRating)} → ${formatNumber(season.endRating ?: state.profile?.rating ?: 0)}")
                Text("目標達成 ${season.wins}回 / あと一歩 ${season.losses}回 / 同じ歩数 ${season.draws}回")
                Text("合計チャレンジ対象歩数 ${formatNumber(season.totalEligibleSteps)}歩")
                Text("最長連続達成 ${season.bestWinStreak}回")
            }
        }
    } ?: item { Text("月間記録を準備しています。", modifier = Modifier.testTag(GameTestTags.EMPTY)) }
}

@Composable
private fun AchievementPage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    item { Text("達成記録", style = MaterialTheme.typography.headlineMedium) }
    items(achievementDefinitions) { definition ->
        val unlocked = state.achievements.firstOrNull { it.achievementId == definition.id }
        GameCard(definition.title) {
            Text(definition.description)
            Text(
                if (unlocked != null) "達成済み" else definition.progress(unlocked?.progressValue ?: 0),
                fontWeight = FontWeight.Bold,
            )
            unlocked?.let {
                Text("達成日 ${formatEpochDate(it.unlockedAtEpochMillis)}")
            }
        }
    }
}

@Composable
private fun GameCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

private data class AchievementPresentation(
    val id: String,
    val title: String,
    val description: String,
    val progress: (Long) -> String = { "未達成" },
)
private val achievementDefinitions = listOf(
    AchievementPresentation("first_1000_steps", "はじめの1,000歩", "1日に1,000歩を記録する") { "${formatNumber(it)} / 1,000歩" },
    AchievementPresentation("three_day_streak", "3日連続記録", "3日連続で歩数を記録する") { "$it / 3日" },
    AchievementPresentation("seven_day_streak", "7日連続記録", "7日連続で歩数を記録する") { "$it / 7日" },
    AchievementPresentation("first_win", "初めての目標達成", "デイリーチャレンジを1回達成する"),
    AchievementPresentation("three_wins", "3回連続達成", "チャレンジを3回連続で達成する") { "$it / 3回" },
    AchievementPresentation("five_wins", "5回連続達成", "チャレンジを5回連続で達成する") { "$it / 5回" },
    AchievementPresentation("daily_10000_steps", "1日10,000歩", "1日に10,000歩を記録する") { "${formatNumber(it)} / 10,000歩" },
    AchievementPresentation("daily_20000_steps", "1日20,000歩", "1日に20,000歩を記録する") { "${formatNumber(it)} / 20,000歩" },
    AchievementPresentation("silver_promotion", "Silver到達", "歩行ランクSilverに到達する"),
    AchievementPresentation("season_10_matches", "月間10チャレンジ", "1か月に10回チャレンジする") { "$it / 10回" },
    AchievementPresentation("seven_days_no_recovery", "補完なし7日連続", "補完なしで7日連続記録する") { "$it / 7日" },
    AchievementPresentation("gap_recovery_success", "欠測補完成功", "欠測した歩数を補完する"),
)

private fun signed(value: Int?) = value?.let { if (it >= 0) "+$it" else "$it" } ?: "-"
fun MatchOutcome.displayName(): String = when (this) {
    MatchOutcome.WIN -> "目標達成"
    MatchOutcome.LOSS -> "あと一歩"
    MatchOutcome.DRAW -> "同じ歩数"
    MatchOutcome.NO_CONTEST -> "判定対象外"
    MatchOutcome.IN_PROGRESS -> "集計中"
    MatchOutcome.CANCELLED -> "記録なし"
}
fun SeasonStatus.displayName(): String = when (this) {
    SeasonStatus.ACTIVE -> "集計中"
    SeasonStatus.FINALIZED -> "集計済み"
}
private fun progressText(steps: Long, target: Long) =
    if (steps >= target) "目標を達成しました" else "あと${formatNumber(target - steps)}歩"
fun participantDisplayName(name: String) = when (name) {
    "You" -> "あなた"
    else -> mapOf("Aoi" to "あさひ", "Ren" to "こもれび", "Sora" to "そよかぜ",
        "Hina" to "ひなた", "Riku" to "みちくさ", "Yui" to "あおぞら",
        "Kai" to "かわべ", "Mio" to "つきみ")[name]
        ?: partnerNames[Math.floorMod(name.hashCode(), partnerNames.size)]
}
private val partnerNames = listOf("あさひ", "こもれび", "そよかぜ", "ひなた", "みちくさ", "あおぞら", "かわべ", "つきみ", "なぎさ", "わかば")
data class NextRankProgress(val rank: RankDefinition, val remaining: Int, val progress: Float)
fun nextRankProgress(rating: Int): NextRankProgress? {
    val current = RankSystem.definition(rating)
    val next = RankSystem.definitions.getOrNull(RankSystem.definitions.indexOf(current) + 1) ?: return null
    val span = next.minimumRating - current.minimumRating
    return NextRankProgress(
        rank = next,
        remaining = (next.minimumRating - rating).coerceAtLeast(0),
        progress = ((rating - current.minimumRating).toFloat() / span.coerceAtLeast(1)).coerceIn(0f, 1f),
    )
}
private fun formatNumber(value: Number): String =
    java.text.NumberFormat.getNumberInstance(Locale.JAPAN).format(value)
private fun formatDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPAN))
}.getOrDefault(value)
private fun formatMonth(value: String): String = runCatching {
    YearMonth.parse(value.take(7)).format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN))
}.getOrDefault("月間記録")
private fun formatEpochDate(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.getDefault()))
private fun participantRows(json: String): List<Pair<String, Int>> =
    Regex(""""name":"([^"]+)","points":(\d+)""").findAll(json)
        .map { it.groupValues[1] to it.groupValues[2].toInt() }.toList()
