package com.padanam.psc.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.padanam.psc.AppContainer
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.ui.components.FloatingTabBar
import com.padanam.psc.ui.components.MainTab
import com.padanam.psc.ui.exam.ExamSetupScreen
import com.padanam.psc.ui.home.HomeScreen
import com.padanam.psc.ui.library.LibraryScreen
import com.padanam.psc.ui.profile.ProfileScreen
import com.padanam.psc.ui.progress.ProgressScreen
import com.padanam.psc.ui.quiz.QuizScreen
import com.padanam.psc.ui.quiz.ResultsScreen
import com.padanam.psc.ui.quiz.ReviewScreen
import com.padanam.psc.ui.scan.AnalysingScreen
import com.padanam.psc.ui.scan.ScanCameraScreen
import com.padanam.psc.ui.scan.ScanResultScreen
import com.padanam.psc.ui.setdetail.SetDetailScreen
import com.padanam.psc.ui.settings.SettingsScreen
import com.padanam.psc.ui.theme.Cloud

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val SCAN = "scan"
    const val ANALYSING = "analysing"
    const val QUIZ = "quiz"
    const val SET = "set/{setId}"
    const val SCAN_RESULT = "scanresult/{setId}"
    const val EXAM_SETUP = "examsetup/{setId}?mode={mode}"
    const val RESULTS = "results/{attemptId}"
    const val REVIEW = "review/{attemptId}"

    fun set(setId: String) = "set/$setId"
    fun scan(gallery: Boolean = false) = "$SCAN?gallery=$gallery"
    fun scanResult(setId: String) = "scanresult/$setId"
    fun examSetup(setId: String, mode: String) = "examsetup/$setId?mode=$mode"
    fun results(attemptId: String) = "results/$attemptId"
    fun review(attemptId: String) = "review/$attemptId"
}

@Composable
fun PadanamRoot(
    container: AppContainer,
    appVm: AppViewModel
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val tabRoute = MainTab.entries.firstOrNull { it.route == route }?.route
    val showTabs = tabRoute != null

    LaunchedEffect(appVm) {
        appVm.events.collect { event ->
            snackbarHostState.showSnackbar(event.message)
        }
    }

    fun navigateTab(target: String) {
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goHome() {
        navController.navigate(Routes.HOME) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = Cloud,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showTabs) {
                FloatingTabBar(
                    selected = tabRoute ?: Routes.HOME,
                    onSelect = ::navigateTab
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    appVm = appVm,
                    onScan = { navController.navigate(Routes.scan()) },
                    onImport = { navController.navigate(Routes.scan(gallery = true)) },
                    onOpenSet = { navController.navigate(Routes.set(it)) },
                    onOpenLibrary = { navigateTab(Routes.LIBRARY) },
                    onResumeExam = { navController.navigate(Routes.QUIZ) },
                    onOpenProfile = { navigateTab(Routes.PROFILE) },
                    onStartMode = { setId, mode ->
                        navController.navigate(Routes.examSetup(setId, mode))
                    },
                    onOpenAttempt = { navController.navigate(Routes.review(it)) }
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    appVm = appVm,
                    onScan = { navController.navigate(Routes.scan()) },
                    onImport = { navController.navigate(Routes.scan(gallery = true)) },
                    onOpenSet = { navController.navigate(Routes.set(it)) }
                )
            }

            composable(Routes.PROGRESS) {
                ProgressScreen(
                    appVm = appVm,
                    onOpenAttempt = { navController.navigate(Routes.review(it)) },
                    onOpenLibrary = { navigateTab(Routes.LIBRARY) }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    appVm = appVm,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAttempt = { navController.navigate(Routes.review(it)) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    appVm = appVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.SET,
                arguments = listOf(navArgument("setId") { type = NavType.StringType })
            ) { entry ->
                val setId = entry.arguments?.getString("setId").orEmpty()
                SetDetailScreen(
                    appVm = appVm,
                    setId = setId,
                    onBack = { navController.popBackStack() },
                    onStartSetup = { mode ->
                        navController.navigate(Routes.examSetup(setId, mode))
                    },
                    onOpenAttempt = { navController.navigate(Routes.review(it)) }
                )
            }

            composable(
                route = Routes.EXAM_SETUP,
                arguments = listOf(
                    navArgument("setId") { type = NavType.StringType },
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = QuizMode.EXAM
                    }
                )
            ) { entry ->
                val setId = entry.arguments?.getString("setId").orEmpty()
                val mode = entry.arguments?.getString("mode") ?: QuizMode.EXAM
                ExamSetupScreen(
                    appVm = appVm,
                    setId = setId,
                    initialMode = mode,
                    onBack = { navController.popBackStack() },
                    onStarted = {
                        navController.navigate(Routes.QUIZ) { launchSingleTop = true }
                    }
                )
            }

            composable(Routes.QUIZ) {
                QuizScreen(
                    container = container,
                    onFinished = { attemptId ->
                        navController.navigate(Routes.results(attemptId)) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                    onLeave = { goHome() }
                )
            }

            composable(
                route = Routes.RESULTS,
                arguments = listOf(navArgument("attemptId") { type = NavType.StringType })
            ) { entry ->
                val attemptId = entry.arguments?.getString("attemptId").orEmpty()
                ResultsScreen(
                    appVm = appVm,
                    attemptId = attemptId,
                    onHome = { goHome() },
                    onReview = { navController.navigate(Routes.review(it)) }
                )
            }

            composable(
                route = Routes.REVIEW,
                arguments = listOf(navArgument("attemptId") { type = NavType.StringType })
            ) { entry ->
                val attemptId = entry.arguments?.getString("attemptId").orEmpty()
                ReviewScreen(
                    appVm = appVm,
                    attemptId = attemptId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Routes.SCAN}?gallery={gallery}",
                arguments = listOf(
                    navArgument("gallery") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                ScanCameraScreen(
                    container = container,
                    openGalleryOnStart = entry.arguments?.getBoolean("gallery") == true,
                    onClose = { navController.popBackStack() },
                    onImagesReady = { navController.navigate(Routes.ANALYSING) }
                )
            }

            composable(Routes.ANALYSING) {
                AnalysingScreen(
                    container = container,
                    onSaved = { setId ->
                        navController.navigate(Routes.scanResult(setId)) {
                            // HOME is a plain route, so popUpTo always resolves; the camera
                            // and analysing screens are removed from the back stack.
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                    onClose = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(
                route = Routes.SCAN_RESULT,
                arguments = listOf(navArgument("setId") { type = NavType.StringType })
            ) { entry ->
                val setId = entry.arguments?.getString("setId").orEmpty()
                ScanResultScreen(
                    appVm = appVm,
                    setId = setId,
                    onClose = { navController.popBackStack() },
                    onViewSet = { navController.navigate(Routes.set(setId)) },
                    onPractice = { navController.navigate(Routes.examSetup(setId, QuizMode.PRACTICE)) },
                    onScanAgain = { navController.navigate(Routes.scan()) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }
    }
}
