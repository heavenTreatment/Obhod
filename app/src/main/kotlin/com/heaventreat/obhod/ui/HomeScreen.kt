@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.heaventreat.obhod.ui

import android.content.ComponentName
import android.app.StatusBarManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heaventreat.obhod.R
import com.heaventreat.obhod.notification.ObhodTileService
import com.heaventreat.obhod.strategy.ByeByeDpiManager
import com.heaventreat.obhod.strategy.StrategyProfileOption
import com.heaventreat.obhod.strategy.StrategyProfiles
import kotlinx.coroutines.launch

private object Spacing {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

@Composable
internal fun HomeScreen(
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
