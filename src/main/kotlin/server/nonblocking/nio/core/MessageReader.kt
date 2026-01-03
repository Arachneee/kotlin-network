package server.nonblocking.nio.core

import util.ThreadLogUtil.log
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

class MessageReader {
    private val readBuffers = ConcurrentHashMap<SocketChannel, ByteBuffer>()
    private val partialMessages = ConcurrentHashMap<SocketChannel, StringBuilder>()

    fun registerChannel(channel: SocketChannel) {
        readBuffers[channel] = ByteBuffer.allocate(1024)
    }

    fun unregisterChannel(channel: SocketChannel) {
        readBuffers.remove(channel)
        partialMessages.remove(channel)
    }

    fun read(channel: SocketChannel): ReadResult {
        val buffer = readBuffers[channel] ?: return ReadResult.ConnectionClosed

        val bytesRead = channel.read(buffer)

        if (bytesRead == -1) return ReadResult.ConnectionClosed
        if (bytesRead == 0) return ReadResult.Incomplete

        val newData = extractStringFromBuffer(buffer)
        log("새로운 데이터 읽음 : $newData")

        return parseMessage(channel, newData)
    }

    private fun extractStringFromBuffer(buffer: ByteBuffer): String {
        buffer.flip()
        val data = Charsets.UTF_8.decode(buffer).toString()
        buffer.clear()
        return data
    }

    private fun parseMessage(
        channel: SocketChannel,
        newData: String,
    ): ReadResult {
        val partialMessage = partialMessages.getOrPut(channel) { StringBuilder() }
        partialMessage.append(newData)

        val message = partialMessage.toString()
        val lineEndIndex = message.indexOf('\n')

        return if (lineEndIndex != -1) {
            val completeMessage = message.substring(0, lineEndIndex)
            handleRemainingData(channel, message.substring(lineEndIndex + 1), partialMessage)
            ReadResult.Complete(completeMessage)
        } else {
            ReadResult.Incomplete
        }
    }

    private fun handleRemainingData(
        channel: SocketChannel,
        remaining: String,
        partialMessage: StringBuilder,
    ) {
        if (remaining.isEmpty()) {
            partialMessages.remove(channel)
        } else {
            partialMessage.clear().append(remaining)
        }
    }
}
