package com.safjnest

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import com.safjnest.core.Bot
import com.safjnest.model.BotSettings.Settings
import com.safjnest.util.SafJNest
import com.safjnest.util.SettingsLoader
import com.safjnest.util.log.BotLogger
import com.safjnest.util.twitch.TwitchClient
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.util.Properties

@SpringBootApplication
class App {
    companion object {
        private lateinit var settings: Settings
        private lateinit var bot: Bot

        @JvmStatic
        fun main(args: Array<String>) {
            SafJNest.bee()
            
            BotLogger("Beebot", null)

            settings = SettingsLoader.getSettings()

            if (isTesting()) {
                BotLogger.info("Beebot is in testing mode")
                //runSpring()
            } else {
                TwitchClient.init()
                //runSpring()
            }
            bot = Bot()
            bot.il_risveglio_della_bestia()
        }

        fun runSpring() {
            val springApplication = SpringApplication(App::class.java)
                
            val springProperties = Properties()
            try {
                springProperties.load(FileReader("spring.properties"))
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            springApplication.setDefaultProperties(springProperties)
            springApplication.run()
        }

        fun shutdown() {
            BotLogger.trace("Shutting down the bot")
            bot.distruzione_demoniaca()
        }

        fun restart() {
            BotLogger.trace("Restarting the bot")
            bot.distruzione_demoniaca()
            bot.il_risveglio_della_bestia()
        }

        fun isTesting(): Boolean {
            return settings.config.isTesting()
        }
    }
}