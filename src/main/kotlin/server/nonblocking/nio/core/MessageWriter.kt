package server.nonblocking.nio.core

import util.ThreadLogUtil.log
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

class MessageWriter {
    private val pendingWrites = mutableListOf<PendingWrite>()

    fun write(
        channel: SocketChannel,
        data: ByteArray,
        key: SelectionKey,
    ): Boolean {
        val buffer = ByteBuffer.wrap(data)

        return try {
            writeBuffer(channel, buffer, key)
        } catch (e: Exception) {
            log("쓰기 중 오류 발생: ${e.message}")
            false
        }
    }

    private fun writeBuffer(
        channel: SocketChannel,
        buffer: ByteBuffer,
        key: SelectionKey,
    ): Boolean {
        while (buffer.hasRemaining()) {
            val bytesWritten = channel.write(buffer)

            if (bytesWritten == 0) {
                addToPendingWrites(channel, buffer, key)
                registerWriteInterest(key)
                return false
            }
        }
        return true
    }

    private fun addToPendingWrites(
        channel: SocketChannel,
        buffer: ByteBuffer,
        key: SelectionKey,
    ) {
        synchronized(pendingWrites) {
            pendingWrites.add(PendingWrite(channel, buffer, key))
        }
    }

    private fun registerWriteInterest(key: SelectionKey) {
        key.interestOps(key.interestOps() or SelectionKey.OP_WRITE)
    }

    fun processPendingWrites(key: SelectionKey): Boolean {
        val channel = key.channel() as SocketChannel

        synchronized(pendingWrites) {
            val iterator = pendingWrites.iterator()

            while (iterator.hasNext()) {
                val pendingWrite = iterator.next()
                if (pendingWrite.channel == channel) {
                    if (!flushPendingWrite(pendingWrite, iterator)) {
                        return false
                    }
                }
            }

            unregisterWriteInterestIfEmpty(channel, key)
        }
        return true
    }

    private fun flushPendingWrite(
        pendingWrite: PendingWrite,
        iterator: MutableIterator<PendingWrite>,
    ): Boolean {
        val buffer = pendingWrite.buffer

        while (buffer.hasRemaining()) {
            val bytesWritten = pendingWrite.channel.write(buffer)
            if (bytesWritten == 0) {
                return false
            }
        }

        iterator.remove()
        return true
    }

    private fun unregisterWriteInterestIfEmpty(
        channel: SocketChannel,
        key: SelectionKey,
    ) {
        if (pendingWrites.none { it.channel == channel }) {
            key.interestOps(key.interestOps() and SelectionKey.OP_WRITE.inv())
        }
    }

    fun removePendingWrites(channel: SocketChannel) {
        synchronized(pendingWrites) {
            pendingWrites.removeAll { it.channel == channel }
        }
    }
}
