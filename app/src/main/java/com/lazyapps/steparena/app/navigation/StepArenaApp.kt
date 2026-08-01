package com.lazyapps.steparena.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.designsystem.component.StepArenaBackground
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.core.designsystem.theme.StepArenaMotion
import com.lazyapps.steparena.feature.home.HomeAction
import com.lazyapps.steparena.feature.home.HomeScreen
import com.lazyapps.steparena.feature.home.HomeUiState
import com.lazyapps.steparena.feature.placeholder.PlaceholderScreen
import com.lazyapps.steparena.feature.diagnostics.TrackingDiagnosticsScreen
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.feature.records.RecordsScreen
import com.lazyapps.steparena.feature.settings.ProfileSettingsScreen
import com.lazyapps.steparena.feature.settings.SettingsScreen
import com.lazyapps.steparena.feature.settings.RecoverySettingsScreen
import com.lazyapps.steparena.feature.settings.DataManagementScreen
import com.lazyapps.steparena.feature.settings.InfoDocument
import com.lazyapps.steparena.feature.settings.InfoDocumentScreen
import com.lazyapps.steparena.feature.diagnostics.RecoveryHistoryScreen
import com.lazyapps.steparena.feature.game.AchievementScreen
import com.lazyapps.steparena.feature.game.ArenaPage
import com.lazyapps.steparena.feature.game.ArenaScreen

enum class AppDestination(val route: String, val labelRes: Int) {
    HOME("home", R.string.nav_home),
    CHALLENGE("challenge", R.string.nav_match),
    RECORDS("records", R.string.nav_records),
    ACHIEVEMENTS("achievements", R.string.nav_achievements),
    SETTINGS("settings", R.string.nav_settings),
}

@Composable
fun StepArenaApp(
    homeUiState: HomeUiState,
    onHomeAction: (HomeAction) -> Unit,
    trackingState: StepTrackingState = StepTrackingState(),
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    initialRoute: String = AppDestination.HOME.route,
    environmentBanner: String? = null,
    onReplayOnboarding: () -> Unit = {},
    onAllDataDeleted: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoute = backStackEntry?.destination?.route ?: AppDestination.HOME.route
    val selectedDestination = topLevelDestinationForRoute(selectedRoute)
    val requestedInitialRoute = canonicalGameRoute(initialRoute)
    val transitions = transitionsFor(homeUiState.motionLevel)
    LaunchedEffect(requestedInitialRoute) {
        if (requestedInitialRoute != AppDestination.HOME.route && selectedRoute != requestedInitialRoute) {
            navController.navigate(requestedInitialRoute) { launchSingleTop = true }
        }
    }

    StepArenaBackground(modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = StepArenaColors.White,
            topBar = {
                if (environmentBanner != null) {
                    Text(
                        environmentBanner,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedDestination == destination,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(AppDestination.HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                androidx.compose.material3.Icon(
                                    imageVector = destinationIcon(destination),
                                    contentDescription = null,
                                    modifier = Modifier.clearAndSetSemantics { },
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.HOME.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { transitions.first },
                exitTransition = { transitions.second },
                popEnterTransition = { transitions.first },
                popExitTransition = { transitions.second },
            ) {
                composable(AppDestination.HOME.route) {
                    HomeScreen(
                        uiState = homeUiState,
                        onAction = { action ->
                            if (action == HomeAction.OpenDiagnostics) {
                                navController.navigate("settings/diagnostics") {
                                    launchSingleTop = true
                                }
                            } else onHomeAction(action)
                        },
                    )
                }
                composable(AppDestination.CHALLENGE.route) {
                    ArenaScreen(ArenaPage.CHALLENGE, homeUiState.motionLevel)
                }
                composable(
                    route = "${AppDestination.CHALLENGE.route}/{page}",
                    arguments = listOf(navArgument("page") { type = NavType.StringType }),
                ) { entry ->
                    ArenaScreen(
                        initialPage = ArenaPage.fromRouteSegment(entry.arguments?.getString("page")),
                        motionLevel = homeUiState.motionLevel,
                    )
                }
                composable(AppDestination.ACHIEVEMENTS.route) {
                    AchievementScreen()
                }
                composable(AppDestination.RECORDS.route) { RecordsScreen() }
                composable(AppDestination.SETTINGS.route) {
                    SettingsScreen(
                        onProfile = { navController.navigate("settings/profile") },
                        onDiagnostics = { navController.navigate("settings/diagnostics") },
                        onRecoverySettings = { navController.navigate("settings/recovery") },
                        onRecoveryHistory = { navController.navigate("settings/recovery-history") },
                        onDataManagement = { navController.navigate("settings/data") },
                        onPrivacy = { navController.navigate("settings/privacy") },
                        onTerms = { navController.navigate("settings/terms") },
                        onLicenses = { navController.navigate("settings/licenses") },
                        onAbout = { navController.navigate("settings/about") },
                        onReplayOnboarding = onReplayOnboarding,
                    )
                }
                composable("settings/profile") { ProfileSettingsScreen() }
                composable("settings/diagnostics") { TrackingDiagnosticsScreen(trackingState) }
                composable("settings/recovery") { RecoverySettingsScreen() }
                composable("settings/recovery-history") { RecoveryHistoryScreen() }
                composable("settings/data") { DataManagementScreen(onAllDataDeleted) }
                composable("settings/privacy") { InfoDocumentScreen(InfoDocument.PRIVACY) }
                composable("settings/terms") { InfoDocumentScreen(InfoDocument.TERMS) }
                composable("settings/licenses") { InfoDocumentScreen(InfoDocument.LICENSES) }
                composable("settings/about") { InfoDocumentScreen(InfoDocument.ABOUT) }
            }
        }
    }
}

private fun NavGraphBuilder.placeholderDestinations() {
    AppDestination.entries.drop(1).filter {
        it != AppDestination.SETTINGS && it != AppDestination.RECORDS
    }.forEach { destination ->
        composable(destination.route) {
            PlaceholderScreen(title = stringResource(destination.labelRes))
        }
    }
}

private fun transitionsFor(level: MotionLevel): Pair<EnterTransition, ExitTransition> =
    when (level) {
        MotionLevel.FULL -> Pair(
            fadeIn(tween(StepArenaMotion.standard)) +
                slideInHorizontally(tween(StepArenaMotion.standard)) { it / 10 },
            fadeOut(tween(StepArenaMotion.quick)) +
                slideOutHorizontally(tween(StepArenaMotion.quick)) { -it / 12 },
        )
        MotionLevel.REDUCED -> Pair(
            fadeIn(tween(StepArenaMotion.quick)),
            fadeOut(tween(StepArenaMotion.quick)),
        )
        MotionLevel.OFF -> Pair(EnterTransition.None, ExitTransition.None)
    }

private fun destinationIcon(destination: AppDestination): ImageVector = when (destination) {
    AppDestination.HOME -> Icons.Default.Home
    AppDestination.CHALLENGE -> Icons.AutoMirrored.Filled.DirectionsWalk
    AppDestination.RECORDS -> Icons.Default.BarChart
    AppDestination.ACHIEVEMENTS -> Icons.Default.EmojiEvents
    AppDestination.SETTINGS -> Icons.Default.Settings
}

internal fun topLevelDestinationForRoute(route: String?): AppDestination = when {
    route == AppDestination.HOME.route -> AppDestination.HOME
    route?.startsWith(AppDestination.CHALLENGE.route) == true -> AppDestination.CHALLENGE
    route == AppDestination.RECORDS.route -> AppDestination.RECORDS
    route == AppDestination.ACHIEVEMENTS.route -> AppDestination.ACHIEVEMENTS
    route?.startsWith(AppDestination.SETTINGS.route) == true -> AppDestination.SETTINGS
    else -> AppDestination.HOME
}

fun canonicalGameRoute(route: String?): String = when (route) {
    "match", AppDestination.CHALLENGE.route -> AppDestination.CHALLENGE.route
    "rank", "${AppDestination.CHALLENGE.route}/rank" -> "${AppDestination.CHALLENGE.route}/rank"
    "league", "${AppDestination.CHALLENGE.route}/weekly-group" -> "${AppDestination.CHALLENGE.route}/weekly-group"
    "season", "${AppDestination.CHALLENGE.route}/monthly-record" -> "${AppDestination.CHALLENGE.route}/monthly-record"
    AppDestination.ACHIEVEMENTS.route -> AppDestination.ACHIEVEMENTS.route
    AppDestination.RECORDS.route -> AppDestination.RECORDS.route
    AppDestination.SETTINGS.route -> AppDestination.SETTINGS.route
    else -> AppDestination.HOME.route
}
