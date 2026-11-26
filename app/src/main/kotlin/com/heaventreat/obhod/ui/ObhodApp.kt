@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.heaventreat.obhod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.StatusBarManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.heaventreat.obhod.logging.AppLog
import com.heaventreat.obhod.diagnostics.TransferTestConfig
import com.heaventreat.obhod.R
import com.heaventreat.obhod.notification.ObhodTileService
import com.heaventreat.obhod.proxy.TelegramProxyConfig
import com.heaventreat.obhod.strategy.ByeByeDpiManager
import com.heaventreat.obhod.strategy.StrategyProfileOption
import com.heaventreat.obhod.strategy.StrategyProfiles
import com.heaventreat.obhod.update.AppUpdate
import com.heaventreat.obhod.update.UpdateDownloadBanner
import com.heaventreat.obhod.update.UpdateDownloadProgress
import com.heaventreat.obhod.update.UpdatePromptDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private enum class ObhodTab(val label: String, val icon: Int) {
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

@Composable
private fun TacticHelpDialog(onAccepted: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var secondsRemaining by rememberSaveable { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        repeat(5) {
            delay(1_000)
            secondsRemaining -= 1
        }
    }

    val accepting = page == 1
    val body = if (accepting) {
        buildAnnotatedString {
            append("Обычный режим использует VPN и автоматически проверяет несколько тактик ByeByeDPI на текущей сети. ")
            append("Стратегия, показавшая рабочие HTTPS-ответы, сохраняется для этой сети на 12 часов.")
        }
    } else {
        buildAnnotatedString {
            append("Zapret Root управляет модулем nZAPRET, установленным через KernelSU Manager. ")
            append("Модуль применяет правила к IPv4 и IPv6; если модуль не установлен, приложение покажет ссылку на проект.")
        }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("Справка по тактикам") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${page + 1} / 2",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = page > 0,
                    onClick = { page -= 1 },
                    modifier = Modifier.semantics {
                        contentDescription = "Предыдущая страница"
                    }
                ) {
                    Text("←", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(
                    enabled = page < 1,
                    onClick = { page += 1 },
                    modifier = Modifier.semantics {
                        contentDescription = "Следующая страница"
                    }
                ) {
                    Text("→", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = accepting && secondsRemaining == 0,
                    onClick = onAccepted
                ) {
                    Text(
                        if (secondsRemaining > 0) {
                            "Принять (${secondsRemaining} с)"
                        } else {
                            "Принять"
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun ObhodNavigationBar(selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            ObhodTab.entries.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    icon = {
                        Icon(
                            painter = painterResource(tab.icon),
                            contentDescription = tab.label
                        )
                    },
                    label = { Text(tab.label) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun ObhodNavigationRail(selected: Int, onSelect: (Int) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        ObhodTab.entries.forEachIndexed { index, tab ->
            NavigationRailItem(
                selected = index == selected,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        painter = painterResource(tab.icon),
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                alwaysShowLabel = true
            )
        }
    }
}

private object Spacing {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

@Composable
private fun HomeScreen(
    vpnRunning: Boolean,
    starting: Boolean,
    selectedStrategyIndex: Int,
    onStrategySelected: (Int) -> Unit,
    batteryOptimizationIgnored: Boolean,
    onRequestBatteryOptimization: () -> Unit,
    autoSelectionStatus: String,
    autoSelectionProgress: ByeByeDpiManager.Progress?,
    byeByeDpiFullAnalysis: Boolean,
    onByeByeDpiFullAnalysisChanged: (Boolean) -> Unit,
    onRunAutoSelection: () -> Unit,
    onToggle: () -> Unit,
    onOpenProxy: () -> Unit
) {
    val selected = StrategyProfiles.options.getOrNull(selectedStrategyIndex)
        ?: StrategyProfiles.options.first()
    val kernelMode = selected.id == StrategyProfiles.ROOT_ID
    val autoMode = selected.id == StrategyProfiles.BYEBYEDPI_ID
    val controlsEnabled = !vpnRunning && !starting

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 840.dp
        val horizontalMargin = if (maxWidth >= 600.dp) Spacing.lg else Spacing.md

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1040.dp)
                    .padding(horizontal = horizontalMargin, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                HomeHeader()

                if (twoPane) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Column(
                            modifier = Modifier.weight(1.15f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            ConnectionHero(
                                vpnRunning = vpnRunning,
                                starting = starting,
                                strategyTitle = selected.title,
                                kernelMode = kernelMode,
                                autoMode = autoMode,
                                onToggle = onToggle,
                                onOpenProxy = onOpenProxy
                            )
                            StrategySelectionCard(
                                kernelMode = kernelMode,
                                autoMode = autoMode,
                                starting = starting,
                                status = autoSelectionStatus,
                                progress = autoSelectionProgress,
                                fullAnalysis = byeByeDpiFullAnalysis,
                                onFullAnalysisChanged = onByeByeDpiFullAnalysisChanged,
                                onRunAutoSelection = onRunAutoSelection
                            )
                            StrategySection(
                                selected = selected,
                                selectedIndex = selectedStrategyIndex,
                                enabled = controlsEnabled,
                                onSelected = onStrategySelected
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                        ) {
                            SettingsSection(
                                batteryOptimizationIgnored = batteryOptimizationIgnored,
                                onRequestBatteryOptimization = onRequestBatteryOptimization
                            )
                        }
                    }
                } else {
                    ConnectionHero(
                        vpnRunning = vpnRunning,
                        starting = starting,
                        strategyTitle = selected.title,
                        kernelMode = kernelMode,
                        autoMode = autoMode,
                        onToggle = onToggle,
                        onOpenProxy = onOpenProxy
                    )
                    StrategySelectionCard(
                        kernelMode = kernelMode,
                        autoMode = autoMode,
                        starting = starting,
                        status = autoSelectionStatus,
                        progress = autoSelectionProgress,
                        fullAnalysis = byeByeDpiFullAnalysis,
                        onFullAnalysisChanged = onByeByeDpiFullAnalysisChanged,
                        onRunAutoSelection = onRunAutoSelection
                    )
                    StrategySection(
                        selected = selected,
                        selectedIndex = selectedStrategyIndex,
                        enabled = controlsEnabled,
                        onSelected = onStrategySelected
                    )
                    SettingsSection(
                        batteryOptimizationIgnored = batteryOptimizationIgnored,
                        onRequestBatteryOptimization = onRequestBatteryOptimization
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Text(
        text = "Обход",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ConnectionHero(
    vpnRunning: Boolean,
    starting: Boolean,
    strategyTitle: String,
    kernelMode: Boolean,
    autoMode: Boolean,
    onToggle: () -> Unit,
    onOpenProxy: () -> Unit
) {
    val active = vpnRunning && !starting
    val title = when {
        starting -> if (autoMode) "Подбор стратегии…" else "Подготовка…"
        vpnRunning && kernelMode -> "Zapret Root включён"
        vpnRunning -> "Обход включён"
        else -> "Обход выключен"
    }
    val subtitle = when {
        starting -> when {
            kernelMode -> "Проверяем root и применяем правила…"
            autoMode -> "Подбираем стратегию и запускаем туннель…"
            else -> "Запускаем системный обход…"
        }
        vpnRunning && kernelMode -> "Root редирект · $strategyTitle"
        vpnRunning -> "Системный обход · $strategyTitle"
        else -> "Профиль: $strategyTitle"
    }

    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (active) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconContainer = if (active) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (starting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_shield),
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (vpnRunning && !starting) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stop),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Выключить обход", style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Button(
                    onClick = onToggle,
                    enabled = !starting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = CircleShape
                ) {
                    if (starting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Запуск…", style = MaterialTheme.typography.titleSmall)
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Включить обход", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            if (!vpnRunning && !starting && kernelMode) {
                Text(
                    text = "Для root-режима нужен доступ root.",
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor
                )
            }

            TextButton(
                onClick = onOpenProxy,
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text("Данные MTProxy")
            }

        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StrategySelectionCard(
    kernelMode: Boolean,
    autoMode: Boolean,
    starting: Boolean,
    status: String,
    progress: ByeByeDpiManager.Progress?,
    fullAnalysis: Boolean,
    onFullAnalysisChanged: (Boolean) -> Unit,
    onRunAutoSelection: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val statusText = status.takeIf { it.isNotBlank() && !starting && progress?.isRunning != true }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (kernelMode) "Настройка nZAPRET" else "Подбор стратегии",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (kernelMode) {
                            "Перезапуск правил обхода для текущей сети."
                        } else {
                            "Проверка ByeByeDPI для текущей сети."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                if (progress?.isRunning == true) {
                    Surface(
                        shape = CircleShape,
                        color = colors.primaryContainer
                    ) {
                        Text(
                            text = "ИДЁТ",
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimaryContainer
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = progress != null,
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = colors.surfaceContainerHigh
                ) {
                    progress?.let {
                        AutoSelectionTaskbar(
                            progress = it,
                            onPrimaryBackground = false,
                            modifier = Modifier.padding(Spacing.md)
                        )
                    }
                }
            }

            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (autoMode) {
                HorizontalDivider(color = colors.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = fullAnalysis,
                            enabled = !starting,
                            role = Role.Switch,
                            onValueChange = onFullAnalysisChanged
                        )
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Полный анализ",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Перебор всех стратегий в поиске лучшей из всех. Занимает больше времени перебора.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = fullAnalysis,
                        onCheckedChange = null,
                        enabled = !starting
                    )
                }
            }

            FilledTonalButton(
                onClick = onRunAutoSelection,
                enabled = !starting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                if (starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = when {
                        starting && autoMode -> "Подбираем стратегию…"
                        starting -> "Настройка…"
                        kernelMode -> "Настроить nZAPRET"
                        else -> "Подобрать стратегию заново"
                    }
                )
            }
        }
    }
}

@Composable
private fun AutoSelectionTaskbar(
    progress: ByeByeDpiManager.Progress,
    onPrimaryBackground: Boolean,
    modifier: Modifier = Modifier
) {
    val total = progress.total
    if (total == 0) return

    val animatedProgress by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f),
        label = "strategy-test-progress"
    )
    val colors = MaterialTheme.colorScheme
    val summaryColor = when {
        onPrimaryBackground -> colors.onPrimary
        progress.isRunning -> colors.onSurfaceVariant
        progress.successful > 0 && progress.failed == 0 -> colors.tertiary
        progress.failed > 0 && progress.successful == 0 -> colors.error
        else -> colors.onSurfaceVariant
    }
    val summary = buildString {
        append("${progress.checked}/$total · ${progress.successPercent}% успех")
        if (progress.checked > 0) append(" · ${progress.failurePercent}% сбои")
        progress.currentCandidate?.let { append(" · $it") }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$summary · ${(progress.fraction * 100).toInt()}% завершено"
                },
            color = if (onPrimaryBackground) colors.onPrimary else colors.primary,
            trackColor = if (onPrimaryBackground) {
                colors.onPrimary.copy(alpha = 0.18f)
            } else {
                colors.surfaceContainerHighest
            }
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = summaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHeader(title: String, supporting: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StrategySection(
    selected: StrategyProfileOption,
    selectedIndex: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(
            title = "Стратегия"
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selected.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (enabled) {
                                "Текущий профиль"
                            } else {
                                "Смена профиля доступна после выключения обхода"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showSheet = true },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (enabled) "Выбрать" else "Активна")
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Spacing.md),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = selected.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "Стратегия обхода",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Выберите профиль для текущей сети",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                StrategyProfiles.options.forEachIndexed { index, option ->
                    ListItem(
                        headlineContent = { Text(option.title) },
                        supportingContent = {
                            Text(
                                text = option.description,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            RadioButton(
                                selected = index == selectedIndex,
                                onClick = null,
                                enabled = enabled
                            )
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .selectable(
                                selected = index == selectedIndex,
                                enabled = enabled,
                                role = Role.RadioButton
                            ) {
                                onSelected(index)
                                scope.launch {
                                    sheetState.hide()
                                    showSheet = false
                                }
                            }
                    )
                }
                if (!enabled) {
                    Text(
                        text = "Стратегию можно сменить после выключения обхода",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    description: String,
    footer: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                action?.invoke()
            }
            footer?.invoke()
        }
    }
}

@Composable
private fun SettingsSection(
    batteryOptimizationIgnored: Boolean,
    onRequestBatteryOptimization: () -> Unit
) {
    var showAutostartHelp by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(title = "Дополнительно")
        SettingsCard(
            icon = Icons.Filled.BatteryFull,
            title = "Работа при выключенном экране",
            description = if (batteryOptimizationIgnored) "Ограничение батареи снято"
                else "Система может остановить обход в фоне. Снимите ограничение батареи, чтобы он работал постоянно.",
            footer = {
                if (batteryOptimizationIgnored) {
                    Text("Готово", color = MaterialTheme.colorScheme.primary)
                } else {
                    FilledTonalButton(
                        onClick = onRequestBatteryOptimization,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Снять ограничение", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        )
        SettingsCard(
            icon = Icons.Filled.RestartAlt,
            title = "Автозапуск после перезагрузки",
            description = "Покажем, где разрешить Obhod запускаться самому",
            footer = {
                FilledTonalButton(onClick = { showAutostartHelp = true }, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Инструкция", style = MaterialTheme.typography.labelMedium)
                }
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SettingsCard(icon = Icons.Filled.Dashboard, title = "Плитка в шторке",
                description = "Кнопка Obhod в быстрых настройках: включать обход одним касанием") {
                QuickSettingsTileButton()
            }
        }
    }
    if (showAutostartHelp) {
        AlertDialog(onDismissRequest = { showAutostartHelp = false }, title = { Text("Как разрешить автозапуск") },
            text = { Text("1. Откройте Настройки - Приложения - Obhod.\n\n" +
                "2. Включите Автозапуск, Фоновая работа или Без ограничений - название и расположение зависит от прошивки.\n\n" +
                "3. На Xiaomi и Huawei дополнительно проверьте раздел Безопасность или Управление приложениями и разрешите запуск после перезагрузки.") },
            confirmButton = { TextButton(onClick = { showAutostartHelp = false }) { Text("Понятно") } })
    }
}
@Composable
private fun QuickSettingsTileButton() {
    val context = LocalContext.current

    Surface(
        onClick = {
            runCatching {
                context.getSystemService(StatusBarManager::class.java)
                    ?.requestAddTileService(
                        ComponentName(context, ObhodTileService::class.java),
                        "Обход",
                        android.graphics.drawable.Icon.createWithResource(
                            context,
                            R.drawable.ic_shield
                        ),
                        context.mainExecutor
                    ) { _ -> }
            }.onFailure {
                Toast.makeText(
                    context,
                    "Добавьте плитку \"Обход\" вручную в настройках шторки",
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(width = 72.dp, height = 56.dp)
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Обход",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

// logs
@Composable
private fun LogsScreen(
    onCopyText: (String) -> Unit,
    onExportDiagnosticReport: (String) -> Unit,
    vpnRunning: Boolean,
    transferTestRunning: Boolean,
    transferTestStatus: String,
    onRunTransferTest: (TransferTestConfig) -> Unit
) {
    val entries by AppLog.entries.collectAsState()
    val newestFirst = entries.asReversed()
    var confirmDiagnosticExport by rememberSaveable { mutableStateOf(false) }
    var confirmClearLogs by rememberSaveable { mutableStateOf(false) }
    var operatorRegion by rememberSaveable { mutableStateOf("") }
    var configureTransferTest by rememberSaveable { mutableStateOf(false) }
    var payloadKiB by rememberSaveable { mutableIntStateOf(150) }
    var idleTimeoutSeconds by rememberSaveable { mutableIntStateOf(4) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Логи",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Записей: ${entries.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = { onCopyText(entries.joinToString("\n") { it.asLine() }) },
                enabled = entries.isNotEmpty()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = "Скопировать все логи"
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = { confirmClearLogs = true },
                enabled = entries.isNotEmpty()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "Очистить логи"
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { confirmDiagnosticExport = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить диагностический отчёт")
        }
        Text(
            text = "Отчёт не отправляется автоматически",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { configureTransferTest = true },
            enabled = vpnRunning && !transferTestRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (transferTestRunning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (transferTestRunning) "Тест передачи выполняется…"
                else "Проверить HTTPS-передачу (3 прогона)"
            )
        }
        Text(
            text = when {
                transferTestRunning -> transferTestStatus
                !vpnRunning -> "Для проверки выбранных стратегий сначала включите VPN."
                transferTestStatus.isNotBlank() -> transferTestStatus
                else -> "Три прогона четырёх сервисов; максимум около 2.4 MiB данных."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            EmptyLogs()
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(newestFirst, key = { it.id }, contentType = { "log" }) { entry ->
                    LogCard(entry = entry, onClick = { onCopyText(entry.asLine()) })
                }
            }
        }
        if (confirmClearLogs) {
            AlertDialog(
                onDismissRequest = { confirmClearLogs = false },
                title = { Text("Очистить логи?") },
                text = { Text("Все записи будут удалены.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            AppLog.clear()
                            confirmClearLogs = false
                        }
                    ) {
                        Text("Очистить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearLogs = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
        if (confirmDiagnosticExport) {
            AlertDialog(
                onDismissRequest = {
                    confirmDiagnosticExport = false
                    operatorRegion = ""
                },
                title = { Text("Сохранить отчёт?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = buildAnnotatedString {
                                append(
                                    "В отчет войдут версия приложения и устройства, тип сети, " +
                                        "результаты калибровки, статистика подключений и журнал приложения. " +
                                        "Статистика включает профили, длительность сеансов, смены сети, " +
                                        "состояния ядра и классифицированные сетевые ошибки. Журнал может " +
                                        "содержать домены, IP-адреса и параметры соединений.\n\n" +
                                        "При указании оператора / региона, они также войдут в отчет. " +
                                        "Важно понимать, что указывать такую информацию для других " +
                                        "не рекомендуется, как и отправлять кому-то отчет вне нужды. " +
                                        "Логи потребовать может "
                                )
                                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                append("лишь разработчик ")
                                withLink(
                                    LinkAnnotation.Url(
                                        url = "https://t.me/skytr1x",
                                        styles = TextLinkStyles(
                                            style = SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                textDecoration = TextDecoration.Underline
                                            )
                                        )
                                    )
                                ) {
                                    append("skytr1x")
                                }
                                pop()
                                append(" и лишь с ")
                                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                append("вашего согласия")
                                pop()
                                append(" для внесения правок.")
                            }
                        )
                        OutlinedTextField(
                            value = operatorRegion,
                            onValueChange = { value ->
                                operatorRegion = value.filterNot(Char::isISOControl).take(80)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Оператор / регион (необязательно)") },
                            supportingText = { Text("Заполняется вручную; геолокация не используется.") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDiagnosticExport = false
                            onExportDiagnosticReport(operatorRegion.trim())
                            operatorRegion = ""
                        }
                    ) {
                        Text("Выбрать место")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            confirmDiagnosticExport = false
                            operatorRegion = ""
                        }
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }
        if (configureTransferTest) {
            AlertDialog(
                onDismissRequest = { configureTransferTest = false },
                title = { Text("Проверка передачи данных") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Тест трижды проверит HTTPS через DoH-адресацию и SOCKS для YouTube, " +
                                "Discord, 4pda и Cloudflare. На попытку отводится до 15 секунд; " +
                                "за один ответ будет прочитано не больше выбранного объёма. Любой " +
                                "полученный HTTP-статус считается достижением сервера, даже если " +
                                "это редирект или отказ. Запросы не содержат учётных данных; " +
                                "результаты останутся в локальных логах."
                        )
                        TransferTestOption(
                            label = "Лимит данных на сервис",
                            value = payloadKiB,
                            suffix = "KiB",
                            options = TransferTestConfig.PAYLOAD_OPTIONS_KIB,
                            onSelected = { payloadKiB = it }
                        )
                        Text("До 15 секунд на попытку; полный тест может занять до 3 минут.")
                        TransferTestOption(
                            label = "Таймаут без новых данных",
                            value = idleTimeoutSeconds,
                            suffix = "с",
                            options = TransferTestConfig.IDLE_TIMEOUT_OPTIONS_SECONDS,
                            onSelected = { idleTimeoutSeconds = it }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            configureTransferTest = false
                            onRunTransferTest(
                                TransferTestConfig(
                                    payloadKiB = payloadKiB,
                                    idleTimeoutSeconds = idleTimeoutSeconds
                                )
                            )
                        }
                    ) {
                        Text("Начать тест")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { configureTransferTest = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}

@Composable
private fun TransferTestOption(
    label: String,
    value: Int,
    suffix: String,
    options: List<Int>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = "$value $suffix",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable
                )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text("$option $suffix") },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LogCard(entry: AppLog.Entry, onClick: () -> Unit) {
    val logColor = when (entry.level) {
        'W', 'E' -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.time,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (entry.level != 'I') {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.level.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = logColor
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = logColor,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyLogs() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_audit),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Пока пусто", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Здесь появятся логи обхода",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// sources
@Composable
private fun SourceScreen() {
    val uriHandler = LocalUriHandler.current
    val repoUrl = "https://github.com/heavenTreatment/Obhod"
    val licenseUrl = "https://github.com/heavenTreatment/Obhod/blob/main/LICENSE"
    val byeByeDpiUrl = "https://github.com/romanvht/ByeByeDPI"
    val tgProxyUrl = "https://github.com/amurcanov/tg-ws-proxy-android"
    val nZapretUrl = "https://github.com/nikitos4683/nzapret"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Surface(
            onClick = { uriHandler.openUri(repoUrl) },
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = "Открыть GitHub",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Obhod",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = { uriHandler.openUri(licenseUrl) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = "GNU GPL-3.0-or-later",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Copyright © 2026 heavenTreatment · без гарантий",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(
            onClick = { uriHandler.openUri(repoUrl) },
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Репозиторий")
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.widthIn(max = 640.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                text = buildAnnotatedString {
                    val linkStyle = TextLinkStyles(
                        style = SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append("Все способы обхода принадлежат не мне. Все ресурсы обхода принадлежат ")
                    withLink(LinkAnnotation.Url(byeByeDpiUrl, linkStyle)) {
                        append("ByeByeDPI")
                    }
                    append(", большая часть обхода Телеграм пренадлежит ")
                    withLink(LinkAnnotation.Url(tgProxyUrl, linkStyle)) {
                        append("tg-ws-proxy-android")
                    }
                    append(" (архивировано) и ")
                    withLink(LinkAnnotation.Url(nZapretUrl, linkStyle)) {
                        append("nZapret")
                    }
                    append(" для root обхода.")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ProxyDialog(onDismiss: () -> Unit, onCopyText: (String) -> Unit) {
    val context = LocalContext.current
    val secret = remember(context) { TelegramProxyConfig.getOrCreateSecret(context) }
    val connection = "${TelegramProxyConfig.HOST}:${TelegramProxyConfig.PORT} — $secret"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = null
            )
        },
        title = { Text("MTProto для Telegram") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ProxyValue("Сервер", TelegramProxyConfig.HOST)
                ProxyValue("Порт", TelegramProxyConfig.PORT.toString())
                ProxyValue("Секрет", secret)
                FilledTonalButton(
                    onClick = {
                        onCopyText(connection)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Скопировать")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(TelegramProxyConfig.getTelegramLink(context))
                            )
                        )
                    }.onFailure {
                        Toast.makeText(context, "Не удалось открыть Телеграм", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            ) {
                Text("Открыть в Телеграм")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@Composable
private fun ProxyValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace
        )
    }
}
