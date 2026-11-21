package com.privacyhub.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.UserSettingsEntity
import com.privacyhub.app.domain.model.SessionType
import com.privacyhub.app.domain.pomodoro.FocusSessionManager
import com.privacyhub.app.service.FocusSessionService
import kotlinx.coroutines.launch
import com.privacyhub.app.ui.analytics.AnalyticsScreen
import com.privacyhub.app.ui.canvas.CanvasScreen
import com.privacyhub.app.ui.focus.FocusScreen
import com.privacyhub.app.ui.home.HomeScreen
import com.privacyhub.app.ui.maps.MapsScreen
import com.privacyhub.app.ui.planner.PlannerScreen
import com.privacyhub.app.ui.search.SearchScreen
import com.privacyhub.app.ui.detox.DetoxScreen
import com.privacyhub.app.ui.review.ReviewScreen
import com.privacyhub.app.ui.settings.SettingsScreen
import com.privacyhub.app.ui.theme.FocusMapTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun FocusMapApp() {
    val repo = repository()
    val settings by repo.observeSettings().collectAsStateWithLifecycle(initialValue = UserSettingsEntity())
    LaunchedEffect(Unit) { repo.ensureSettings(); repo.ensureSchedulerSettings() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    LaunchedEffect(Unit) { if (repo.sessions.getActive() != null) FocusSessionService.start(context) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun startFocus(node: NodeEntity) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            val st = repo.settings.get() ?: UserSettingsEntity()
            FocusSessionManager(repo).start(node.projectId, node.id, SessionType.FOCUS, st.focusDurationSeconds)
            FocusSessionService.start(context)
            nav.navigate(Routes.FOCUS)
        }
    }

    FocusMapTheme(themeMode = settings.theme) {
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val tabs = listOf(
            Tab(Routes.HOME, "Home", Icons.Rounded.Home),
            Tab(Routes.MAPS, "Maps", Icons.Rounded.Hub),
            Tab(Routes.FOCUS, "Focus", Icons.Rounded.Timer),
            Tab(Routes.PLANNER, "Planner", Icons.Rounded.DateRange),
            Tab(Routes.DETOX, "Detox", Icons.Rounded.Block),
            Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
        )
        val showBar = tabs.any { it.route == currentRoute } ||
            currentRoute == Routes.REVIEW || currentRoute == Routes.ANALYTICS
        Scaffold(
            bottomBar = {
                if (showBar) NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = currentRoute == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(Routes.HOME)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label, maxLines = 1, fontSize = 8.sp, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenProject = { nav.navigate(Routes.canvas(it)) },
                        onGoMaps = { nav.navigate(Routes.MAPS) },
                        onGoFocus = { nav.navigate(Routes.FOCUS) },
                        onGoSearch = { nav.navigate(Routes.SEARCH) },
                    )
                }
                composable(Routes.MAPS) { MapsScreen(onOpenProject = { nav.navigate(Routes.canvas(it)) }) }
                composable(Routes.FOCUS) { FocusScreen(onGoMaps = { nav.navigate(Routes.MAPS) }) }
                composable(Routes.PLANNER) { PlannerScreen() }
                composable(Routes.DETOX) { DetoxScreen() }
                composable(Routes.SEARCH) { SearchScreen(
                    onOpenProject = { nav.navigate(Routes.canvas(it)) },
                    onOpenNode = { pid, _ -> nav.navigate(Routes.canvas(pid)) },
                ) }
                composable(Routes.REVIEW) { ReviewScreen() }
                composable(
                    Routes.REVIEW_NODE,
                    arguments = listOf(navArgument("nodeId") { type = NavType.LongType }),
                ) { entry -> ReviewScreen(filterNodeId = entry.arguments?.getLong("nodeId")) }
                composable(Routes.ANALYTICS) { AnalyticsScreen() }
                composable(Routes.SETTINGS) { SettingsScreen(
                    onGoReview = { nav.navigate(Routes.REVIEW) },
                    onGoAnalytics = { nav.navigate(Routes.ANALYTICS) },
                ) }
                composable(
                    Routes.CANVAS,
                    arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
                ) { entry ->
                    val pid = entry.arguments?.getLong("projectId") ?: 0L
                    CanvasScreen(projectId = pid, onBack = { nav.popBackStack() }, onStartFocus = { node -> startFocus(node) },
                        onRecall = { node -> nav.navigate(Routes.reviewNode(node.id)) },
                        onReviewNode = { node -> nav.navigate(Routes.reviewNode(node.id)) })
                }
            }
        }
    }
}
