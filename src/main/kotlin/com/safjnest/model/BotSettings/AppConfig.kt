package com.safjnest.model.BotSettings

data class AppConfig(
    val testing: Boolean = false,
    val bot: String = "",
    val host: String = ""
) {
    fun isTesting(): Boolean = testing
}