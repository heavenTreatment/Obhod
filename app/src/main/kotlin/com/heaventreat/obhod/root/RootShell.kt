package com.heaventreat.obhod.root

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong


internal object RootShell {
    data class Result(val code: Int, val output: String) {
        val ok: Boolean get() = code == 0
    }

    private val tokenSeq = AtomicLong()

    val suBinary: String by lazy {
        listOf("/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su", "/sbin/su")
            .firstOrNull { File(it).exists() } ?: "su"
    }

    fun isRootAvailable(): Boolean {
        val result = run("id")
        return result.ok && result.output.contains("uid=0")
    }

    fun run(
        command: String,
        input: ByteArray? = null,
        timeoutMs: Long = 20_000
    ): Result {
        val token = "obhod_su_${System.nanoTime()}_${tokenSeq.getAndIncrement()}"
        return execute("obhod_token='$token'; $command", input, timeoutMs) { killTree(token) }
    }

    private fun execute(
        command: String,
        input: ByteArray?,
        timeoutMs: Long,
        onTimeout: () -> Unit = {}
    ): Result {
        val process = try {
            ProcessBuilder(suBinary, "-c", command).redirectErrorStream(true).start()
        } catch (error: Exception) {
            return Result(-1, error.message ?: "su недоступен")
        }
        val output = StringBuilder()
        val lock = Any()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(lock) { output.append(line).append('\n') }
                }
            } catch (_: Exception) {
            }
        }.apply {
            isDaemon = true
            start()
        }
        val writer = if (input != null) {
            Thread {
                try {
                    process.outputStream.use { it.write(input) }
                } catch (_: Exception) {
                }
            }.apply {
                isDaemon = true
                start()
            }
        } else {
            runCatching { process.outputStream.close() }
            null
        }

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            onTimeout()
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
        reader.join(1_000)
        writer?.join(1_000)
        val code = if (finished) process.exitValue() else -1
        return Result(code, synchronized(lock) { output.toString() })
    }

    private fun killTree(token: String) {
        val script = """
            t='$token'
            base=''
            for f in /proc/[0-9]*/cmdline; do
              line=${'$'}(tr '\0' ' ' < "${'$'}f" 2>/dev/null)
              case "${'$'}line" in
                *"${'$'}t"*)
                  p=${'$'}{f#/proc/}
                  p=${'$'}{p%/cmdline}
                  [ "${'$'}p" = "${'$'}${'$'}" ] || base="${'$'}base ${'$'}p"
                  ;;
              esac
            done
            i=0
            while [ ${'$'}i -lt 5 ] && [ -n "${'$'}base" ]; do
              changed=0
              for f in /proc/[0-9]*/stat; do
                p=${'$'}{f#/proc/}
                p=${'$'}{p%/stat}
                case " ${'$'}base " in *" ${'$'}p "*) continue ;; esac
                ppid=${'$'}(sed 's/^.*) //' "${'$'}f" 2>/dev/null | awk '{print ${'$'}2}')
                case " ${'$'}base " in *" ${'$'}ppid "*)
                  base="${'$'}base ${'$'}p"
                  changed=1
                  ;;
                esac
              done
              [ "${'$'}changed" = "1" ] || break
              i=${'$'}((i + 1))
            done
            for p in ${'$'}base; do
              [ "${'$'}p" != "1" ] && kill -9 "${'$'}p" 2>/dev/null
            done
        """.trimIndent()
        execute(script, null, 5_000)
    }
}
