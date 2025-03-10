package de.darkatra.vrising.discord

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.validation.annotation.Validated
import java.nio.file.Path
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

@Validated
@ConstructorBinding
@ConfigurationProperties("bot")
class BotProperties {

    @field:NotBlank
    lateinit var discordBotToken: String

    @field:NotNull
    lateinit var databasePath: Path

    @field:NotBlank
    lateinit var databaseUsername: String

    @field:NotBlank
    lateinit var databasePassword: String
}
