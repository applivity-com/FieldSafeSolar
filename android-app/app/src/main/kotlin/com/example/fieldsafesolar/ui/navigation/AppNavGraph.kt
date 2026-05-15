package com.example.fieldsafesolar.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.fieldsafesolar.data.model.SafetyReport
import com.example.fieldsafesolar.di.ServiceProvider
import com.example.fieldsafesolar.ui.screens.EvidenceGalleryScreen
import com.example.fieldsafesolar.ui.screens.FinalVerdictScreen
import com.example.fieldsafesolar.ui.screens.GemmaLoadingScreen
import com.example.fieldsafesolar.ui.screens.InspectionTypeSelectScreen
import com.example.fieldsafesolar.ui.screens.InspectorReportListScreen
import com.example.fieldsafesolar.ui.screens.InspectorReviewScreen
import com.example.fieldsafesolar.ui.screens.ModelDownloadScreen
import com.example.fieldsafesolar.ui.screens.ModeSelectScreen
import com.example.fieldsafesolar.ui.screens.PreTaskPlanScreen
import com.example.fieldsafesolar.ui.screens.QuestionSliderScreen
import com.example.fieldsafesolar.ui.screens.ReportDetailScreen
import com.example.fieldsafesolar.ui.screens.ReportListScreen
import com.example.fieldsafesolar.ui.screens.SettingsScreen
import com.example.fieldsafesolar.ui.theme.FieldSafeColors

sealed class Route(val route: String) {
    // New primary flow
    object ModeSelect : Route("mode_select")
    object InspectionTypeSelect : Route("inspection_type/{mode}") {
        fun createRoute(mode: String) = "inspection_type/$mode"
    }
    object QuestionSlider : Route("question_slider/{inspectionType}/{mode}") {
        fun createRoute(inspectionType: String, mode: String) = "question_slider/$inspectionType/$mode"
    }
    object GemmaLoading : Route("gemma_loading/{inspectionType}/{mode}") {
        fun createRoute(inspectionType: String, mode: String) = "gemma_loading/$inspectionType/$mode"
    }
    object FinalVerdict : Route("final_verdict/{inspectionType}/{mode}") {
        fun createRoute(inspectionType: String, mode: String) = "final_verdict/$inspectionType/$mode"
    }

    // Reports
    object ReportDetail : Route("report_detail/{reportId}") {
        fun createRoute(reportId: String) = "report_detail/$reportId"
    }
    object ReportList : Route("report_list")
    object EvidenceGallery : Route("evidence_gallery/{reportId}") {
        fun createRoute(reportId: String) = "evidence_gallery/$reportId"
    }

    // Inspector review
    object InspectorReviewList : Route("inspector_review_list")
    object InspectorReview : Route("inspector_review/{reportId}") {
        fun createRoute(reportId: String) = "inspector_review/$reportId"
    }

    // Settings
    object Settings : Route("settings")

    // Pre-task JHA gate
    object PreTaskPlan : Route("pre_task_plan/{mode}") {
        fun createRoute(mode: String) = "pre_task_plan/$mode"
    }

    // Legacy — kept so existing code references don't crash
    object Home : Route("home")
    object InspectionSelection : Route("inspection_selection/{inspectionType}") {
        fun createRoute(inspectionType: String) = "inspection_selection/$inspectionType"
    }
    object EvidenceCapture : Route("evidence_capture/{inspectionType}") {
        fun createRoute(inspectionType: String) = "evidence_capture/$inspectionType"
    }
    object VoiceInteraction : Route("voice_interaction/{inspectionType}") {
        fun createRoute(inspectionType: String) = "voice_interaction/$inspectionType"
    }
}

@Composable
fun FieldSafeNavGraph(
    navController: NavHostController,
    startDestination: String = Route.ModeSelect.route
) {
    val context = LocalContext.current
    val resolvedStart = remember {
        val modelsDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("models")
        if (modelsDir.resolve("gemma-4-E2B-it.litertlm").exists())
            startDestination
        else
            "model_download"
    }

    NavHost(navController = navController, startDestination = resolvedStart) {

        // ── Model download (first launch) ─────────────────────────
        composable("model_download") {
            ModelDownloadScreen(
                onComplete = {
                    navController.navigate(Route.ModeSelect.route) {
                        popUpTo("model_download") { inclusive = true }
                    }
                },
                onDemoMode = {
                    navController.navigate(Route.ModeSelect.route) {
                        popUpTo("model_download") { inclusive = true }
                    }
                }
            )
        }

        // ── New primary flow ──────────────────────────────────────
        composable(Route.ModeSelect.route) {
            ModeSelectScreen(navController)
        }

        composable(
            Route.PreTaskPlan.route,
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { back ->
            PreTaskPlanScreen(navController, back.arguments?.getString("mode"))
        }

        composable(
            Route.InspectionTypeSelect.route,
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { back ->
            InspectionTypeSelectScreen(navController, back.arguments?.getString("mode"))
        }

        composable(
            Route.QuestionSlider.route,
            arguments = listOf(
                navArgument("inspectionType") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType },
            )
        ) { back ->
            QuestionSliderScreen(
                navController = navController,
                inspectionTypeStr = back.arguments?.getString("inspectionType"),
                modeStr = back.arguments?.getString("mode"),
            )
        }

        composable(
            Route.GemmaLoading.route,
            arguments = listOf(
                navArgument("inspectionType") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType },
            )
        ) { back ->
            val inspectionType = back.arguments?.getString("inspectionType") ?: ""
            val mode = back.arguments?.getString("mode") ?: "worker"
            GemmaLoadingScreen(navController, inspectionType, mode)
        }

        composable(
            Route.FinalVerdict.route,
            arguments = listOf(
                navArgument("inspectionType") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType },
            )
        ) { back ->
            val inspectionType = back.arguments?.getString("inspectionType") ?: ""
            val mode = back.arguments?.getString("mode") ?: "worker"
            FinalVerdictScreen(navController, inspectionType, mode)
        }

        // ── Reports ───────────────────────────────────────────────
        composable(Route.ReportList.route) {
            var reports by remember { mutableStateOf<List<SafetyReport>>(emptyList()) }
            LaunchedEffect(Unit) { reports = ServiceProvider.getReportRepository().getAllReports() }
            ReportListScreen(navController, reports)
        }

        composable(
            Route.ReportDetail.route,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
        ) { back ->
            val reportId = back.arguments?.getString("reportId") ?: ""
            var report by remember { mutableStateOf<SafetyReport?>(null) }
            LaunchedEffect(reportId) { report = ServiceProvider.getReportRepository().getReport(reportId) }
            ReportDetailScreen(navController, report)
        }

        composable(
            Route.EvidenceGallery.route,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
        ) { back ->
            val reportId = back.arguments?.getString("reportId") ?: ""
            var evidenceUris by remember { mutableStateOf<List<String>>(emptyList()) }
            LaunchedEffect(reportId) {
                evidenceUris = ServiceProvider.getReportRepository().getReport(reportId)
                    ?.evidence?.map { it.fileUri } ?: emptyList()
            }
            EvidenceGalleryScreen(navController, evidenceUris)
        }

        // ── Inspector review ──────────────────────────────────────
        composable(Route.InspectorReviewList.route) {
            var reports by remember { mutableStateOf<List<SafetyReport>>(emptyList()) }
            LaunchedEffect(Unit) { reports = ServiceProvider.getReportRepository().getAllReports() }
            InspectorReportListScreen(navController, reports)
        }

        composable(
            Route.InspectorReview.route,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
        ) { back ->
            val reportId = back.arguments?.getString("reportId") ?: ""
            var report by remember { mutableStateOf<SafetyReport?>(null) }
            LaunchedEffect(reportId) { report = ServiceProvider.getReportRepository().getReport(reportId) }
            InspectorReviewScreen(navController, report)
        }

        // ── Settings ──────────────────────────────────────────────
        composable(Route.Settings.route) {
            SettingsScreen(navController)
        }

        // ── Legacy fallback ───────────────────────────────────────
        composable(Route.Home.route) {
            ModeSelectScreen(navController)
        }
    }
}

