package com.padanam.psc.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.padanam.psc.AppContainer
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.data.model.ViewMode
import com.padanam.psc.ui.admin.AdminScreen
import com.padanam.psc.ui.accounts.AccountsScreen
import com.padanam.psc.ui.auth.AuthLoadingScreen
import com.padanam.psc.ui.auth.LoginScreen
import com.padanam.psc.ui.components.FloatingTabBar
import com.padanam.psc.ui.components.MainTab
import com.padanam.psc.ui.decks.DeckLibraryScreen
import com.padanam.psc.ui.decks.DeckStudyScreen
import com.padanam.psc.ui.decks.DeckTopicScreen
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
import kotlinx.coroutines.delay

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val ACCOUNTS = "accounts"
    const val ADD_ACCOUNT = "addaccount"
    const val ADMIN = "admin"
    const val SCAN = "scan"
    const val ANALYSING = "analysing"
    const val QUIZ = "quiz"
    const val SET = "set/{setId}"
    const val SCAN_RESULT = "scanresult/{setId}?appended={appended}&added={added}"
    const val EXAM_SETUP = "examsetup/{setId}?mode={mode}"
    const val RESULTS = "results/{attemptId}"
    const val REVIEW = "review/{attemptId}"
    const val DECKS = "decks"
    const val DECK_TOPIC = "decktopic?topic={topic}"
    const val DECK_STUDY = "deckstudy?topic={topic}&sub={sub}"

    fun set(setId: String) = "set/$setId"
    fun deckTopic(topic: String) = "decktopic?topic=${Uri.encode(topic)}"
    fun deckStudy(topic: String, sub: String = "") =
        "deckstudy?topic=${Uri.encode(topic)}&sub=${Uri.encode(sub)}"
    fun scan(gallery: Boolean = false) = "$SCAN?gallery=$gallery"
    fun scanResult(setId: String, appended: Boolean = false, added: Int = 0) =
        "scanresult/$setId?appended=$appended&added=$added"
    fun examSetup(setId: String, mode: String) = "examsetup/$setId?mode=$mode"
    fun results(attemptId: String) = "results/$attemptId"
    fun review(attemptId: String) = "review/$attemptId"
}

@Composable
fun PadanamRoot(
    container: AppContainer,
    appVm: AppViewModel
) {
    val authReady by appVm.authReady.collectAsStateWithLifecycle()
    val session by appVm.session.collectAsStateWithLifecycle()
    val settings by appVm.settings.collectAsStateWithLifecycle()

    val account = session
    when {
        !authReady -> AuthLoadingScreen()
        account == null -> LoginScreen(
            appVm = appVm,
            initialServer = settings.serverUrl
        )
        else -> key(account.key) {
            val adminView = account.user.isAdmin && settings.viewMode != ViewMode.LEARNER
            SignedInRoot(
                container = container,
                appVm = appVm,
                admin = adminView,
                realAdmin = account.user.isAdmin,
                serverUrl = settings.serverUrl
            )
        }
    }
}

@Composable
private fun SignedInRoot(
    container: AppContainer,
    appVm: AppViewModel,
    admin: Boolean,
    realAdmin: Boolean,
    serverUrl: String
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val tabRoute = MainTab.entries.firstOrNull { it.route == route }?.route
    val showTabs = tabRoute != null

    // Back on the home tab does not silently drop the app into the task
    // switcher: the first press asks for confirmation and the second press
    // removes the task for real.
    val context = LocalContext.current
    var exitArmed by remember { mutableStateOf(false) }
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(2_000)
            exitArmed = false
        }
    }
    BackHandler(enabled = route == Routes.HOME) {
        if (exitArmed) {
            (context as? Activity)?.finishAndRemoveTask()
        } else {
            exitArmed = true
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

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

    fun openScan(gallery: Boolean = false) {
        if (!admin) {
            appVm.emit(UiEvent("Only the administrator can scan pages.", error = true))
            return
        }
        navController.navigate(Routes.scan(gallery))
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
        val layoutDirection = LocalLayoutDirection.current
        // The tab bar floats over the page, so the screen background extends
        // behind it instead of leaving a solid strip around the pill.
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection)
            )
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    appVm = appVm,
                    canScan = admin,
                    onScan = { openScan() },
                    onImport = { openScan(gallery = true) },
                    onOpenSet = { navController.navigate(Routes.set(it)) },
                    onOpenLibrary = { navigateTab(Routes.LIBRARY) },
                    onResumeExam = { navController.navigate(Routes.QUIZ) },
                    onOpenProfile = { navigateTab(Routes.PROFILE) },
                    onStartMode = { setId, mode ->
                        navController.navigate(Routes.examSetup(setId, mode))
                    },
                    onOpenAttempt = { navController.navigate(Routes.review(it)) },
                    onOpenDecks = { navController.navigate(Routes.DECKS) },
                    onContinueDeck = { topic, sub ->
                        navController.navigate(Routes.deckStudy(topic, sub))
                    }
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    appVm = appVm,
                    canScan = admin,
                    onScan = { openScan() },
                    onImport = { openScan(gallery = true) },
                    onOpenSet = { navController.navigate(Routes.set(it)) },
                    onOpenDecks = { navController.navigate(Routes.DECKS) }
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
                    canManage = admin,
                    realAdmin = realAdmin,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenAdmin = { navController.navigate(Routes.ADMIN) },
                    onOpenAttempt = { navController.navigate(Routes.review(it)) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    appVm = appVm,
                    onBack = { navController.popBackStack() },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenAdmin = { navController.navigate(Routes.ADMIN) }
                )
            }

            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    appVm = appVm,
                    onBack = { navController.popBackStack() },
                    onAddAccount = { navController.navigate(Routes.ADD_ACCOUNT) }
                )
            }

            composable(Routes.ADD_ACCOUNT) {
                LoginScreen(
                    appVm = appVm,
                    initialServer = serverUrl,
                    addMode = true,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ADMIN) {
                AdminScreen(
                    container = container,
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
                    canManage = admin,
                    onBack = { navController.popBackStack() },
                    onStartSetup = { mode ->
                        navController.navigate(Routes.examSetup(setId, mode))
                    },
                    onOpenAttempt = { navController.navigate(Routes.review(it)) },
                    onOpenDeck = { topic, sub ->
                        navController.navigate(Routes.deckStudy(topic, sub))
                    }
                )
            }

            composable(Routes.DECKS) {
                DeckLibraryScreen(
                    appVm = appVm,
                    onBack = { navController.popBackStack() },
                    onOpenLibrary = { navigateTab(Routes.LIBRARY) },
                    onOpenTopic = { navController.navigate(Routes.deckTopic(it)) },
                    onOpenStudy = { topic, sub ->
                        navController.navigate(Routes.deckStudy(topic, sub))
                    }
                )
            }

            composable(
                route = Routes.DECK_TOPIC,
                arguments = listOf(
                    navArgument("topic") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                DeckTopicScreen(
                    appVm = appVm,
                    topicName = entry.arguments?.getString("topic").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenStudy = { topic, sub ->
                        navController.navigate(Routes.deckStudy(topic, sub))
                    }
                )
            }

            composable(
                route = Routes.DECK_STUDY,
                arguments = listOf(
                    navArgument("topic") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("sub") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                DeckStudyScreen(
                    appVm = appVm,
                    mainTopic = entry.arguments?.getString("topic").orEmpty(),
                    subTopic = entry.arguments?.getString("sub").orEmpty(),
                    onClose = { navController.popBackStack() },
                    onOpenTopic = { topic ->
                        navController.navigate(Routes.deckTopic(topic)) {
                            popUpTo(Routes.DECK_STUDY) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
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
                    onSaved = { setId, appended, added ->
                        navController.navigate(Routes.scanResult(setId, appended, added)) {
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
                arguments = listOf(
                    navArgument("setId") { type = NavType.StringType },
                    navArgument("appended") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("added") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { entry ->
                val setId = entry.arguments?.getString("setId").orEmpty()
                ScanResultScreen(
                    appVm = appVm,
                    setId = setId,
                    appended = entry.arguments?.getBoolean("appended") == true,
                    added = entry.arguments?.getInt("added") ?: 0,
                    onClose = { navController.popBackStack() },
                    onViewSet = { navController.navigate(Routes.set(setId)) },
                    onPractice = { navController.navigate(Routes.examSetup(setId, QuizMode.PRACTICE)) },
                    onScanAgain = { openScan() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }
    }
}
