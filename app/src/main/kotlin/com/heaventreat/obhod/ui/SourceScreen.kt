@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.heaventreat.obhod.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.heaventreat.obhod.R

@Composable
internal fun SourceScreen() {
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
