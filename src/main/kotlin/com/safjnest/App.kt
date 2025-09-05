package com.safjnest

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import com.safjnest.core.Bot
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.util.Properties

@SpringBootApplication
class App {
    companion object {
        private lateinit var bot: Bot
        
        @JvmStatic
        fun main(args: Array<String>) {
            println("🐝 Beebot starting up in Kotlin! 🐝")
            
            // Initialize the bot
            bot = Bot()
            bot.il_risveglio_della_bestia()
            
            // Optionally start Spring Boot for web features
            if (args.contains("--web")) {
                runSpring(args)
            }
        }

        fun runSpring(args: Array<String>) {
            val springApplication = SpringApplication(App::class.java)
                
            val springProperties = Properties()
            try {
                springProperties.load(FileReader("spring.properties"))
            } catch (e: FileNotFoundException) {
                println("Spring properties file not found, using defaults")
            } catch (e: IOException) {
                e.printStackTrace()
            }

            springApplication.setDefaultProperties(springProperties)
            springApplication.run(*args)
        }

        fun shutdown() {
            println("Shutting down the bot")
            if (::bot.isInitialized) {
                bot.distruzione_demoniaca()
            }
        }

        fun restart() {
            println("Restarting the bot")
            if (::bot.isInitialized) {
                bot.distruzione_demoniaca()
                bot.il_risveglio_della_bestia()
            }
        }

        fun isTesting(): Boolean {
            return System.getenv("TESTING")?.toBoolean() ?: false
        }
    }
}