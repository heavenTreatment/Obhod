package com.heaventreat.obhod.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpdateDownloadBanner(
    progress: UpdateDownloadProgress,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = colors.primaryContainer
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = when (progress.stage) {
                                UpdateDownloadStage.DOWNLOADING -> "Загружается обновление"
                                UpdateDownloadStage.INSTALLING -> "Подготовка установки"
                                UpdateDownloadStage.FAILED -> "Не удалось загрузить обновление"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = progress.message ?: when (progress.stage) {
                                UpdateDownloadStage.DOWNLOADING -> "${progress.percent}%"
                                UpdateDownloadStage.INSTALLING -> "Сейчас откроется установщик Android"
                                UpdateDownloadStage.FAILED -> "Попробуйте скачать APK с GitHub"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progress.stage == UpdateDownloadStage.FAILED) {
                                colors.error
                            } else {
                                colors.onSurfaceVariant
                            }
                        )
                    }
                }

                when (progress.stage) {
                    UpdateDownloadStage.DOWNLOADING -> LinearWavyProgressIndicator(
                        progress = { progress.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    UpdateDownloadStage.INSTALLING -> LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    UpdateDownloadStage.FAILED -> Unit
                }
            }
            if (progress.stage != UpdateDownloadStage.INSTALLING) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Закрыть статус загрузки"
                    )
                }
            }
        }
    }
}
