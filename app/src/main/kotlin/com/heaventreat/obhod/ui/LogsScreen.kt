@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.heaventreat.obhod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.heaventreat.obhod.logging.AppLog
import com.heaventreat.obhod.diagnostics.TransferTestConfig
import com.heaventreat.obhod.R

@Composable
internal fun LogsScreen(
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
            Text("Сохранить логи и диагностику")
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
                title = { Text("Сохранить отчет?") },
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
