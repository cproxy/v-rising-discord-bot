package de.darkatra.vrising.discord

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.exception.EntityNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.dizitart.no2.Nitrite
import org.dizitart.no2.objects.filters.ObjectFilters
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Duration
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.absolutePathString

@Service
@EnableConfigurationProperties(BotProperties::class)
class ServerStatusMonitorService(
    private val serverQueryClient: ServerQueryClient,
    botProperties: BotProperties
) : CoroutineScope, DisposableBean {

    override val coroutineContext: CoroutineContext = Dispatchers.Default

    private var database = Nitrite.builder()
        .compressed()
        .filePath(botProperties.databasePath.absolutePathString())
        .openOrCreate(botProperties.databaseUsername, botProperties.databasePassword)

    private var repository = database.getRepository(ServerStatusMonitor::class.java)

    fun putServerStatusMonitor(serverStatusMonitor: ServerStatusMonitor) {
        if (repository.find(ObjectFilters.eq("id", serverStatusMonitor.id)).firstOrNull() != null) {
            repository.update(serverStatusMonitor)
        } else {
            repository.insert(serverStatusMonitor)
        }
    }

    fun removeServerStatusMonitor(id: String) {
        repository.remove(ObjectFilters.eq("id", id))
    }

    fun getServerStatusMonitors(): List<ServerStatusMonitor> {
        return repository.find().toList()
    }

    fun launchServerStatusMonitor(kord: Kord) {
        launch {
            while (isActive) {
                runCatching {
                    getServerStatusMonitors().forEach { serverStatusConfiguration ->

                        val channel = kord.getChannel(Snowflake(serverStatusConfiguration.discordChannelId))
                        if (channel == null || channel !is MessageChannelBehavior) {
                            return@forEach
                        }

                        val serverInfo = serverQueryClient.getServerInfo(serverStatusConfiguration.hostName, serverStatusConfiguration.queryPort)
                        val players = serverQueryClient.getPlayerList(serverStatusConfiguration.hostName, serverStatusConfiguration.queryPort)
                        val rules = serverQueryClient.getRules(serverStatusConfiguration.hostName, serverStatusConfiguration.queryPort)

                        val currentEmbedMessageId = serverStatusConfiguration.currentEmbedMessageId
                        if (currentEmbedMessageId != null) {
                            try {
                                ServerStatusEmbed.update(serverInfo, players, rules, channel.getMessage(Snowflake(currentEmbedMessageId)))
                                return@forEach
                            } catch (e: EntityNotFoundException) {
                                serverStatusConfiguration.currentEmbedMessageId = null
                            }
                        }

                        serverStatusConfiguration.currentEmbedMessageId = ServerStatusEmbed.create(serverInfo, players, rules, channel).toString()
                        putServerStatusMonitor(serverStatusConfiguration)
                    }
                }.onFailure { throwable ->
                    println("Exception in status monitoring thread: ${throwable.message}")
                    throwable.printStackTrace()
                }

                delay(Duration.ofMinutes(1).toMillis())
            }
        }
    }

    override fun destroy() {
        database.close()
    }
}
