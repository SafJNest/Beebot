package com.safjnest.model.BotSettings

data class Settings(
    val config: AppConfig,
    val jsonSettings: JsonSettings,
    val botSettings: BotSettings
)