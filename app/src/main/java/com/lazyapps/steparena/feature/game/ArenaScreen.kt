package com.lazyapps.steparena.feature.game

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.game.GameNotificationType

enum class ArenaPage(
    val routeSegment: String,
    @param:StringRes val labelRes: Int,
) {
    CHALLENGE("challenge", R.string.game_tab_match),
    RANK("rank", R.string.game_tab_rank),
    WEEKLY_GROUP("weekly-group", R.string.game_tab_league),
    MONTHLY_RECORD("monthly-record", R.string.game_tab_season);

    companion object {
        fun fromRouteSegment(value: String?): ArenaPage =
            entries.firstOrNull { it.routeSegment == value } ?: CHALLENGE
    }
}

object ArenaTestTags {
    const val SCREEN = "arena_screen"
    const val PAGER = "arena_pager"
    const val PROMOTION = "promotion_dialog"
    fun tab(page: ArenaPage) = "arena_tab_${page.routeSegment}"
    fun page(page: ArenaPage) = "arena_page_${page.routeSegment}"
}

@Composable
fun ArenaScreen(
    initialPage: ArenaPage = ArenaPage.CHALLENGE,
    motionLevel: MotionLevel = MotionLevel.FULL,
    vm: GameViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ArenaContent(
        state = state,
        initialPage = initialPage,
        motionLevel = motionLevel,
        onAcknowledgeEvent = vm::acknowledgeEvent,
        onChallengeObserved = vm::observeChallengeMilestone,
        onCelebrationConsumed = vm::acknowledgeChallengeCelebration,
    )
}

@Composable
internal fun ArenaContent(
    state: GameUiState,
    initialPage: ArenaPage = ArenaPage.CHALLENGE,
    motionLevel: MotionLevel = MotionLevel.FULL,
    onAcknowledgeEvent: (String) -> Unit = {},
    onChallengeObserved: (String, Long, Long) -> Unit = { _, _, _ -> },
    onCelebrationConsumed: (String) -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = initialPage.ordinal) {
        ArenaPage.entries.size
    }
    val scope = rememberCoroutineScope()
    state.notificationEvents.firstOrNull {
        it.type == GameNotificationType.PROMOTION && !it.acknowledged
    }?.let { event ->
        AlertDialog(
            modifier = Modifier.testTag(ArenaTestTags.PROMOTION),
            onDismissRequest = { onAcknowledgeEvent(event.id) },
            title = { Text(stringResource(R.string.game_promotion_title)) },
            text = {
                Text(stringResource(R.string.game_rating_value, formatNumber(state.profile?.rating ?: 0)))
            },
            confirmButton = {
                TextButton(onClick = { onAcknowledgeEvent(event.id) }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
    Column(Modifier.fillMaxSize().testTag(ArenaTestTags.SCREEN)) {
        PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
            ArenaPage.entries.forEach { item ->
                Tab(
                    modifier = Modifier.testTag(ArenaTestTags.tab(item)),
                    selected = pagerState.currentPage == item.ordinal,
                    onClick = { scope.launch { pagerState.animateScrollToPage(item.ordinal) } },
                    text = { Text(stringResource(item.labelRes)) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().testTag(ArenaTestTags.PAGER),
            key = { ArenaPage.entries[it].routeSegment },
        ) { pageIndex ->
            when (ArenaPage.entries[pageIndex]) {
                ArenaPage.CHALLENGE -> ChallengeScreen(
                    state = state.challengeUiState(),
                    motionLevel = motionLevel,
                    onChallengeObserved = onChallengeObserved,
                    onCelebrationConsumed = onCelebrationConsumed,
                )
                ArenaPage.RANK -> RankScreen(state.rankUiState())
                ArenaPage.WEEKLY_GROUP -> WeeklyGroupScreen(state.weeklyGroupUiState())
                ArenaPage.MONTHLY_RECORD -> MonthlyRecordScreen(state.monthlyRecordUiState())
            }
        }
    }
}
