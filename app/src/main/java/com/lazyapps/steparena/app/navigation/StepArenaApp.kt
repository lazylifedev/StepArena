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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
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
import com.lazyapps.steparena.feature.game.GamePage
import com.lazyapps.steparena.feature.game.GameScreen

enum class AppDestination(val route: String, val labelRes: Int) {
    HOME("home", R.string.nav_home),
    MATCH("match", R.string.nav_match),
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
    val transitions = transitionsFor(homeUiState.motionLevel)
    LaunchedEffect(initialRoute) {
        if (initialRoute != AppDestination.HOME.route && selectedRoute != initialRoute) {
            navController.navigate(initialRoute) { launchSingleTop = true }
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
                            selected = selectedRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(AppDestination.HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destinationSymbol(destination)) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = initialRoute,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { transitions.first },
                exitTransition = { transitions.second },
                popEnterTransition = { transitions.first },
                popExitTransition = { transitions.second },
            ) {
                composable(AppDestination.HOME.route) {
                    HomeScreen(uiState = homeUiState, onAction = onHomeAction)
                }
                composable(AppDestination.MATCH.route) { GameScreen(GamePage.MATCH) }
                composable(AppDestination.ACHIEVEMENTS.route) { GameScreen(GamePage.ACHIEVEMENTS) }
                composable("rank") { GameScreen(GamePage.RANK) }
                composable("league") { GameScreen(GamePage.LEAGUE) }
                composable("season") { GameScreen(GamePage.SEASON) }
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

private fun destinationSymbol(destination: AppDestination): String = when (destination) {
    AppDestination.HOME -> "⌂"
    AppDestination.MATCH -> "VS"
    AppDestination.RECORDS -> "▥"
    AppDestination.ACHIEVEMENTS -> "◇"
    AppDestination.SETTINGS -> "⚙"
}
