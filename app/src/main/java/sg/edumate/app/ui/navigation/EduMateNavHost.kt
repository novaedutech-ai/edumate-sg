package sg.edumate.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import sg.edumate.app.ui.classes.ClassesScreen
import sg.edumate.app.ui.library.LibraryScreen
import sg.edumate.app.ui.live.LiveScreen
import sg.edumate.app.ui.plan.LessonEditorScreen
import sg.edumate.app.ui.plan.PlannerScreen
import sg.edumate.app.ui.plan.WorksheetScreen
import sg.edumate.app.ui.privacy.PrivacyCenterScreen
import sg.edumate.app.ui.today.TodayScreen

enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Outlined.Today),
    PLAN("plan", "Plan", Icons.Outlined.EditNote),
    LIBRARY("library", "Library", Icons.Outlined.LocalLibrary),
    LIVE("live", "Live", Icons.Outlined.Sensors),
    CLASSES("classes", "Classes", Icons.Outlined.Groups),
}

object Routes {
    const val EDITOR = "plan/editor/{planId}"
    fun editor(id: String) = "plan/editor/$id"
    const val WORKSHEET = "plan/worksheet?id={id}"
    fun worksheet(id: String? = null) = if (id == null) "plan/worksheet" else "plan/worksheet?id=$id"
    const val LIVE_WITH_PLAN = "live?planId={planId}"
    fun live(planId: String) = "live?planId=$planId"
    const val PRIVACY = "privacy"
}

/** Bottom bar on phones, navigation rail on tablets/foldables (NavigationSuiteScaffold adapts). */
@Composable
fun EduMateRoot(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route.orEmpty()
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevel.entries.forEach { dest ->
                item(
                    selected = current == dest.route || current.startsWith(dest.route + "/") || current.startsWith(dest.route + "?"),
                    onClick = {
                        nav.navigate(dest.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(dest.icon, contentDescription = null) },
                    label = { Text(dest.label) },
                )
            }
        },
    ) {
        NavHost(nav, startDestination = TopLevel.TODAY.route) {
            composable(TopLevel.TODAY.route) {
                TodayScreen(
                    onNewLesson = { nav.navigate(TopLevel.PLAN.route) },
                    onNewWorksheet = { nav.navigate(Routes.worksheet()) },
                    onStartLive = { nav.navigate(TopLevel.LIVE.route) },
                    onOpenPlan = { nav.navigate(Routes.editor(it)) },
                    onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                )
            }
            composable(TopLevel.PLAN.route) {
                PlannerScreen(
                    onOpenPlan = { nav.navigate(Routes.editor(it)) },
                    onOpenWorksheet = { nav.navigate(Routes.worksheet(it)) },
                    onNewWorksheet = { nav.navigate(Routes.worksheet()) },
                )
            }
            composable(Routes.EDITOR, arguments = listOf(navArgument("planId") { type = NavType.StringType })) {
                LessonEditorScreen(onBack = { nav.popBackStack() }, onOpenPlan = { nav.navigate(Routes.editor(it)) { popUpTo(Routes.EDITOR) { inclusive = true } } },
                    onLaunchLive = { nav.navigate(Routes.live(it)) })
            }
            composable(Routes.WORKSHEET, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                WorksheetScreen(onBack = { nav.popBackStack() })
            }
            composable(TopLevel.LIBRARY.route) { LibraryScreen() }
            composable(TopLevel.LIVE.route) { LiveScreen() }
            composable(Routes.LIVE_WITH_PLAN, arguments = listOf(navArgument("planId") { type = NavType.StringType; nullable = true; defaultValue = null })) { LiveScreen() }
            composable(TopLevel.CLASSES.route) { ClassesScreen() }
            composable(Routes.PRIVACY) { PrivacyCenterScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
