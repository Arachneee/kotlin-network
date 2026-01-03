package server.nonblocking.nio

import server.ClientContext
import server.nonblocking.nio.core.ClientRegistry
import server.nonblocking.nio.core.MessageWriter
import util.ThreadLogUtil.log
import java.nio.channels.SocketChannel

class NioClientContext(
    private val channel: SocketChannel,
    private val clientRegistry: ClientRegistry,
    private val messageWriter: MessageWriter,
    private val channelToClientId: Map<SocketChannel, String>,
) : ClientContext {
    override val clientId: String
        get() = channelToClientId[channel] ?: channel.remoteAddress.toString()

    override fun send(message: String) {
        writeToChannel(channel, message)
    }

    override fun broadcast(message: String) {
        clientRegistry.getAllChannels().forEach { targetChannel ->
            writeToChannel(targetChannel, message)
        }
    }

    override fun broadcastExcludingSelf(message: String) {
        clientRegistry
            .getAllChannels()
            .filter { it != channel }
            .forEach { targetChannel ->
                writeToChannel(targetChannel, message)
            }
    }

    private fun writeToChannel(
        targetChannel: SocketChannel,
        message: String,
    ) {
        val key = clientRegistry.getKey(targetChannel) ?: return
        val data = message.toByteArray(Charsets.UTF_8)

        if (!messageWriter.write(targetChannel, data, key)) {
            log("클라이언트 ${targetChannel.remoteAddress}에게 즉시 전송 실패 - 대기열에 추가됨")
        }
    }
}
