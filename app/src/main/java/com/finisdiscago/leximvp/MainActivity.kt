package com.finisdiscago.leximvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.finisdiscago.leximvp.feature.learning.LearningRoute
import com.finisdiscago.leximvp.feature.reading.ArticleListRoute
import com.finisdiscago.leximvp.feature.reading.ArticleReaderRoute
import com.finisdiscago.leximvp.feature.review.ReviewRoute
import com.finisdiscago.leximvp.feature.profile.SettingsRoute
import com.finisdiscago.leximvp.feature.stats.StatisticsRoute
import com.finisdiscago.leximvp.feature.vocabulary.BookListRoute
import com.finisdiscago.leximvp.feature.vocabulary.WordListRoute
import com.finisdiscago.leximvp.core.ui.LexiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LexiApplication
        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = com.finisdiscago.leximvp.domain.model.UserSettings()
            )
            LexiTheme(darkTheme = settings.themeMode == com.finisdiscago.leximvp.domain.model.ThemeMode.Night) {
                val colors = MaterialTheme.colorScheme
                androidx.compose.runtime.SideEffect {
                    val night = settings.themeMode == com.finisdiscago.leximvp.domain.model.ThemeMode.Night
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !night
                        isAppearanceLightNavigationBars = !night
                    }
                    @Suppress("DEPRECATION")
                    window.statusBarColor = colors.background.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = colors.surface.toArgb()
                }
                Surface(color = MaterialTheme.colorScheme.background) {
                    LexiNavigation(app, settings)
                }
            }
        }
    }
}

@Composable
private fun LexiNavigation(app: LexiApplication, settings: com.finisdiscago.leximvp.domain.model.UserSettings) {
    var pendingTaskCount by remember { mutableIntStateOf(0) }
    val navController = rememberNavController()
    LaunchedEffect(settings.speechRate) {
        app.audioService.setSpeechRate(settings.speechRate)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()
    val destinations = listOf(
        MainDestination("learn", "学习", R.drawable.tab_review_unselected, R.drawable.tab_review_selected),
        MainDestination("books", "词库", R.drawable.tab_book_unselected, R.drawable.tab_book_selected),
        MainDestination("stats", "统计", R.drawable.tab_chart_unselected, R.drawable.tab_chart_selected),
        MainDestination("profile", "我的", R.drawable.tab_setting_unselected, R.drawable.tab_setting_selected)
    )
    fun openMainDestination(route: String) {
        if (route == "learn") {
            app.learningRepository.selectWords(emptyList())
            // The start entry already exists. Restoring it after a direct library
            // entry can restore the just-popped library stack instead.
            navController.popBackStack("learn", inclusive = false, saveState = true)
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in setOf("learn", "books", "stats", "profile")) {
                com.finisdiscago.leximvp.core.ui.LexiBottomBar(
                    tabs = destinations.map { com.finisdiscago.leximvp.core.ui.LexiTab(it.route, it.label, it.icon, it.selectedIcon) },
                    selectedRoute = currentRoute, pending = pendingTaskCount,
                    onSelect = { destination -> openMainDestination(destination.route) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "learn",
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        ) {
            composable("learn") {
                com.finisdiscago.leximvp.feature.learning.StudyOverviewScreen(
                    app = app,
                    onStartLearning = {
                        app.learningRepository.selectWords(emptyList())
                        navController.navigate("learn/session")
                    },
                    onStartReview = { navController.navigate("review") },
                    onOpenReading = { navController.navigate("articles") },
                    onOpenBooks = { openMainDestination("books") },
                    onOpenStatistics = { openMainDestination("stats") },
                    onTaskCountChange = { pendingTaskCount = it }
                )
            }
            composable("learn/session") {
                LearningRoute(
                    repository = app.learningRepository,
                    audioService = app.audioService,
                    settingsRepository = app.settingsRepository,
                    autoPlayAudio = settings.autoPlayAudio,
                    onOpenReview = { navController.navigate("review") },
                    onOpenContent = { openMainDestination("books") },
                    onOpenWord = { navController.navigate("entry/$it") }
                )
            }
            composable("review") {
                ReviewRoute(
                    repository = app.reviewRepository,
                    audioService = app.audioService,
                    settingsRepository = app.settingsRepository,
                    autoPlayAudio = settings.autoPlayAudio,
                    onOpenNewWords = { navController.navigate("learn/session") },
                    onOpenWord = { navController.navigate("entry/$it") }
                )
            }
            composable("books") {
                com.finisdiscago.leximvp.feature.vocabulary.UnifiedLexiconScreen(
                    app, openWord = { navController.navigate("entry/$it") },
                    openReading = { navController.navigate("articles") },
                    openExams = { navController.navigate("exams") },
                    manageBooks = { navController.navigate("books/manage") }
                )
            }
            composable("books/manage") {
                BookListRoute(
                    repository = app.vocabularyRepository,
                    packageImporter = app.vocabularyPackageImporter,
                    onOpenBook = { navController.navigate("books/$it") },
                    onOpenArticles = { navController.navigate("articles") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("entry/{wordId}", arguments = listOf(navArgument("wordId") { type = NavType.LongType })) { entry ->
                com.finisdiscago.leximvp.feature.vocabulary.LexicalEntryScreen(app, entry.arguments!!.getLong("wordId"), navController::popBackStack)
            }
            composable("exams") {
                com.finisdiscago.leximvp.feature.exam.ExamScreen(app, navController::popBackStack) { navController.navigate("entry/$it") }
            }

            composable("articles") {
                ArticleListRoute(
                    repository = app.articleRepository,
                    documentImporter = app.documentImporter,
                    onBackToBooks = { openMainDestination("books") },
                    onOpenArticle = { articleId ->
                        navController.navigate("articles/$articleId")
                    }
                )
            }
            composable(
                route = "articles/{articleId}",
                arguments = listOf(
                    navArgument("articleId") { type = NavType.LongType }
                )
            ) { entry ->
                ArticleReaderRoute(
                    articleId = entry.arguments?.getLong("articleId") ?: return@composable,
                    articleRepository = app.articleRepository,
                    vocabularyRepository = app.vocabularyRepository,
                    audioService = app.audioService,
                    settingsRepository = app.settingsRepository,
                    onBack = navController::popBackStack,
                    onStartLearning = { app.learningRepository.selectWords(emptyList()); navController.navigate("learn/session") },
                    onStartCapturedLearning = { ids -> app.learningRepository.selectWords(ids); navController.navigate("learn/session") }
                )
            }
            composable(
                route = "books/{bookId}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType }
                )
            ) { entry ->
                WordListRoute(
                    bookId = entry.arguments?.getLong("bookId") ?: return@composable,
                    repository = app.vocabularyRepository,
                    onBack = navController::popBackStack
                )
            }
            composable("stats") {
                StatisticsRoute(
                    repository = app.statisticsRepository,
                    settingsRepository = app.settingsRepository
                )
            }
            composable("profile") {
                com.finisdiscago.leximvp.feature.profile.ProfileScreen(
                    settings = settings,
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenBooks = { openMainDestination("books") },
                    onOpenReading = { navController.navigate("articles") },
                    onOpenStatistics = { openMainDestination("stats") },
                    onAdjustPlan = { navController.navigate("settings?section=learning") },
                    onOpenBackup = { navController.navigate("settings?section=data") }
                )
            }
            composable("settings?section={section}", arguments = listOf(navArgument("section") { defaultValue = "appearance" })) { entry ->
                SettingsRoute(
                    repository = app.settingsRepository,
                    reminderScheduler = app.reminderScheduler,
                    backupService = app.backupService,
                    onOpenVocabulary = { openMainDestination("books") },
                    onBack = { navController.popBackStack() },
                    initialSection = when (entry.arguments?.getString("section")) { "learning" -> 1; "data" -> 2; else -> 0 }
                )
            }
        }
    }
}

private data class MainDestination(
    val route: String,
    val label: String,
    val icon: Int,
    val selectedIcon: Int
)
