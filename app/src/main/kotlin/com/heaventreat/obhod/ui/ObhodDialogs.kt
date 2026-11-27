@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.heaventreat.obhod.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.heaventreat.obhod.R
import com.heaventreat.obhod.proxy.TelegramProxyConfig
import kotlinx.coroutines.delay

@Composable
internal fun TacticHelpDialog(onAccepted: () -> Unit) {
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
            append("Zapret Root управляет модулем nZAPRET. ")
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
internal fun ProxyDialog(onDismiss: () -> Unit, onCopyText: (String) -> Unit) {
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
