package com.heaventreat.obhod.diagnostics

import android.os.SystemClock
import com.heaventreat.obhod.logging.AppLog

internal data class ConnectionSessionReport(
    val requestedAt: String,
    val profile: String,
    val startedAt: String?,
    val stoppedAt: String?,
    val startupDurationMs: Long?,
    val durationMs: Long?,
    val outcome: String
)

internal data class ConnectionHistorySummary(
    val currentState: String,
    val bufferEntryCount: Int,
    val startRequests: Int,
    val serviceStartCalls: Int,
    val reachedVpnRunning: Int,
    val startFailures: Int,
    val stopRequests: Int,
    val completedStops: Int,
    val networkRestarts: Int,
    val warningCount: Int,
    val errorCount: Int,
    val startupP50Ms: Long?,
    val startupP95Ms: Long?,
    val durationP50Ms: Long?,
    val durationP95Ms: Long?,
    val sessions: List<ConnectionSessionReport>,
    val recentIssues: List<String>
)

internal data class DiagnosticReportData(
    val generatedAt: String,
    val appVersion: String,
    val androidVersion: String,
    val device: String,
    val networkClass: String,
    val networkValidated: Boolean?,
    val networkMetered: Boolean?,
    val privateDnsActive: Boolean?,
    val ipv6Only: Boolean?,
    val calibrationSignal: String,
    val dnsComparison: String,
    val selectedProfile: String,
    val operatorRegion: String,
    val selfTest: TransferTestSummary?,
    val logs: List<String> = emptyList(),
    val connectionEntries: List<AppLog.Entry> = emptyList(),
    val vpnRunning: Boolean = false,
    val vpnStarting: Boolean = false
)

internal object DiagnosticReport {
    private val selfTestStartPattern = Regex(
        """^\[SELFTEST_RUN] repetitions=(\d+) payload_kib=(\d+) host_timeout_s=(\d+) idle_timeout_s=(\d+)$"""
    )
    private val selfTestResultPattern = Regex(
        """^\[SELFTEST] service=(youtube|discord|4pda|cloudflare) attempt=(\d+) outcome=([a-z0-9_]+) bytes=(\d+) elapsed_ms=(\d+) socks_ms=(\d+|-) doh_ms=(\d+|-) tls_ms=(\d+|-) transfer_ms=(\d+|-) http=(\d+|-)$"""
    )

    fun connectionHistory(
        entries: List<AppLog.Entry>,
        vpnRunning: Boolean,
        vpnStarting: Boolean
    ): ConnectionHistorySummary {
        class MutableSession(
            var requestedAt: String,
            var requestedElapsedMs: Long,
            var profile: String
        ) {
            var startedAt: String? = null
            var startedElapsedMs: Long? = null
            var stoppedAt: String? = null
            var stoppedElapsedMs: Long? = null
            var stopRequestedByUi = false
            var startFailed = false
            var closed = false
        }

        val sessions = mutableListOf<MutableSession>()
        var current: MutableSession? = null
        var startRequests = 0
        var serviceStartCalls = 0
        var reachedVpnRunning = 0
        var startFailures = 0
        var stopRequests = 0
        var completedStops = 0
        var networkRestarts = 0

        fun beginSession(time: String, elapsedMs: Long, profile: String): MutableSession {
            val session = MutableSession(time, elapsedMs, profile)
            sessions += session
            current = session
            return session
        }

        entries.forEach { entry ->
            val message = entry.message
            when {
                entry.tag == "UI" && message.startsWith("Запуск обхода: ") -> {
                    startRequests++
                    if (current == null || current.closed) {
                        beginSession(
                            entry.time,
                            entry.elapsedRealtimeMs,
                            message.removePrefix("Запуск обхода: ")
                        )
                    }
                }

                message.startsWith("Запуск: ") -> {
                    serviceStartCalls++
                    val profile = message.removePrefix("Запуск: ")
                    val session = current?.takeUnless { it.closed }
                        ?: beginSession(entry.time, entry.elapsedRealtimeMs, profile)
                    session.profile = profile
                }

                entry.tag == "UI" && message == "Остановка обхода" -> {
                    stopRequests++
                    current?.takeUnless { it.closed }?.stopRequestedByUi = true
                }

                message.startsWith("Системный обход включён: ") -> {
                    reachedVpnRunning++
                    val profile = message.removePrefix("Системный обход включён: ")
                    val session = current?.takeUnless { it.closed }
                        ?: beginSession(entry.time, entry.elapsedRealtimeMs, profile)
                    session.profile = profile
                    session.startedAt = entry.time
                    session.startedElapsedMs = entry.elapsedRealtimeMs
                }

                message.startsWith("Не удалось запустить системный обход") -> {
                    startFailures++
                    val session = current?.takeUnless { it.closed }
                        ?: beginSession(entry.time, entry.elapsedRealtimeMs, "неизвестно")
                    session.startFailed = true
                    session.closed = true
                    current = null
                }

                message == "Системный обход выключен" -> {
                    completedStops++
                    current?.takeUnless { it.closed }?.let { session ->
                        session.stoppedAt = entry.time
                        session.stoppedElapsedMs = entry.elapsedRealtimeMs
                        session.closed = true
                    }
                    current = null
                }

                message.startsWith("Сеть изменилась;") -> networkRestarts++
            }
        }

        val startupDurations = sessions.mapNotNull { session ->
            val started = session.startedElapsedMs ?: return@mapNotNull null
            (started - session.requestedElapsedMs).takeIf { it >= 0L }
        }
        val completedDurations = sessions.mapNotNull { session ->
            val started = session.startedElapsedMs ?: return@mapNotNull null
            val stopped = session.stoppedElapsedMs ?: return@mapNotNull null
            (stopped - started).takeIf { it >= 0L }
        }
        val now = SystemClock.elapsedRealtime()
        val sessionReports = sessions.map { session ->
            val isCurrent = session === current && !session.closed
            val activeDuration = if (
                isCurrent && vpnRunning && session.startedElapsedMs != null
            ) {
                (now - session.startedElapsedMs!!).coerceAtLeast(0L)
            } else {
                null
            }
            val stoppedDuration = if (
                session.startedElapsedMs != null && session.stoppedElapsedMs != null
            ) {
                (session.stoppedElapsedMs!! - session.startedElapsedMs!!)
                    .takeIf { it >= 0L }
            } else {
                null
            }
            val outcome = when {
                session.startFailed -> "ошибка запуска"
                session.stoppedAt != null && session.stopRequestedByUi -> "остановлен по команде"
                session.stoppedAt != null -> "остановлен"
                isCurrent && vpnStarting -> "запускается"
                isCurrent && vpnRunning -> "активен"
                else -> "нет завершения в буфере"
            }
            ConnectionSessionReport(
                requestedAt = session.requestedAt,
                profile = session.profile,
                startedAt = session.startedAt,
                stoppedAt = session.stoppedAt,
                startupDurationMs = session.startedElapsedMs
                    ?.minus(session.requestedElapsedMs)
                    ?.takeIf { it >= 0L },
                durationMs = stoppedDuration ?: activeDuration,
                outcome = outcome
            )
        }

        return ConnectionHistorySummary(
            currentState = when {
                vpnStarting -> "запускается"
                vpnRunning -> "активен"
                else -> "выключен"
            },
            bufferEntryCount = entries.size,
            startRequests = startRequests,
            serviceStartCalls = serviceStartCalls,
            reachedVpnRunning = reachedVpnRunning,
            startFailures = startFailures,
            stopRequests = stopRequests,
            completedStops = completedStops,
            networkRestarts = networkRestarts,
            warningCount = entries.count { it.level == 'W' },
            errorCount = entries.count { it.level == 'E' },
            startupP50Ms = percentile(startupDurations, 50),
            startupP95Ms = percentile(startupDurations, 95),
            durationP50Ms = percentile(completedDurations, 50),
            durationP95Ms = percentile(completedDurations, 95),
            sessions = sessionReports,
            recentIssues = entries
                .filter { it.level == 'W' || it.level == 'E' }
                .takeLast(10)
                .map { "${it.time} ${it.level}/${it.tag}: ${it.message}" }
        )
    }

    private fun percentile(values: List<Long>, percent: Int): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = ((percent / 100.0) * sorted.size).toInt().coerceAtLeast(1)
        return sorted[rank.coerceAtMost(sorted.size) - 1]
    }

    fun latestSelfTest(messages: List<String>): TransferTestSummary? {
        val startIndex = messages.indexOfLast { selfTestStartPattern.matches(it) }
        if (startIndex < 0) return null
        val configMatch = selfTestStartPattern.matchEntire(messages[startIndex]) ?: return null
        val repetitions = configMatch.groupValues[1].toIntOrNull() ?: return null
        if (repetitions != ConnectivitySelfTest.REPETITIONS) return null
        val config = TransferTestConfig(
            payloadKiB = configMatch.groupValues[2].toIntOrNull() ?: return null,
            idleTimeoutSeconds = configMatch.groupValues[4].toIntOrNull() ?: return null
        )
        if (configMatch.groupValues[3].toIntOrNull() != config.hostTimeoutSeconds) return null
        val results = messages.drop(startIndex + 1).mapNotNull(::parseSelfTestResult)
        return TransferTestSummary(config, repetitions, results)
    }

    fun format(data: DiagnosticReportData): String = buildString {
        appendLine("Обход - диагностический отчёт")
        appendLine("Создан: ${data.generatedAt.reportSafe()}")
        appendLine("Версия приложения: ${data.appVersion.reportSafe()}")
        appendLine("Android: ${data.androidVersion.reportSafe()}")
        appendLine("Устройство: ${data.device.reportSafe()}")
        appendLine("Сеть: ${data.networkClass.reportSafe()}")
        appendLine("Сеть подтверждена Android: ${data.networkValidated.asText()}")
        appendLine("Сеть тарифицируется: ${data.networkMetered.asText()}")
        appendLine("Private DNS активен: ${data.privateDnsActive.asText()}")
        appendLine("IPv6-only маршрут: ${data.ipv6Only.asText()}")
        appendLine("Калибровка TLS: ${data.calibrationSignal.reportSafe()}")
        appendLine("Сравнение DNS: ${data.dnsComparison.reportSafe()}")
        appendLine("Интерпретация DNS: ${dnsInterpretation(data.dnsComparison)}")
        appendLine("Выбранный профиль: ${data.selectedProfile.reportSafe()}")

        val connectionHistory = connectionHistory(
            entries = data.connectionEntries,
            vpnRunning = data.vpnRunning,
            vpnStarting = data.vpnStarting
        )
        appendLine("Текущее состояние VPN: ${connectionHistory.currentState}")
        appendLine(
            "История подключений: ${connectionHistory.bufferEntryCount}/${AppLog.MAX_ENTRIES} " +
                "последних записей в памяти"
        )
        appendLine(
            "Запуски из интерфейса: ${connectionHistory.startRequests}; " +
                "вызовы сервиса: ${connectionHistory.serviceStartCalls}; " +
                "достигли состояния VPN active: ${connectionHistory.reachedVpnRunning}; " +
                "ошибки старта: ${connectionHistory.startFailures}"
        )
        appendLine(
            "Остановки: запросов ${connectionHistory.stopRequests}; " +
                "подтверждено журналом ${connectionHistory.completedStops}"
        )
        appendLine(
            "Время запуска до VPN active: p50 " +
                "${connectionHistory.startupP50Ms?.let { "$it ms" } ?: "нет данных"}, p95 " +
                "${connectionHistory.startupP95Ms?.let { "$it ms" } ?: "нет данных"}"
        )
        appendLine(
            "Длительность завершённых VPN-сессий: p50 " +
                "${connectionHistory.durationP50Ms?.let { "$it ms" } ?: "нет данных"}, p95 " +
                "${connectionHistory.durationP95Ms?.let { "$it ms" } ?: "нет данных"}"
        )
        appendLine(
            "Сетевые перенастройки: перезапуски из-за сети " +
                "${connectionHistory.networkRestarts}"
        )
        appendLine(
            "Предупреждения: ${connectionHistory.warningCount}; " +
                "ошибки приложения: ${connectionHistory.errorCount}"
        )
        if (connectionHistory.sessions.isEmpty()) {
            appendLine("Сеансы VPN: нет записей в текущем буфере")
        } else {
            appendLine("Последние сеансы VPN:")
            connectionHistory.sessions.takeLast(8).forEach { session ->
                val activation = session.startedAt ?: "не дошёл до active"
                val stopped = session.stoppedAt?.let { " → $it" }.orEmpty()
                val startup = session.startupDurationMs?.let { ", запуск ${it} ms" }.orEmpty()
                val duration = session.durationMs?.let { ", ${it} ms" }.orEmpty()
                appendLine(
                    "  ${session.requestedAt} ${session.profile.reportSafe()}: " +
                        "$activation$stopped$startup$duration; ${session.outcome}"
                )
            }
        }
        if (connectionHistory.recentIssues.isNotEmpty()) {
            appendLine("Последние предупреждения и ошибки приложения:")
            connectionHistory.recentIssues.forEach { appendLine("  ${it.logSafe()}") }
        }
        appendLine(
            "История ограничена текущим буфером приложения (до ${AppLog.MAX_ENTRIES} записей); " +
                "после очистки или перезапуска старые записи недоступны."
        )
        val operatorRegion = data.operatorRegion
            .filterNot(Char::isISOControl)
            .trim()
            .take(80)
            .ifBlank { "не указан" }
        appendLine("Оператор / регион: $operatorRegion")
        appendLine(
            "Предупреждение: оператор и регион указаны вручную и могут быть неточными. " +
                "Вместе с журналом эти данные помогают идентифицировать вашу сеть, поэтому " +
                "просмотрите файл перед отправкой."
        )
        val selfTest = data.selfTest
        if (selfTest == null) {
            appendLine("Проверка передачи данных: не запускалась")
        } else {
            appendLine(
                "Самотест: ${selfTest.repetitions} прогона; до ${selfTest.config.payloadKiB} KiB " +
                    "на ответ, до ${selfTest.config.hostTimeoutSeconds} s на попытку, " +
                    "idle ${selfTest.config.idleTimeoutSeconds} s"
            )
            if (selfTest.results.isEmpty()) {
                appendLine("Результаты самотеста: нет")
            } else {
                selfTest.serviceStats().forEach { stats ->
                    val service = serviceLabel(stats.service)
                    val httpResponsePercent = if (stats.attempts > 0) {
                        stats.httpResponsesReceived * 100 / stats.attempts
                    } else {
                        0
                    }
                    val medianRate = stats.medianTransferKiBPerSecond?.let {
                        String.format(java.util.Locale.ROOT, "%.1f KiB/s", it)
                    } ?: "нет данных"
                    appendLine(
                        "$service: HTTP-ответ ${stats.httpResponsesReceived}/${stats.attempts} " +
                            "($httpResponsePercent%), медиана времени ${stats.medianElapsedMs ?: "?"} ms, " +
                            "медиана тела ${stats.medianBytesReceived ?: "?"} bytes, " +
                            "медианная скорость $medianRate"
                    )
                }
                selfTest.results.forEach { result ->
                    appendLine(
                        "${serviceLabel(result.service)} run ${result.attempt}: ${result.outcome}, " +
                            "${result.bytesReceived} bytes, total=${result.elapsedMs} ms, " +
                            "DoH=${result.dohResolveMs ?: "?"} ms, " +
                            "transfer=${result.transferMs ?: "?"} ms, " +
                            "TLS=${result.tlsHandshakeMs ?: "?"} ms, " +
                            "HTTP=${result.httpStatus ?: "?"}"
                    )
                }
            }
        }
        appendLine()
        appendLine("Отчёт создаётся только по запросу и сохраняется в выбранное вами место.")
        appendLine(
            if (data.logs.isNotEmpty()) {
                "При приложенном журнале отчёт может содержать доменные имена, IP-адреса и " +
                    "параметры соединений; содержимое трафика не записывается, и отчёт не " +
                    "отправляется автоматически."
            } else {
                "Отчёт не содержит домены, IP-адреса, SSID или содержимое трафика и не отправляется автоматически."
            }
        )
        appendLine("Разные DNS-ответы и неудачный TLS-пробник сами по себе не доказывают подмену DNS или тип блокировки.")
        appendLine("Пропускная способность измеряется только самотестом для четырёх фиксированных HTTPS-сервисов.")
        if (data.logs.isNotEmpty()) {
            appendLine()
            appendLine("Журнал приложения (${data.logs.size} записей):")
            data.logs.forEach { appendLine(it.logSafe()) }
        }
    }

    private fun parseSelfTestResult(message: String): TransferTestResult? {
        val match = selfTestResultPattern.matchEntire(message) ?: return null
        fun optionalLong(group: Int): Long? =
            match.groupValues[group].takeIf { it != "-" }?.toLongOrNull()
        fun optionalInt(group: Int): Int? =
            match.groupValues[group].takeIf { it != "-" }?.toIntOrNull()
        return TransferTestResult(
            service = match.groupValues[1],
            attempt = match.groupValues[2].toIntOrNull() ?: return null,
            outcome = match.groupValues[3],
            bytesReceived = match.groupValues[4].toIntOrNull() ?: return null,
            elapsedMs = match.groupValues[5].toLongOrNull() ?: return null,
            socksConnectMs = optionalLong(6),
            dohResolveMs = optionalLong(7),
            tlsHandshakeMs = optionalLong(8),
            transferMs = optionalLong(9),
            httpStatus = optionalInt(10)
        )
    }

    private fun serviceLabel(service: String): String = when (service) {
        "youtube" -> "YouTube"
        "discord" -> "Discord"
        "4pda" -> "4pda"
        else -> "Cloudflare control"
    }

    private fun dnsInterpretation(comparison: String): String = when (comparison) {
        "match" -> "системный DNS и DoH вернули хотя бы один общий адрес."
        "answers_differ_tls_ok" ->
            "ответы различаются, но TLS на системном IP проходит; возможна штатная CDN-вариация."
        "answers_differ_tls_failed" ->
            "ответы различаются и TLS-проба не прошла; причина неизвестна, подмена не доказана."
        "system_failed" -> "системный DNS не вернул адрес; причина неизвестна."
        "doh_failed" -> "DoH не вернул ответ; системный DNS может быть доступен."
        else -> "недостаточно данных."
    }

    private fun String.reportSafe(): String =
        filterNot(Char::isISOControl).take(160)

    private fun String.logSafe(): String =
        filterNot(Char::isISOControl).take(2000)

    private fun Boolean?.asText(): String = when (this) {
        true -> "да"
        false -> "нет"
        null -> "неизвестно"
    }
}
