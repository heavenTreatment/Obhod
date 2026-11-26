package com.heaventreat.obhod.root

import android.content.Context
import com.heaventreat.obhod.logging.AppLog
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONObject

object NzapretManager {
    private const val TAG = "Nzapret"
    private const val MODULE_ID = "nzapret"
    private const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
    private const val MODULE_UPDATE_DIR = "/data/adb/modules_update/$MODULE_ID"
    private const val CLI = "$MODULE_DIR/system/bin/nzapret"
    private const val RELEASE_API = "https://api.github.com/repos/nikitos4683/nzapret/releases/latest"
    private const val LEGACY_MODULE_ID = "sulinux"
    private const val LEGACY_MODULE_DIR = "/data/adb/modules/$LEGACY_MODULE_ID"
    private const val MAX_DOWNLOAD_BYTES = 120L * 1024 * 1024

    data class Outcome(
        val ok: Boolean,
        val message: String,
        val rebootRequired: Boolean = false
    )

    private data class ReleaseAsset(
        val tag: String,
        val url: String,
        val size: Long,
        val sha256: String?
    )

    fun activate(context: Context, networkKey: String = "default"): Outcome {
        if (!isRootAvailable()) {
            return Outcome(false, "Для режима Zapret Root предоставьте приложению root-доступ.")
        }
        val ksud = findKsud()
            ?: return Outcome(false, "Не найден ksud KernelSU; автоматическая установка недоступна.")

        val legacy = removeLegacyModule(ksud)
        if (!legacy.ok) return legacy

        var installNeedsReboot = false
        if (!moduleInstalled()) {
            val installed = installLatestModule(context, ksud)
            if (!installed.ok) return installed
            installNeedsReboot = installed.rebootRequired
        }

        if (legacy.rebootRequired || installNeedsReboot || moduleNeedsReboot()) {
            return Outcome(
                false,
                "nZAPRET установлен через ksud. Перезагрузите устройство, затем включите режим снова.",
                rebootRequired = true
            )
        }

        if (moduleDisabled()) {
            val enabled = root("'$ksud' module enable '$MODULE_ID'", timeoutMs = 30_000)
            if (!enabled.ok) {
                return Outcome(false, "Не удалось включить модуль nZAPRET: " + enabled.output.trim().takeLast(500))
            }
        }

        AppLog.i(TAG, "Запуск nZAPRET для сети " + networkKey)
        val start = root("'$CLI' start", timeoutMs = 90_000)
        if (!start.ok) {
            return Outcome(false, "nZAPRET не запустился: " + start.output.trim().takeLast(500))
        }
        val status = statusOutput()
        AppLog.i(TAG, start.output.trim().ifBlank { "nZAPRET start завершился" })
        return Outcome(true, status.ifBlank { "nZAPRET активен" })
    }

    fun deactivate(): Outcome {
        if (!isRootAvailable()) return Outcome(true, "Root-модуль не менялся: root-доступ недоступен.")
        if (!moduleFilesExist()) return Outcome(true, "Модуль nZAPRET не установлен.")
        val ksud = findKsud()
            ?: return Outcome(false, "Не найден ksud: не удалось остановить модуль nZAPRET.")

        if (moduleInstalled()) {
            val stop = root("'$CLI' stop", timeoutMs = 90_000)
            if (!stop.ok) AppLog.w(TAG, "nZAPRET stop: " + stop.output.trim().takeLast(500))
        }

        if (!moduleDisabled()) {
            val disable = root("'$ksud' module disable '$MODULE_ID'", timeoutMs = 30_000)
            if (!disable.ok) {
                return Outcome(false, "Не удалось отключить автозапуск nZAPRET: " + disable.output.trim().takeLast(500))
            }
        }
        return Outcome(true, "nZAPRET остановлен и отключён для следующей загрузки.")
    }

    fun isRootAvailable(): Boolean = RootShell.isRootAvailable()

    fun autoSelectionStatus(): String {
        if (!isRootAvailable()) return "Для установки и управления nZAPRET нужен root-доступ."
        if (moduleNeedsReboot()) return "nZAPRET установлен, перезагрузите устройство."
        if (!moduleInstalled()) return "nZAPRET установится через ksud при первом запуске режима."
        return statusOutput().ifBlank { "Состояние nZAPRET недоступно." }
    }

    fun reselect(context: Context, networkKey: String = "default"): Outcome {
        if (!isRootAvailable()) return Outcome(false, "Нет root-доступа для перезапуска nZAPRET.")
        if (!moduleInstalled() || moduleNeedsReboot()) return activate(context, networkKey)
        val ksud = findKsud() ?: return Outcome(false, "Не найден ksud KernelSU.")
        if (moduleDisabled()) {
            val enable = root("'$ksud' module enable '$MODULE_ID'", timeoutMs = 30_000)
            if (!enable.ok) {
                return Outcome(false, "Не удалось включить nZAPRET: " + enable.output.trim().takeLast(500))
            }
        }
        val result = root("'$CLI' restart", timeoutMs = 90_000)
        val output = result.output.trim()
        AppLog.i(TAG, output.ifBlank { "nZAPRET перезапущен" })
        return if (result.ok) Outcome(true, output.ifBlank { "nZAPRET перезапущен" })
        else Outcome(false, "Не удалось перезапустить nZAPRET: " + output.takeLast(500))
    }

    private fun installLatestModule(context: Context, ksud: String): Outcome {
        val asset = try {
            latestReleaseAsset()
        } catch (error: Exception) {
            return Outcome(false, "Не удалось получить ZIP-релиз nZAPRET: " + error.message)
        }
        val zip = File(context.cacheDir, "nzapret-" + asset.tag + ".zip")
        try {
            downloadAsset(asset, zip)
            validateModuleArchive(zip)
            val install = root(
                "'" + ksud + "' module install '" + zip.absolutePath + "'",
                timeoutMs = 240_000
            )
            if (!install.ok) {
                return Outcome(false, "ksud не установил nZAPRET: " + install.output.trim().takeLast(1000))
            }
            if (!moduleFilesExist()) {
                return Outcome(false, "ksud завершился без ошибки, но каталог nZAPRET не найден.")
            }
            AppLog.i(TAG, "ZIP nZAPRET " + asset.tag + " проверен и передан ksud")
            return Outcome(
                true,
                "nZAPRET " + asset.tag + " подготовлен к установке.",
                rebootRequired = true
            )
        } catch (error: Exception) {
            return Outcome(false, "Автоматическая установка nZAPRET прервана: " + error.message)
        } finally {
            zip.delete()
        }
    }

    private fun latestReleaseAsset(): ReleaseAsset {
        val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Obhod-Android")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("GitHub API вернул HTTP " + connection.responseCode)
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) {
                "последний релиз не является стабильным"
            }
            val tag = release.optString("tag_name")
            require(tag.matches(Regex("v?[0-9][A-Za-z0-9._-]{0,39}"))) {
                "неверная версия релиза"
            }
            val assets = release.optJSONArray("assets") ?: error("в релизе нет ZIP-архива")
            val candidates = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .filter { it.optString("name").matches(Regex("(?i)nzapret-v[^/]+\\.zip")) }
            val asset = candidates.firstOrNull() ?: error("в релизе не найден ZIP nZAPRET")
            val size = asset.optLong("size", -1L)
            require(size in 1L..MAX_DOWNLOAD_BYTES) { "неподдерживаемый размер ZIP: " + size + " байт" }
            val url = URL(asset.optString("browser_download_url"))
            require(
                url.protocol == "https" &&
                    url.host == "github.com" &&
                    url.path.startsWith("/nikitos4683/nzapret/releases/download/")
            ) { "ссылка ZIP ведёт не на релизы официального репозитория" }
            val digest = asset.optString("digest").takeIf { it.startsWith("sha256:") }
                ?.removePrefix("sha256:")
            return ReleaseAsset(tag, url.toString(), size, digest)
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAsset(asset: ReleaseAsset, destination: File) {
        val connection = URL(asset.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 45_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Obhod-Android")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("сервер ZIP ответил HTTP " + connection.responseCode)
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_DOWNLOAD_BYTES && total <= asset.size) {
                            "размер загрузки превышает объявленный размер"
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(total == asset.size) { "размер ZIP не совпал с релизом" }
            if (asset.sha256 != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual.equals(asset.sha256, ignoreCase = true)) {
                    "SHA-256 ZIP не совпал с метаданными релиза"
                }
            } else {
                AppLog.w(TAG, "GitHub API не вернул SHA-256 для ZIP; проверены размер и структура модуля")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateModuleArchive(zip: File) {
        ZipFile(zip).use { archive ->
            val properties = archive.getEntry("module.prop") ?: error("в ZIP отсутствует module.prop")
            val moduleId = archive.getInputStream(properties).bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("id=") }?.substringAfter('=')?.trim()
            }
            require(moduleId == MODULE_ID) {
                "ZIP содержит модуль '" + moduleId + "', ожидался '" + MODULE_ID + "'"
            }
            require(archive.getEntry("system/bin/nzapret") != null) {
                "в ZIP отсутствует CLI system/bin/nzapret"
            }
            require(archive.getEntry("service.sh") != null) {
                "в ZIP отсутствует служба автозапуска nZAPRET"
            }
        }
    }

    private fun removeLegacyModule(ksud: String): Outcome {
        val exists = root(
            "[ -f '$LEGACY_MODULE_DIR/module.prop' ] || " +
                "[ -f '/data/adb/modules_update/$LEGACY_MODULE_ID/module.prop' ]"
        ).ok
        if (!exists) return Outcome(true, "")
        val alreadyMarked = root(
            "[ -e '$LEGACY_MODULE_DIR/remove' ] || " +
                "[ -e '/data/adb/modules_update/$LEGACY_MODULE_ID/remove' ]"
        ).ok
        if (alreadyMarked) {
            return Outcome(true, "Старый модуль уже помечен к удалению.", rebootRequired = true)
        }

        val oldScript = "$LEGACY_MODULE_DIR/sulinux.sh"
        val restore = root("[ -f '$oldScript' ] && sh '$oldScript' restore", timeoutMs = 60_000)
        if (!restore.ok) {
            AppLog.w(TAG, "Не удалось восстановить правила старого SULinux: " + restore.output.trim().takeLast(500))
        }
        val uninstall = root("'$ksud' module uninstall '$LEGACY_MODULE_ID'", timeoutMs = 30_000)
        if (!uninstall.ok) {
            return Outcome(false, "Не удалось удалить прежний модуль SULinux: " + uninstall.output.trim().takeLast(500))
        }
        AppLog.i(TAG, "Старый модуль SULinux помечен к удалению через ksud")
        return Outcome(true, "Старый модуль SULinux удалится после перезагрузки.", rebootRequired = true)
    }

    private fun findKsud(): String? {
        val result = root(
            "command -v ksud 2>/dev/null; " +
                "[ -x /data/adb/ksud ] && echo /data/adb/ksud; " +
                "[ -x /data/adb/ksu/bin/ksud ] && echo /data/adb/ksu/bin/ksud"
        )
        return result.output.lineSequence().map(String::trim)
            .firstOrNull { it.startsWith("/") && !it.contains(' ') }
    }

    private fun moduleInstalled(): Boolean =
        root("[ -x '$CLI' ] && [ -f '$MODULE_DIR/module.prop' ]").ok

    private fun moduleFilesExist(): Boolean =
        root(
            "[ -f '$MODULE_DIR/module.prop' ] || " +
                "[ -f '$MODULE_UPDATE_DIR/module.prop' ]"
        ).ok

    private fun moduleNeedsReboot(): Boolean =
        root(
            "[ -e '$MODULE_DIR/update' ] || " +
                "[ -f '$MODULE_UPDATE_DIR/module.prop' ]"
        ).ok

    private fun moduleDisabled(): Boolean =
        root("[ -e '$MODULE_DIR/disable' ]").ok

    private fun statusOutput(): String {
        val result = root("'$CLI' status --json", timeoutMs = 15_000)
        if (result.ok && result.output.isNotBlank()) return "nZAPRET: " + result.output.trim().take(800)
        val fallback = root("'$CLI' status", timeoutMs = 15_000)
        return fallback.output.trim().take(800)
    }

    private fun root(command: String, timeoutMs: Long = 20_000): RootShell.Result =
        RootShell.run(command, timeoutMs = timeoutMs)
}
