package com.safjnest.core

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import com.safjnest.model.BotSettings.BotSettings
import java.awt.Color

/**
 * Main class of the bot converted to Kotlin.
 * Simplified version focusing on core functionality.
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 */
class Bot {
    
    companion object {
        private lateinit var jda: JDA
        private lateinit var botID: String
        private lateinit var settings: BotSettings
        
        fun getJDA(): JDA = jda
        fun getSettings(): BotSettings = settings
        fun getPrefix(): String = settings.prefix
        fun getBotId(): String = botID
        fun getColor(): Color = settings.embedColor
    }

    /**
     * Il risveglio della bestia - The awakening of the beast
     * Core bot initialization in Kotlin
     */
    fun il_risveglio_della_bestia() {
        println("Bot starting initialization...")
        
        // Simplified settings for initial conversion
        settings = BotSettings(
            name = "Beebot",
            discordToken = System.getenv("DISCORD_TOKEN") ?: "your-bot-token-here",
            embedColor = Color.BLUE,
            prefix = "!",
            activity = "with Kotlin! {0}help",
            ownerId = "your-owner-id",
            coOwnersIds = listOf(),
            helpWord = "help"
        )

        println("Bot info: ${settings.info}")

        try {
            jda = JDABuilder.createLight(
                settings.discordToken,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_EXPRESSIONS,
                GatewayIntent.GUILD_PRESENCES,
                GatewayIntent.GUILD_MODERATION
            )
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.ALL)
            .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.ACTIVITY)
            .build()

            // Wait for JDA to be ready
            jda.awaitReady()
            botID = jda.selfUser.id

            val activity = Activity.listening(settings.activity.replace("{0}", settings.prefix))
            jda.presence.setActivity(activity)

            jda.addEventListener(object : ListenerAdapter() {
                override fun onReady(event: ReadyEvent) {
                    println("Bot ready! Logged in as: ${event.jda.selfUser.name}")
                }
            })

            println("Bot initialization completed successfully!")

        } catch (e: Exception) {
            println("Error during bot initialization: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Distruzione demoniaca - Demonic destruction
     * Shutdown the bot
     */
    fun distruzione_demoniaca() {
        println("Shutting down the bot...")
        try {
            jda.shutdown()
        } catch (e: Exception) {
            println("Error during shutdown: ${e.message}")
        }
    }
}