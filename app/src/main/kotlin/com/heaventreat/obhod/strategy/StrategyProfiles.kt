package com.heaventreat.obhod.strategy

data class StrategyProfileOption(
    val id: String,
    val title: String,
    val description: String
)

object StrategyProfiles {
    const val BYEBYEDPI_ID = "byebye-dpi"
    const val ROOT_ID = "root"
    const val DEFAULT_ID = BYEBYEDPI_ID

    /** Stored by versions that managed the old SULinux module; migrated on read. */
    internal const val LEGACY_ROOT_ID = "sulinux"

    val options = listOf(
        StrategyProfileOption(
            BYEBYEDPI_ID,
            "ByeByeDPI",
            "Основной вариант обхода. Подбирает рабочую TCP-тактику для текущей сети по реальным HTTPS-подключениям."
        ),
        StrategyProfileOption(
            ROOT_ID,
            "Zapret Root",
            "Root-режим. Управляет установленным KernelSU-модулем nZAPRET и его правилами для IPv4/IPv6."
        )
    )

    fun isValid(id: String): Boolean = options.any { it.id == normalize(id) }

    fun normalize(id: String): String = if (id == LEGACY_ROOT_ID) ROOT_ID else id

    fun label(id: String): String =
        options.firstOrNull { it.id == normalize(id) }?.title ?: options.first().title
}
