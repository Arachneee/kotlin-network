package server.nonblocking.nio.core

import util.ThreadLogUtil.log
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

class ClientRegistry(
    private val messageReader: MessageReader,
    private val messageWriter: MessageWriter,
) {
    private val clients = ConcurrentHashMap<SocketChannel, SelectionKey>()

    fun register(
        channel: SocketChannel,
        key: SelectionKey,
    ) {
        clients[channel] = key
        messageReader.registerChannel(channel)
    }

    fun unregister(channel: SocketChannel) {
        val key = clients.remove(channel)
        messageReader.unregisterChannel(channel)
        messageWriter.removePendingWrites(channel)
        closeChannel(channel)
        key?.cancel()
    }

    private fun closeChannel(channel: SocketChannel) {
        try {
            channel.close()
        } catch (e: Exception) {
            log("클라이언트 채널 닫기 실패: ${e.message}")
        }
    }

    fun getAllChannels(): Set<SocketChannel> = clients.keys.toSet()

    fun getKey(channel: SocketChannel): SelectionKey? = clients[channel]
}
