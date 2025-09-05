package com.safjnest.commands.misc

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Simple Info command converted to Kotlin
 * Shows basic information about the bot
 */
class InfoCommand : ListenerAdapter() {
    
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name == "info") {
            val info = """
                🐝 **Beebot - Kotlin Edition**
                
                **Version:** 10.0 (Kotlin)
                **Language:** Kotlin 2.1.0
                **Framework:** JDA 5.2.1
                **Status:** Successfully converted from Java!
                
                *Original authors:* NeutronSun & Leon412
                *Kotlin conversion:* Complete ✅
            """.trimIndent()
            
            event.reply(info).setEphemeral(true).queue()
        }
    }
}