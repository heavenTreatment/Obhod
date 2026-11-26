package com.heaventreat.obhod.strategy

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import com.heaventreat.obhod.logging.AppLog
import com.heaventreat.obhod.vpn.ByeDpiEngineService
import com.heaventreat.obhod.vpn.VpnPreferences
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

private typealias Candidate = AutoStrategyProbe.Candidate
private typealias Config = AutoStrategyProbe.Config

internal object ByeByeDpiManager {
    private const val TAG = "ByeByeDPI"
    private const val PREFIX = "byebye_auto_"
    private const val LAST_KEY = "byebye_auto_last"
    private const val PROGRESS_KEY = "byebye_auto_progress"
    private const val PROGRESS_STATE_KEY = "byebye_auto_progress_state"
    private const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L
    private const val ACCEPT_RATIO = 5.0 / 6.0
    private const val SOCKS_PORT = 10800
    private var probeEngineRunning = false
    private var activeProbePort = 0

    data class Outcome(val ok: Boolean, val message: String)
    data class Progress(
        val totalCandidates: Int,
        val successfulCandidates: Int,
        val failedCandidates: Int,
        val currentCandidate: String?,
        val currentProgress: Float = 0f,
        val isRunning: Boolean
    ) {
        val total: Int get() = totalCandidates.coerceAtLeast(0)
        val successful: Int get() = successfulCandidates.coerceIn(0, total)
        val failed: Int get() = failedCandidates.coerceIn(0, total - successful)
        val checked: Int get() = successful + failed
        val active: Int get() = if (isRunning && currentCandidate != null && checked < total) 1 else 0
        val pending: Int get() = (total - checked - active).coerceAtLeast(0)
        val fraction: Float
            get() = if (total == 0) 0f else
                (checked + if (active == 1) currentProgress.coerceIn(0f, 1f) else 0f) / total
        val successPercent: Int get() = if (checked == 0) 0 else successful * 100 / checked
        val failurePercent: Int get() = if (checked == 0) 0 else failed * 100 / checked
    }

    internal fun isSuccessfulScore(score: Int, maximum: Int): Boolean =
        maximum > 0 && score >= ceil(maximum * ACCEPT_RATIO).toInt()

    internal fun shouldStopEarly(fullAnalysis: Boolean, score: Int, maximum: Int): Boolean =
        !fullAnalysis && isSuccessfulScore(score, maximum)

    data class Selection(
        val version: Int,
        val networkKey: String,
        val winnerId: String,
        val score: Int,
        val total: Int,
        val selectedAt: Long,
        val arguments: List<String>,
        val scores: Map<String, Int>
    )

    fun status(context: Context): String {
        val store = prefs(context)
        store.getString(PROGRESS_KEY, "").orEmpty().takeIf(String::isNotBlank)?.let { return it }
        val config = runCatching { AutoStrategyProbe.loadConfig(context) }.getOrNull()
            ?: return "Авто-подбор ещё не выполнялся"
        val result = store.getString(LAST_KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return "Авто-подбор ещё не выполнялся"
        if (result.optInt("version", -1) != config.version) {
            return "Авто-подбор ещё не выполнялся для этой версии списка"
        }
        val scores = result.optJSONObject("scores") ?: return "Авто-подбор ещё не выполнялся"
        val total = result.optInt("total", 1).coerceAtLeast(1)
        val winner = result.optString("winnerId")
        val time = SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(Date(result.optLong("selectedAt")))
        return if (winner.isBlank()) {
            "Рабочая стратегия не найдена · ${scores.length()}/${config.candidates.size} проверено · $time"
        } else {
            "ByeByeDPI: $winner · ${result.optInt("score")}/$total · $time"
        }
    }

    fun progress(context: Context): Progress? {
        val store = prefs(context)
        store.getString(PROGRESS_STATE_KEY, null)?.let { raw ->
            parseProgress(raw)?.takeIf { it.isRunning }?.let { return it }
        }

        val config = runCatching { AutoStrategyProbe.loadConfig(context) }.getOrNull() ?: return null
        val result = store.getString(LAST_KEY, null)?.let {
            runCatching { JSONObject(it) }.getOrNull()
        } ?: return null
        if (result.optInt("version", -1) != config.version) return null

        val scores = result.optJSONObject("scores") ?: return null
        val checked = scores.length()
        if (checked == 0) return null
        val successful = scores.keys().asSequence()
            .count { isSuccessfulScore(scores.optInt(it), result.optInt("total", 0)) }
        return Progress(
            totalCandidates = config.candidates.size,
            successfulCandidates = successful,
            failedCandidates = checked - successful,
            currentCandidate = null,
            isRunning = false
        )
    }

    fun clear(context: Context) {
        val store = prefs(context)
        store.edit().apply {
            store.all.keys.filter { it.startsWith(PREFIX) }.forEach(::remove)
        }.apply()
    }

    fun select(
        context: Context,
        networkKey: String,
        onProgress: (String) -> Unit = {}
    ): Selection {
        val config = AutoStrategyProbe.loadConfig(context)
        val fullAnalysis = prefs(context).getBoolean(VpnPreferences.BYEBYEDPI_FULL_ANALYSIS, false)
        if (!fullAnalysis) {
            cached(context, networkKey, config.version)?.let {
                report(context, onProgress, "ByeByeDPI: применяю сохранённую стратегию ${it.winnerId}")
                return it
            }
        }
        return runProbes(context, networkKey, config, fullAnalysis, onProgress)
            ?: error("ByeByeDPI: стратегии не прошли проверку на тестовых сайтах")
    }

    /** True when the last selected strategy is still valid and can be reused without probing. */
    fun hasCachedSelection(context: Context): Boolean {
        val config = runCatching { AutoStrategyProbe.loadConfig(context) }.getOrNull() ?: return false
        val raw = prefs(context).getString(LAST_KEY, null) ?: return false
        val selection = parse(raw) ?: return false
        return selection.version == config.version &&
            System.currentTimeMillis() - selection.selectedAt <= CACHE_TTL_MS
    }

    fun proxyArguments(selection: Selection, port: Int = SOCKS_PORT): Array<String> =
        (listOf("byedpi", "--ip", "127.0.0.1", "--port", port.toString()) + selection.arguments).toTypedArray()

    fun stopEngineService(context: Context, port: Int = SOCKS_PORT) {
        runCatching {
            context.startService(
                Intent(context, ByeDpiEngineService::class.java).setAction(ByeDpiEngineService.ACTION_STOP)
            )
        }
        awaitPortClosed(port, 2_000L)
        awaitEngineProcessExit(context, 2_000L)
    }

    fun reselect(
        context: Context,
        networkKey: String,
        onProgress: (String) -> Unit = {}
    ): Outcome {
        clear(context)
        return try {
            val selection = select(context, networkKey, onProgress)
            Outcome(true, "ByeByeDPI: выбрана ${selection.winnerId} (${selection.score}/${selection.total})")
        } catch (error: Exception) {
            Outcome(false, error.message ?: "ByeByeDPI: не удалось подобрать стратегию")
        }
    }

    private fun runProbes(
        context: Context,
        networkKey: String,
        config: Config,
        fullAnalysis: Boolean,
        onProgress: (String) -> Unit
    ): Selection? {
        report(context, onProgress, "ByeByeDPI: проверяю ${config.candidates.size} оригинальных стратегий на ${config.sites.size} сайтах…")
        val executor = Executors.newFixedThreadPool(config.concurrency.coerceIn(1, 8))
        try {
            val scores = LinkedHashMap<String, Int>()
            val maximum = config.sites.size * config.requests.coerceAtLeast(1)
            var winner: Candidate? = null
            var winnerScore = 0
            for ((index, candidate) in config.candidates.withIndex()) {
                saveProgress(context, config.candidates.size, scores, candidate.id, 0f, maximum)
                report(context, onProgress, "ByeByeDPI: ${index + 1}/${config.candidates.size} ${candidate.id}")
                val score = probeCandidate(context, candidate, config, executor) { candidateProgress ->
                    saveProgress(
                        context,
                        config.candidates.size,
                        scores,
                        candidate.id,
                        candidateProgress,
                        maximum
                    )
                }
                scores[candidate.id] = score
                saveProgress(context, config.candidates.size, scores, null, 0f, maximum)
                if (score > winnerScore || (score == winnerScore && score > 0 && candidate.cost < (winner?.cost ?: Int.MAX_VALUE))) {
                    winner = candidate
                    winnerScore = score
                }
                report(context, onProgress, "ByeByeDPI: ${candidate.id} — $score/$maximum")
                if (shouldStopEarly(fullAnalysis, score, maximum)) break
            }
            val chosen = winner ?: run {
                saveResult(context, config.version, networkKey, maximum, scores)
                return null
            }
            val selection = Selection(
                config.version, networkKey, chosen.id, winnerScore, maximum,
                System.currentTimeMillis(), chosen.arguments, scores
            )
            save(context, selection)
            report(context, onProgress, "ByeByeDPI: выбрана ${chosen.id} ($winnerScore/$maximum)")
            return selection
        } finally {
            executor.shutdownNow()
            prefs(context).edit()
                .remove(PROGRESS_KEY)
                .remove(PROGRESS_STATE_KEY)
                .apply()
        }
    }

    private fun probeCandidate(
        context: Context,
        candidate: Candidate,
        config: Config,
        executor: java.util.concurrent.ExecutorService,
        onProgress: (Float) -> Unit
    ): Int {
        stopEngine(context)
        val port = freePort()
        val args = listOf("byedpi", "--ip", "127.0.0.1", "--port", port.toString()) + candidate.arguments
        val intent = Intent(context, ByeDpiEngineService::class.java)
            .setAction(ByeDpiEngineService.ACTION_START)
            .putExtra(ByeDpiEngineService.EXTRA_ARGS, args.toTypedArray())
        return try {
            context.startService(intent) ?: error("Could not start ByeByeDPI process")
            probeEngineRunning = true
            activeProbePort = port
            if (!waitForSocks(port, 4_000)) {
                AppLog.w(TAG, "${candidate.id}: ByeByeDPI SOCKS5 did not become ready")
                0
            } else {
                AutoStrategyProbe.checkSites(port, config, executor, onProgress)
            }
        } catch (error: Exception) {
            AppLog.w(TAG, "${candidate.id}: probe failed", error)
            0
        } finally {
            stopEngine(context)
        }
    }

    private fun stopEngine(context: Context) {
        if (!probeEngineRunning) return
        stopEngineService(context, activeProbePort)
        probeEngineRunning = false
        activeProbePort = 0
    }

    private fun awaitPortClosed(port: Int, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (port > 0 && System.currentTimeMillis() < deadline) {
            val listening = runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 100) }
                true
            }.getOrDefault(false)
            if (!listening) return
            Thread.sleep(50)
        }
    }

    private fun awaitEngineProcessExit(context: Context, timeoutMs: Long) {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val processName = "${context.packageName}:byedpi"
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val process = activityManager.runningAppProcesses.orEmpty()
                .firstOrNull { it.processName == processName }
            if (process == null) return
            Thread.sleep(50)
        }
        activityManager.runningAppProcesses.orEmpty()
            .firstOrNull { it.processName == processName }
            ?.let { Process.killProcess(it.pid) }
    }

    private fun waitForSocks(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 250)
                    socket.soTimeout = 250
                    socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                    return socket.getInputStream().read() == 5 && socket.getInputStream().read() == 0
                }
            } catch (_: Exception) {
                Thread.sleep(80)
            }
        }
        return false
    }

    private fun freePort(): Int =
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

    private fun report(context: Context, onProgress: (String) -> Unit, message: String) {
        AppLog.i(TAG, message)
        onProgress(message)
        prefs(context).edit().putString(PROGRESS_KEY, message).apply()
    }

    private fun saveProgress(
        context: Context,
        totalCandidates: Int,
        scores: Map<String, Int>,
        currentCandidate: String?,
        currentProgress: Float,
        maximum: Int
    ) {
        val successful = scores.values.count { isSuccessfulScore(it, maximum) }
        val progress = JSONObject().apply {
            put("totalCandidates", totalCandidates)
            put("successfulCandidates", successful)
            put("failedCandidates", scores.size - successful)
            put("currentCandidate", currentCandidate)
            put("currentProgress", currentProgress)
            put("isRunning", true)
        }
        prefs(context).edit().putString(PROGRESS_STATE_KEY, progress.toString()).apply()
    }

    private fun parseProgress(raw: String): Progress? = runCatching {
        val value = JSONObject(raw)
        val total = value.optInt("totalCandidates").coerceAtLeast(0)
        val successful = value.optInt("successfulCandidates").coerceIn(0, total)
        val failed = value.optInt("failedCandidates").coerceIn(0, total - successful)
        Progress(
            totalCandidates = total,
            successfulCandidates = successful,
            failedCandidates = failed,
            currentCandidate = value.optString("currentCandidate").takeIf(String::isNotBlank),
            currentProgress = value.optDouble("currentProgress", 0.0).toFloat(),
            isRunning = value.optBoolean("isRunning")
        )
    }.getOrNull()

    private fun cached(context: Context, networkKey: String, version: Int): Selection? {
        val raw = prefs(context).getString(PREFIX + networkKey, null) ?: return null
        val selection = parse(raw) ?: return null
        if (selection.version != version || System.currentTimeMillis() - selection.selectedAt > CACHE_TTL_MS) return null
        return selection
    }

    private fun save(context: Context, selection: Selection) {
        val value = resultJson(selection).toString()
        prefs(context).edit().putString(PREFIX + selection.networkKey, value).putString(LAST_KEY, value).apply()
    }

    private fun saveResult(context: Context, version: Int, networkKey: String, total: Int, scores: Map<String, Int>) {
        val value = JSONObject().apply {
            put("version", version)
            put("networkKey", networkKey)
            put("winnerId", "")
            put("score", 0)
            put("total", total)
            put("selectedAt", System.currentTimeMillis())
            put("scores", JSONObject(scores))
        }
        prefs(context).edit().putString(LAST_KEY, value.toString()).apply()
    }

    private fun resultJson(selection: Selection) = JSONObject().apply {
        put("version", selection.version)
        put("networkKey", selection.networkKey)
        put("winnerId", selection.winnerId)
        put("score", selection.score)
        put("total", selection.total)
        put("selectedAt", selection.selectedAt)
        put("arguments", JSONArray(selection.arguments))
        put("scores", JSONObject(selection.scores))
    }

    private fun parse(raw: String): Selection? = runCatching {
        val value = JSONObject(raw)
        val argsArray = value.optJSONArray("arguments") ?: return null
        val args = (0 until argsArray.length()).map { argsArray.getString(it) }
        val scoresJson = value.optJSONObject("scores") ?: JSONObject()
        val scores = buildMap {
            scoresJson.keys().forEach { put(it, scoresJson.optInt(it)) }
        }
        Selection(
            value.optInt("version"), value.optString("networkKey", "default"),
            value.optString("winnerId"), value.optInt("score"), value.optInt("total"),
            value.optLong("selectedAt"), args, scores
        ).takeIf { it.winnerId.isNotBlank() && it.arguments.isNotEmpty() }
    }.getOrNull()

    private fun prefs(context: Context) = VpnPreferences.get(context)
}
