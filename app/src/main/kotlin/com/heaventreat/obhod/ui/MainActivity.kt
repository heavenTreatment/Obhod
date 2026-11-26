package com.heaventreat.obhod.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.heaventreat.obhod.diagnostics.ConnectivitySelfTest
import com.heaventreat.obhod.diagnostics.DiagnosticReport
import com.heaventreat.obhod.diagnostics.DiagnosticReportData
import com.heaventreat.obhod.diagnostics.TransferTestConfig
import com.heaventreat.obhod.logging.AppLog
import com.heaventreat.obhod.network.NetworkCalibrator
import com.heaventreat.obhod.strategy.ByeByeDpiManager
import com.heaventreat.obhod.strategy.StrategyProfiles
import com.heaventreat.obhod.root.NzapretManager
import com.heaventreat.obhod.update.AppUpdate
import com.heaventreat.obhod.update.GitHubUpdateRepository
import com.heaventreat.obhod.update.UpdateDownloadProgress
import com.heaventreat.obhod.update.UpdateDownloadStage
import com.heaventreat.obhod.update.UpdateDownloader
import com.heaventreat.obhod.update.UpdatePreferences
import com.heaventreat.obhod.vpn.ObhodVpnService
import com.heaventreat.obhod.vpn.VpnPreferences
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private var starting by mutableStateOf(false)
    private var vpnRunning by mutableStateOf(false)
    private var selectedStrategyIndex by mutableIntStateOf(0)
    private var batteryOptimizationIgnored by mutableStateOf(false)
    private var pendingDiagnosticReport: String? = null
    private var transferTestRunning by mutableStateOf(false)
    private var transferTestStatus by mutableStateOf("")
    private var autoSelectionStatus by mutableStateOf("")
    private var autoSelectionProgress by mutableStateOf<ByeByeDpiManager.Progress?>(null)
    private var byeByeDpiFullAnalysis by mutableStateOf(false)
    private var availableUpdate by mutableStateOf<AppUpdate?>(null)
    private var updateDownloadProgress by mutableStateOf<UpdateDownloadProgress?>(null)
    private var updateLaunchCount = 0
    private var pendingUpdateForPermission: AppUpdate? = null
    private var showEnableConfirmation by mutableStateOf(false)
    private var enableConfirmationCached by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { continueVpnAndStart() }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startObhod()
        else {
            AppLog.w("UI", "Разрешение VPN отклонено")
            Toast.makeText(this, "Для системного режима нужно разрешение VPN", Toast.LENGTH_LONG).show()
        }
    }

    private val diagnosticReportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) {
                pendingDiagnosticReport = null
            } else {
                val report = pendingDiagnosticReport ?: createDiagnosticReport("")
                pendingDiagnosticReport = null
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val output = contentResolver.openOutputStream(uri, "wt")
                                ?: throw IOException("Не удалось открыть выбранный файл")
                            output.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
                        }

                        Toast.makeText(
                            this@MainActivity,
                            "Отчёт сохранён",
                            Toast.LENGTH_LONG
                        ).show()
                        AppLog.i("Diagnostics", "Отчёт сохранён в выбранное пользователем место")
                    } catch (error: IOException) {
                        AppLog.e("Diagnostics", "Не удалось сохранить отчёт", error)
                        Toast.makeText(
                            this@MainActivity,
                            "Не удалось сохранить отчёт",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (error: SecurityException) {
                        AppLog.e("Diagnostics", "Нет доступа к выбранному файлу", error)
                        Toast.makeText(
                            this@MainActivity,
                            "Нет доступа к выбранному файлу",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    private val installSourcePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val update = pendingUpdateForPermission
            pendingUpdateForPermission = null
            if (update != null && packageManager.canRequestPackageInstalls()) {
                beginUpdateDownload(update)
            } else if (update != null) {
                Toast.makeText(
                    this,
                    "Разрешите установку APK для Obhod, чтобы установить обновление.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val preferences = VpnPreferences.get(this)
        byeByeDpiFullAnalysis = preferences.getBoolean(VpnPreferences.BYEBYEDPI_FULL_ANALYSIS, false)
        updateLaunchCount = if (savedInstanceState == null) {
            UpdatePreferences.registerLaunch(this)
        } else {
            UpdatePreferences.launchCount(this)
        }
        val needsTacticHelp = !preferences.getBoolean(KEY_TACTIC_HELP_ACCEPTED, false)
        if (needsTacticHelp) {
            preferences.edit()
                .putString(VpnPreferences.SELECTED_PROFILE, StrategyProfiles.DEFAULT_ID)
                .apply()
        }
        var savedProfile = preferences.getString(VpnPreferences.SELECTED_PROFILE, StrategyProfiles.DEFAULT_ID)
            ?: StrategyProfiles.DEFAULT_ID
        if (!StrategyProfiles.isValid(savedProfile)) {
            savedProfile = StrategyProfiles.DEFAULT_ID
            preferences.edit().putString(VpnPreferences.SELECTED_PROFILE, savedProfile).apply()
        }
        selectedStrategyIndex =
            StrategyProfiles.options.indexOfFirst { it.id == savedProfile }.coerceAtLeast(0)

        setContent {
            ObhodTheme {
                ObhodApp(
                    vpnRunning = vpnRunning,
                    starting = starting,
                    selectedStrategyIndex = selectedStrategyIndex,
                    onStrategySelected = ::selectStrategy,
                    onToggle = ::toggleObhod,
                    showEnableConfirmation = showEnableConfirmation,
                    enableConfirmationCached = enableConfirmationCached,
                    onEnableConfirmationDismissed = { showEnableConfirmation = false },
                    onEnableConfirmationAccepted = {
                        showEnableConfirmation = false
                        requestVpnAndStart()
                    },
                    showTacticHelp = needsTacticHelp,
                    onTacticHelpAccepted = ::acceptTacticHelp,
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    onRequestBatteryOptimization = ::requestBatteryOptimizationExemption,
                    onExportDiagnosticReport = ::requestDiagnosticReport,
                    transferTestRunning = transferTestRunning,
                    transferTestStatus = transferTestStatus,
                    onRunTransferTest = ::runTransferTest,
                    autoSelectionStatus = autoSelectionStatus,
                    autoSelectionProgress = autoSelectionProgress,
                    byeByeDpiFullAnalysis = byeByeDpiFullAnalysis,
                    onByeByeDpiFullAnalysisChanged = ::updateByeByeDpiFullAnalysis,
                    onRunAutoSelection = ::runAutoSelection,
                    availableUpdate = availableUpdate,
                    updateDownloadProgress = updateDownloadProgress,
                    onRemindUpdateLater = ::remindUpdateLater,
                    onDownloadUpdate = ::downloadUpdate,
                    onOpenUpdateGitHub = ::openUpdateGitHub,
                    onDismissUpdateDownload = ::dismissUpdateDownload
                )
            }
        }
        refreshState()
        lifecycleScope.launch {
            updateDownloadProgress = withContext(Dispatchers.IO) {
                UpdateDownloader.progress(this@MainActivity)
            }
            while (true) {
                delay(1000)
                refreshState()
                updateDownloadProgress = withContext(Dispatchers.IO) {
                    UpdateDownloader.progress(this@MainActivity)
                }
            }
        }
        checkForAppUpdate()
        if (savedInstanceState == null) handleQuickSettingsIntent(intent)
        if (savedInstanceState == null) AppLog.i("UI", "Obhod запущен")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickSettingsIntent(intent)
    }

    private fun handleQuickSettingsIntent(intent: Intent?) {
        if (intent?.action != ACTION_START_FROM_TILE) return
        intent.action = null
        toggleObhod()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ObhodVpnService.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshState()
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshBatteryOptimizationState()
        refreshState()
    }

    private fun refreshBatteryOptimizationState() {
        val powerManager = getSystemService(PowerManager::class.java)
        batteryOptimizationIgnored = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || batteryOptimizationIgnored) return
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(request)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: SecurityException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun selectStrategy(index: Int) {
        selectedStrategyIndex = index
        val option = StrategyProfiles.options[index]
        VpnPreferences.get(this).edit()
            .putString(VpnPreferences.SELECTED_PROFILE, option.id).apply()
        AppLog.i("UI", "Выбрана стратегия: ${option.title}")
    }

    private fun acceptTacticHelp() {
        VpnPreferences.get(this).edit()
            .putBoolean(KEY_TACTIC_HELP_ACCEPTED, true)
            .apply()
    }

    private fun requestDiagnosticReport(operatorRegion: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        pendingDiagnosticReport = createDiagnosticReport(operatorRegion)
        diagnosticReportLauncher.launch("obhod-diagnostics-$timestamp.txt")
    }

    private fun runTransferTest(config: TransferTestConfig) {
        if (transferTestRunning) return
        if (!vpnRunning) {
            Toast.makeText(this, "Сначала включите VPN", Toast.LENGTH_LONG).show()
            return
        }

        transferTestRunning = true
        transferTestStatus = "Проверка DNS и передачи выполняется…"
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.let(::underlyingNetworkForReport)
        AppLog.i(
            "SelfTest",
            "[SELFTEST_RUN] repetitions=${ConnectivitySelfTest.REPETITIONS} " +
                "payload_kib=${config.payloadKiB} " +
                "host_timeout_s=${config.hostTimeoutSeconds} " +
                "idle_timeout_s=${config.idleTimeoutSeconds}"
        )
        lifecycleScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    runInterruptible {
                        ConnectivitySelfTest.run(
                            config = config,
                            network = network,
                            onProgress = { service, attempt ->
                                val label = when (service) {
                                    "youtube" -> "YouTube"
                                    "discord" -> "Discord"
                                    "4pda" -> "4pda"
                                    else -> "Cloudflare"
                                }
                                runOnUiThread {
                                    transferTestStatus =
                                        "Прогон $attempt/${ConnectivitySelfTest.REPETITIONS}: $label"
                                }
                                AppLog.i(
                                    "SelfTest",
                                    "[SELFTEST_PROGRESS] attempt=$attempt service=$service"
                                )
                            },
                            onResult = { result ->
                                AppLog.i("SelfTest", result.asLogMessage())
                            }
                        )
                    }
                }
                val serviceStats = summary.serviceStats()
                val reached = serviceStats.sumOf { it.httpResponsesReceived }
                val total = serviceStats.sumOf { it.attempts }
                transferTestStatus =
                    "Готово: HTTP-ответ получен в $reached из $total попыток. Результаты в отчёте."
            } catch (error: IOException) {
                AppLog.e("SelfTest", "Тест передачи завершился с ошибкой", error)
                transferTestStatus = "Тест завершился с ошибкой; подробности в логах."
            } finally {
                transferTestRunning = false
            }
        }
    }

    private fun createDiagnosticReport(operatorRegion: String): String {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.let(::underlyingNetworkForReport)
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val details = network?.let { NetworkCalibrator.inspectNetwork(this, it) }
        val entries = AppLog.entries.value
        val messages = entries.map { it.message }
        val calibrationSignal = messages.asReversed().firstNotNullOfOrNull { message ->
            message.takeIf { it.startsWith("Калибровка сети: ") }
                ?.removePrefix("Калибровка сети: ")
                ?.substringBefore(" (кэш)")
        } ?: "неизвестно"
        val dnsComparison = messages.asReversed()
            .firstNotNullOfOrNull { message ->
                message.takeIf { it.startsWith("DNS Discord/system-vs-DoH: ") }
                    ?.removePrefix("DNS Discord/system-vs-DoH: ")
            } ?: "неизвестно"
        val selectedProfile = VpnPreferences.get(this)
            .getString(VpnPreferences.SELECTED_PROFILE, StrategyProfiles.DEFAULT_ID)
            ?: "неизвестно"
        val networkClass = when {
            capabilities == null -> "неизвестно"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "мобильная"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "другая"
        }
        val data = DiagnosticReportData(
            generatedAt = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.MEDIUM,
                Locale.ROOT
            ).format(Date()),
            appVersion = appVersionName(),
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80),
            networkClass = networkClass,
            networkValidated = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ),
            networkMetered = capabilities?.let {
                !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            },
            privateDnsActive = details?.privateDnsActive,
            ipv6Only = details?.ipv6Only,
            calibrationSignal = calibrationSignal,
            dnsComparison = dnsComparison,
            selectedProfile = selectedProfile,
            operatorRegion = operatorRegion,
            selfTest = DiagnosticReport.latestSelfTest(messages),
            logs = entries.map { it.asLine() },
            connectionEntries = entries,
            vpnRunning = vpnRunning,
            vpnStarting = starting
        )
        return DiagnosticReport.format(data)
    }

    @Suppress("DEPRECATION")
    private fun underlyingNetworkForReport(connectivity: ConnectivityManager): Network? {
        val candidates = (listOfNotNull(connectivity.activeNetwork) +
            connectivity.allNetworks).distinct()
        return candidates.firstOrNull { candidate ->
            val capabilities = connectivity.getNetworkCapabilities(candidate)
                ?: return@firstOrNull false
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"

    private fun checkForAppUpdate() {
        val currentVersion = appVersionName()
        lifecycleScope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    GitHubUpdateRepository.findUpdate(currentVersion)
                }
                if (update != null &&
                    UpdatePreferences.shouldPrompt(this@MainActivity, update.tag, updateLaunchCount)
                ) {
                    availableUpdate = update
                }
            } catch (error: Exception) {
                AppLog.w("AppUpdate", "Не удалось проверить обновления", error)
            }
        }
    }

    private fun remindUpdateLater(update: AppUpdate) {
        UpdatePreferences.remindLater(this, updateLaunchCount)
        availableUpdate = null
    }

    private fun openUpdateGitHub(update: AppUpdate) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releasePageUrl)))
            UpdatePreferences.dismiss(this, update.tag)
            availableUpdate = null
        } catch (error: ActivityNotFoundException) {
            AppLog.w("AppUpdate", "Не найдено приложение для открытия страницы релиза", error)
            Toast.makeText(this, "Не удалось открыть GitHub", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadUpdate(update: AppUpdate) {
        if (update.apkUrl == null) {
            Toast.makeText(
                this,
                "В релизе нет APK. Скачайте обновление с GitHub.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!packageManager.canRequestPackageInstalls()) {
            pendingUpdateForPermission = update
            try {
                installSourcePermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (error: ActivityNotFoundException) {
                pendingUpdateForPermission = null
                AppLog.e("AppUpdate", "Не удалось открыть настройки установки APK", error)
                Toast.makeText(
                    this,
                    "Разрешите установку APK для Obhod в настройках устройства.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        beginUpdateDownload(update)
    }

    private fun beginUpdateDownload(update: AppUpdate) {
        try {
            val existingId = UpdatePreferences.downloadId(this)
            if (existingId != null) {
                if (updateDownloadProgress?.stage != UpdateDownloadStage.FAILED) return
                UpdateDownloader.cancel(this)
            }
            UpdateDownloader.enqueue(this, update)
            UpdatePreferences.dismiss(this, update.tag)
            availableUpdate = null
            updateDownloadProgress = UpdateDownloadProgress(UpdateDownloadStage.DOWNLOADING, 0)
        } catch (error: Exception) {
            AppLog.e("AppUpdate", "Не удалось начать загрузку APK", error)
            Toast.makeText(this, "Не удалось начать загрузку обновления", Toast.LENGTH_LONG).show()
        }
    }

    private fun dismissUpdateDownload() {
        val tag = UpdatePreferences.downloadTag(this)
        UpdateDownloader.cancel(this)
        if (tag != null) {
            UpdatePreferences.clearDismissedTag(this, tag)
            UpdatePreferences.remindLater(this, updateLaunchCount)
        }
        updateDownloadProgress = null
    }

    private fun isServiceActive(): Boolean {
        val prefs = VpnPreferences.get(this)
        return prefs.getBoolean(VpnPreferences.RUNNING, false) || prefs.getBoolean(VpnPreferences.STARTING, false)
    }

    private fun selectedProfileId(): String =
        StrategyProfiles.options.getOrNull(selectedStrategyIndex)?.id
            ?: StrategyProfiles.DEFAULT_ID

    private fun currentAutoSelectionStatus(): String = when (selectedProfileId()) {
        StrategyProfiles.ROOT_ID -> NzapretManager.autoSelectionStatus()
        StrategyProfiles.BYEBYEDPI_ID -> ByeByeDpiManager.status(this)
        else -> ""
    }

    private fun refreshState() {
        val vpnPrefs = VpnPreferences.get(this)
        vpnRunning = vpnPrefs.getBoolean(VpnPreferences.RUNNING, false)
        starting = vpnPrefs.getBoolean(VpnPreferences.STARTING, false)
        autoSelectionStatus = currentAutoSelectionStatus()
        autoSelectionProgress = if (selectedProfileId() == StrategyProfiles.BYEBYEDPI_ID) {
            ByeByeDpiManager.progress(this)
        } else {
            null
        }
        val error = vpnPrefs.getString(VpnPreferences.LAST_ERROR, null)
        if (!error.isNullOrBlank()) {
            vpnPrefs.edit().remove(VpnPreferences.LAST_ERROR).apply()
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun runAutoSelection() {
        if (starting) return
        val profileId = selectedProfileId()
        if (profileId == StrategyProfiles.ROOT_ID) {
            if (!isServiceActive()) {
                requestVpnAndStart()
                return
            }

            starting = true
            VpnPreferences.get(this).edit()
                .putBoolean(VpnPreferences.STARTING, true).apply()
            autoSelectionStatus = "Перезапускаю nZAPRET…"
            startService(
                Intent(this, ObhodVpnService::class.java)
                    .setAction(ObhodVpnService.ACTION_ROOT_RESELECT)
            )
            return
        }
        if (!isServiceActive()) {
            ByeByeDpiManager.clear(this)
            Toast.makeText(
                this,
                "Включите обход, чтобы ByeByeDPI заново подобрал тактику",
                Toast.LENGTH_LONG
            ).show()
            autoSelectionStatus = "Стратегия будет подобрана при следующем включении"
            return
        }
        starting = true
        VpnPreferences.get(this).edit()
            .putBoolean(VpnPreferences.STARTING, true).apply()
        autoSelectionStatus = "ByeByeDPI: подбираю тактику заново…"
        AppLog.i("UI", "Запуск повторного авто-подбора ByeByeDPI")
        startService(
            Intent(this, ObhodVpnService::class.java)
                .setAction(ObhodVpnService.ACTION_BYEBYEDPI_RESELECT)
        )
    }

    private fun updateByeByeDpiFullAnalysis(enabled: Boolean) {
        byeByeDpiFullAnalysis = enabled
        VpnPreferences.get(this).edit()
            .putBoolean(VpnPreferences.BYEBYEDPI_FULL_ANALYSIS, enabled)
            .apply()
    }

    private fun toggleObhod() {
        if (isServiceActive() || starting) {
            stopObhod()
            return
        }
        val selected = StrategyProfiles.options.getOrNull(selectedStrategyIndex)
        if (selected?.id == StrategyProfiles.BYEBYEDPI_ID) {
            enableConfirmationCached = ByeByeDpiManager.hasCachedSelection(this)
            showEnableConfirmation = true
        } else {
            requestVpnAndStart()
        }
    }

    private fun requestVpnAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        continueVpnAndStart()
    }

    private fun continueVpnAndStart() {
        val option = StrategyProfiles.options.getOrNull(selectedStrategyIndex)
        if (option?.id == StrategyProfiles.ROOT_ID) {
            startObhod()
            return
        }
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else startObhod()
    }

    private fun startObhod() {
        val option = StrategyProfiles.options[selectedStrategyIndex]
        VpnPreferences.get(this).edit()
            .putString(VpnPreferences.SELECTED_PROFILE, option.id)
            .putBoolean(VpnPreferences.STARTING, true)
            .apply()
        starting = true
        AppLog.i("UI", "Запуск обхода: ${option.title}")
        ContextCompat.startForegroundService(this, Intent(this, ObhodVpnService::class.java).apply {
            action = ObhodVpnService.ACTION_START
            putExtra(ObhodVpnService.EXTRA_PROFILE, option.id)
        })
    }

    private fun stopObhod() {
        starting = false
        VpnPreferences.get(this).edit()
            .putBoolean(VpnPreferences.STARTING, false).apply()
        AppLog.i("UI", "Остановка обхода")
        startService(Intent(this, ObhodVpnService::class.java).setAction(ObhodVpnService.ACTION_STOP))
        refreshState()
    }

    companion object {
        private const val KEY_TACTIC_HELP_ACCEPTED = "tactic_help_accepted"
        const val ACTION_START_FROM_TILE = "com.heaventreat.obhod.action.START_FROM_TILE"
    }
}
