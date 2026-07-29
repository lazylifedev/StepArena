package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.steparena.core.database.entity.DailyMatchEntity
import com.lazyapps.steparena.game.*
import java.time.*
import java.time.temporal.ChronoUnit

enum class GamePage(val label: String) {
    MATCH("対戦"), RANK("ランク"), LEAGUE("リーグ"), SEASON("シーズン"), ACHIEVEMENTS("実績")
}

@Composable
fun GameScreen(initialPage: GamePage = GamePage.MATCH, vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(initialPage) }
    Column(Modifier.fillMaxSize()) {
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

@Composable private fun MatchPage(state: GameUiState) = LazyColumn(
    modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item {
        Text("デイリー対戦", style = MaterialTheme.typography.headlineMedium)
        Text("現在の対戦相手は、あなたの活動量に合わせて生成されるローカル対戦データです。")
    }
    state.todayMatch?.let { match ->
        item {
            GameCard("今日の対戦") {
                Text("あなた  ${match.eligibleUserSteps} 対 ${match.opponentName}  ${opponentProgress(match)}")
                LinearProgressIndicator(
                    progress = {
                        (match.eligibleUserSteps.toFloat() / match.opponentTargetSteps.coerceAtLeast(1)).coerceIn(0f, 1f)
                    }, modifier = Modifier.fillMaxWidth(),
                )
                Text("相手の最終目標 ${match.opponentTargetSteps}歩")
                Text("対戦有効歩数 ${match.eligibleUserSteps}歩 / 総歩数 ${match.totalUserSteps}歩")
                Text("品質: ${match.competitiveQuality}")
                if (match.restrictedUserSteps + match.excludedUserSteps > 0) {
                    Text("一部の補完歩数は公平性のため制限されています。")
                }
                Text("残り ${remainingToday()}")
            }
        }
    }
    item { Text("過去の対戦", style = MaterialTheme.typography.titleLarge) }
    if (state.recentMatches.isEmpty()) item { Text("確定済みの対戦はまだありません。") }
    items(state.recentMatches.filter { it.status == MatchStatus.FINALIZED }) {
        GameCard(it.localDate) {
            Text("${it.opponentName}  ${it.outcome ?: MatchOutcome.IN_PROGRESS}")
            Text("有効 ${it.eligibleUserSteps}歩 / 相手 ${it.opponentTargetSteps}歩")
            Text("rating ${signed(it.ratingDelta)}")
            if (it.restrictionReasons.isNotBlank()) Text("制限理由: ${it.restrictionReasons}")
        }
    }
}

@Composable private fun RankPage(state: GameUiState) = LazyColumn(
    modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    val profile = state.profile
    item { Text("ランク", style = MaterialTheme.typography.headlineMedium) }
    if (profile == null) item { CircularProgressIndicator() } else {
        val rank = RankSystem.definition(profile.rating)
        val next = RankSystem.definitions.getOrNull(RankSystem.definitions.indexOf(rank) + 1)
        item {
            GameCard(rank.displayName) {
                Text("${profile.rating} RP", style = MaterialTheme.typography.headlineSmall)
                Text(next?.let { "次のランクまで ${(it.minimumRating - profile.rating).coerceAtLeast(0)} RP" } ?: "最高ランク")
                Text("勝 ${profile.wins} / 敗 ${profile.losses} / 分 ${profile.draws}")
                val decided = profile.wins + profile.losses
                Text("勝率 ${if (decided == 0) 0 else profile.wins * 100 / decided}%")
                Text("連勝 ${profile.currentWinStreak} / 最高 ${profile.bestWinStreak}")
            }
        }
        item { Text("最近のrating推移", style = MaterialTheme.typography.titleLarge) }
        items(state.recentMatches.filter { it.ratingAfter != null }.take(10)) {
            Text("${it.localDate}: ${it.ratingAfter} (${signed(it.ratingDelta)})")
        }
    }
}

@Composable private fun LeaguePage(state: GameUiState) = LazyColumn(
    modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
) {
    item { Text("週間リーグ", style = MaterialTheme.typography.headlineMedium) }
    item { Text("月曜00:00から日曜までのローカルNPCリーグです。") }
    val league = state.league
    if (league == null) item { Text("リーグを準備しています。") } else {
        item { Text("現在 ${league.userRank ?: "-"}位 / 10人・勝点 ${league.userPoints}") }
        val rows = participantRows(league.participantsJson)
            .sortedByDescending { it.second }
        items(rows) { (name, points) ->
            ListItem(
                headlineContent = { Text(name, fontWeight = if (name == "You") FontWeight.Bold else null) },
                trailingContent = { Text("$points pt") },
            )
        }
    }
}

@Composable private fun SeasonPage(state: GameUiState) = LazyColumn(
    modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { Text("月間シーズン", style = MaterialTheme.typography.headlineMedium) }
    state.season?.let { season ->
        item {
            GameCard(season.id) {
                Text("残り ${ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.now().plusMonths(1).withDayOfMonth(1))}日")
                Text("開始 ${RankSystem.definition(season.startRating).displayName}")
                Text("現在 ${state.profile?.let { RankSystem.definition(it.rating).displayName } ?: "-"}")
                Text("最高 ${season.highestRankTier.name} ${season.highestRankDivision ?: ""}")
                Text("勝 ${state.profile?.wins ?: 0} / 敗 ${state.profile?.losses ?: 0}")
                Text("対戦有効歩数 ${season.totalEligibleSteps}")
                Text("最高連勝 ${state.profile?.bestWinStreak ?: 0}")
            }
        }
    } ?: item { Text("シーズンを準備しています。") }
}

@Composable private fun AchievementPage(state: GameUiState) = LazyColumn(
    modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    item { Text("実績", style = MaterialTheme.typography.headlineMedium) }
    val definitions = listOf(
        "first_step" to ("はじめの一歩" to "1日1,000歩を記録"),
        "first_win" to ("初勝利" to "デイリー対戦で初勝利"),
        "three_wins" to ("連勝開始" to "3連勝を達成"),
        "five_wins" to ("止まらない歩み" to "5連勝を達成"),
        "silver_promotion" to ("Bronze突破" to "Silverへ昇格"),
    )
    items(definitions) { (id, text) ->
        val unlocked = state.achievements.any { it.achievementId == id }
        ListItem(
            headlineContent = { Text(text.first) },
            supportingContent = { Text(text.second) },
            trailingContent = { Text(if (unlocked) "解除済み" else "未解除") },
        )
    }
}

@Composable private fun GameCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private fun opponentProgress(match: DailyMatchEntity): Long {
    val now = ZonedDateTime.now()
    return LocalOpponentGenerator().progress(
        LocalOpponent(
            match.opponentId, match.opponentName, match.opponentAvatarKey,
            match.opponentRankTier, match.opponentRankDivision, match.opponentTargetSteps,
            match.opponentPersonality,
        ),
        now.hour * 60 + now.minute,
    )
}
private fun remainingToday(): String {
    val now = ZonedDateTime.now()
    val minutes = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMinutes()
    return "${minutes / 60}時間${minutes % 60}分"
}
private fun signed(value: Int?) = value?.let { if (it >= 0) "+$it" else "$it" } ?: "-"
private fun participantRows(json: String): List<Pair<String, Int>> =
    Regex("""\{"name":"([^"]+)","points":(\d+)}""").findAll(json)
        .map { it.groupValues[1] to it.groupValues[2].toInt() }.toList()
