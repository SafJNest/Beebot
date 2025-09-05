package com.safjnest.commands.misc

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Simple Ping command converted to Kotlin
 * Shows the bot's latency
 * 
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @author Converted to Kotlin
 */
class PingCommand : ListenerAdapter() {
    
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name == "ping") {
            val time = System.currentTimeMillis()
            
            event.deferReply().queue { hook ->
                val ping = System.currentTimeMillis() - time
                val gatewayPing = event.jda.gatewayPing
                
                hook.editOriginal(
                    "🏓 Pong!\n" +
                    "**Response Time:** ${ping}ms\n" +
                    "**Gateway Ping:** ${gatewayPing}ms"
                ).queue()
            }
        }
    }
}