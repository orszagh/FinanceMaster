package sk.orszagh.financemaster.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceMasterApp(model: ReceiptsViewModel, recoveryError: StateFlow<Boolean>) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val capture by model.capture.collectAsStateWithLifecycle()
    val recoveryFailed by recoveryError.collectAsStateWithLifecycle()
    val processingFailed by model.processingError.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF225C46), secondary = Color(0xFF52665A),
        background = Color(0xFFF6F8F5), surface = Color(0xFFF6F8F5),
    )) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(when (route) {
                    "camera" -> "Nová účtenka"
                    "history" -> "História"
                    "detail/{id}" -> "Detail účtenky"
                    else -> "FinanceMaster"
                }) },
                navigationIcon = {
                    if (route != "home") TextButton(
                        onClick = { nav.popBackStack() }, enabled = !capture.saving,
                    ) { Text("Späť") }
                },
            )
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (processingFailed) Text("Spracovanie sa nepodarilo uložiť. Fotografie zostali zachované. Skontrolujte voľné miesto a otvorte aplikáciu znova.", color = MaterialTheme.colorScheme.error)
                if (recoveryFailed) Text(
                    "Obnovenie spracovania sa nepodarilo. Fotografie zostali uložené. Skúste aplikáciu otvoriť znova.",
                    color = MaterialTheme.colorScheme.error,
                )
                NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                    composable("home") {
                        val receipts by model.receipts.collectAsStateWithLifecycle()
                        HomeScreen(receipts, { nav.navigate("camera") }, { nav.navigate("history") },
                            { nav.navigate("detail/$it") })
                    }
                    composable("history") {
                        val receipts by model.receipts.collectAsStateWithLifecycle()
                        HistoryScreen(receipts) { nav.navigate("detail/$it") }
                    }
                    composable("camera") {
                        LaunchedEffect(capture.savedId) {
                            if (capture.savedId != null) {
                                model.consumeCapture()
                                nav.navigate("history") { popUpTo("camera") { inclusive = true }; launchSingleTop = true }
                            }
                        }
                        CameraScreen(capture, model::takePhoto)
                    }
                    composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        val id = requireNotNull(it.arguments?.getString("id"))
                        val receiptFlow = remember(id) { model.observe(id) }
                        val receipt by receiptFlow.collectAsStateWithLifecycle(initialValue = null)
                        ReceiptDetailScreen(receipt, model::imageFile, { model.retry(id) })
                    }
                }
            }
        }
    }
}
