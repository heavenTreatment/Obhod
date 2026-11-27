@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.heaventreat.obhod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.heaventreat.obhod.diagnostics.TransferTestConfig
import com.heaventreat.obhod.R
import com.heaventreat.obhod.strategy.ByeByeDpiManager
import com.heaventreat.obhod.update.AppUpdate
import com.heaventreat.obhod.update.UpdateDownloadBanner
import com.heaventreat.obhod.update.UpdateDownloadProgress
import com.heaventreat.obhod.update.UpdatePromptDialog
import kotlinx.coroutines.launch

internal enum class ObhodTab(val label: String, val icon: Int) {
    Home("Главная", R.drawable.ic_home),
    Logs("Логи", R.drawable.ic_audit),
    Source("Исходник", R.drawable.ic_github)
}

@Composable
internal fun ObhodApp(
    vpnRunning: Boolean,
    starting: Boolean,
    selectedStrategyIndex: Int,
    onStrategySelected: (Int) -> Unit,
    onToggle: () -> Unit,
    showEnableConfirmation: Boolean,
    enableConfirmationCached: Boolean,
    onEnableConfirmationDismissed: () -> Unit,
    onEnableConfirmationAccepted: () -> Unit,
    showTacticHelp: Boolean,
    onTacticHelpAccepted: () -> Unit,
    batteryOptimizationIgnored: Boolean,
    onRequestBatteryOptimization: () -> Unit,
    onExportDiagnosticReport: (String) -> Unit,
    transferTestRunning: Boolean,
    transferTestStatus: String,
    onRunTransferTest: (TransferTestConfig) -> Unit,
    autoSelectionStatus: String,
    autoSelectionProgress: ByeByeDpiManager.Progress?,
    byeByeDpiFullAnalysis: Boolean,
    onByeByeDpiFullAnalysisChanged: (Boolean) -> Unit,
    onRunAutoSelection: () -> Unit,
    availableUpdate: AppUpdate?,
    updateDownloadProgress: UpdateDownloadProgress?,
    onRemindUpdateLater: (AppUpdate) -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onOpenUpdateGitHub: (AppUpdate) -> Unit,
    onDismissUpdateDownload: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pageStateHolder = rememberSaveableStateHolder()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showHelp by rememberSaveable(showTacticHelp) { mutableStateOf(showTacticHelp) }
    var showProxyDialog by rememberSaveable { mutableStateOf(false) }
    val selectedTab = ObhodTab.entries[tabIndex]

    val copyText: (String) -> Unit = remember(context, snackbarHostState, scope) {
        { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Obhod", text))
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("Скопировано")
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        val contentInsets = if (useNavigationRail) {
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            )
        } else {
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            )
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = contentInsets,
            topBar = {
                AnimatedVisibility(
                    visible = updateDownloadProgress != null,
                    enter = fadeIn(animationSpec = tween(220)) +
                        expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                    exit = fadeOut(animationSpec = tween(150)) +
                        shrinkVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        )
                ) {
                    Column(Modifier.statusBarsPadding()) {
                        updateDownloadProgress?.let {
                            UpdateDownloadBanner(it, onDismissUpdateDownload)
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    ObhodNavigationBar(
                        selected = tabIndex,
                        onSelect = { tabIndex = it }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    ObhodNavigationRail(
                        selected = tabIndex,
                        onSelect = { tabIndex = it }
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    pageStateHolder.SaveableStateProvider(selectedTab.name) {
                        when (selectedTab) {
                            ObhodTab.Home -> HomeScreen(
                                vpnRunning = vpnRunning,
                                starting = starting,
                                selectedStrategyIndex = selectedStrategyIndex,
                                onStrategySelected = onStrategySelected,
                                batteryOptimizationIgnored = batteryOptimizationIgnored,
                                onRequestBatteryOptimization = onRequestBatteryOptimization,
                                autoSelectionStatus = autoSelectionStatus,
                                autoSelectionProgress = autoSelectionProgress,
                                byeByeDpiFullAnalysis = byeByeDpiFullAnalysis,
                                onByeByeDpiFullAnalysisChanged = onByeByeDpiFullAnalysisChanged,
                                onRunAutoSelection = onRunAutoSelection,
                                onToggle = onToggle,
                                onOpenProxy = { showProxyDialog = true }
                            )

                            ObhodTab.Logs -> LogsScreen(
                                onCopyText = copyText,
                                onExportDiagnosticReport = onExportDiagnosticReport,
                                vpnRunning = vpnRunning,
                                transferTestRunning = transferTestRunning,
                                transferTestStatus = transferTestStatus,
                                onRunTransferTest = onRunTransferTest
                            )
                            ObhodTab.Source -> SourceScreen()
                        }
                    }
                    if (showHelp) {
                        TacticHelpDialog(
                            onAccepted = {
                                onTacticHelpAccepted()
                                showHelp = false
                            }
                        )
                    }
                    if (showProxyDialog) {
                        ProxyDialog(
                            onDismiss = { showProxyDialog = false },
                            onCopyText = copyText
                        )
                    }
                    if (showEnableConfirmation) {
                        AlertDialog(
                            onDismissRequest = onEnableConfirmationDismissed,
                            title = { Text("Включить Обход?") },
                            text = if (enableConfirmationCached) {
                                null
                            } else {
                                { Text("Выбор стратегии может занять 1-5 минут.") }
                            },
                            dismissButton = {
                                TextButton(onClick = onEnableConfirmationDismissed) {
                                    Text("Нет")
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = onEnableConfirmationAccepted) {
                                    Text("Включить")
                                }
                            }
                        )
                    }
                    if (availableUpdate != null &&
                        !showHelp &&
                        !showProxyDialog &&
                        !showEnableConfirmation
                    ) {
                        UpdatePromptDialog(
                            update = availableUpdate,
                            onRemindLater = { onRemindUpdateLater(availableUpdate) },
                            onDownload = { onDownloadUpdate(availableUpdate) },
                            onOpenGitHub = { onOpenUpdateGitHub(availableUpdate) }
                        )
                    }
                }
            }
        }
    }
}
