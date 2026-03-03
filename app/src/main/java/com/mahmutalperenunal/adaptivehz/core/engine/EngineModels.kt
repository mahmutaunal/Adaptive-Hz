package com.mahmutalperenunal.adaptivehz.core.engine

data class SettingWrite(
    val key: String,
    val intValue: Int,
    val label: String = "$key=$intValue"
)