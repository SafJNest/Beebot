package com.safjnest.model.BotSettings

import java.awt.Color

data class BotSettings(
    val name: String = "",
    val discordToken: String = "",
    val embedColor: Color = Color.BLUE,
    val prefix: String = "!",
    val activity: String = "",
    val ownerId: String = "",
    val coOwnersIds: List<String> = listOf(),
    val helpWord: String = "help",
    val maxPrime: Int = 10,
    val maxFreePlaylists: Int = 5,
    val maxFreePlaylistSize: Int = 100,
    val maxPremiumPlaylists: Int = 20,
    val maxPremiumPlaylistSize: Int = 500,
    val info: String = ""
)